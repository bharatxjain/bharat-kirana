-- =============================================================================
--  BreakQ — Tests for STEP2_ORDER_RELIABILITY.sql
--
--  RUN THE WHOLE FILE AT ONCE, after STEP2_ORDER_RELIABILITY.sql. Nothing is
--  saved: every test runs inside a savepoint that is rolled back, so no order,
--  stock, price or rating is changed and no push/email is sent.
--
--  PASS = works as intended.  FAIL = broken.  SKIP = no suitable test data.
--
--  Tidy up afterwards:  drop table public.breakq_step2_results;
--  Also re-run TEST_SECURITY_STEP1.sql and TEST_ORDER_SERVER_AUTHORITY.sql.
-- =============================================================================

drop table if exists public.breakq_step2_results;
create table public.breakq_step2_results (seq int, test text, result text, detail text);

do $$
declare
  v_shop       text;
  v_shop_name  text;
  v_owner      uuid;
  v_product    text;
  v_weight     text;
  v_customer   uuid;
  v_other      uuid;
  v_items      jsonb;
  v_oid        text;
  v_oid2       text;
  v_num1       int;
  v_num2       int;
  v_int        int;
  v_int2       int;
  v_before     int;
  v_after      int;
  v_total      int;
  v_txt        text;
  v_txt2       text;
  v_msg        text;
  v_token      text;
begin
  ---------------------------------------------------------------------------
  -- Fixtures: an approved, open shop whose owner is known, with a single-size,
  -- stock-tracked product; a customer; a second customer.
  ---------------------------------------------------------------------------
  select p.shop_id, s.name, s.owner_id, p.id, p.weight_options->0->>'label'
    into v_shop, v_shop_name, v_owner, v_product, v_weight
    from public.products p
    join public.shops s
      on s.id = p.shop_id
     and s.status::text = 'approved'
     and coalesce(s.is_deleted, false) = false
     and coalesce(s.accepting_orders, true) = true
     and s.owner_id is not null
   where coalesce(p.in_stock, true)
     and coalesce(p.is_active, true)
     and not coalesce(p.is_restricted, false)
     and p.stock_qty is not null and p.stock_qty >= 3
     and case when jsonb_typeof(p.weight_options) = 'array'
              then jsonb_array_length(p.weight_options) else 0 end = 1
   order by p.stock_qty desc
   limit 1;

  select pr.id into v_customer
    from public.profiles pr join auth.users u on u.id = pr.id
   where pr.role = 'customer' and coalesce(pr.is_blocked, false) = false
     and pr.id is distinct from v_owner
   limit 1;

  select pr.id into v_other
    from public.profiles pr join auth.users u on u.id = pr.id
   where pr.role = 'customer' and coalesce(pr.is_blocked, false) = false
     and pr.id is distinct from v_owner and pr.id is distinct from v_customer
   limit 1;

  if v_product is null or v_customer is null then
    insert into public.breakq_step2_results values (0, 'Fixtures', 'BLOCKED',
      'Need an approved open shop with a single-size, stock-tracked product (qty >= 3) and a customer.');
    return;
  end if;

  v_items := jsonb_build_array(jsonb_build_object(
    'product_id', v_product, 'weight_label', v_weight, 'quantity', 1));

  insert into public.breakq_step2_results values (0, 'Fixtures', 'INFO',
    format('shop=%s (%s) product=%s customer=%s second_customer=%s',
           v_shop_name, v_shop, v_product, v_customer, coalesce(v_other::text, 'none')));

  ---------------------------------------------------------------------------
  -- H4  The same checkout sent twice creates ONE order and holds stock once.
  ---------------------------------------------------------------------------
  v_msg := null; v_num1 := null; v_num2 := null; v_int := null; v_before := null; v_after := null;
  begin
    select stock_qty into v_before from public.products where id = v_product;
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    v_oid := 'S2TEST-' || gen_random_uuid()::text;
    select r.order_number into v_num1
      from public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items) r;
    select r.order_number into v_num2
      from public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items) r;
    select count(*) into v_int from public.orders where id = v_oid;
    select count(*) into v_int2 from public.order_items where order_id = v_oid;
    select stock_qty into v_after from public.products where id = v_product;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step2_results values (1, 'H4 Retried checkout returns the same order (no duplicate)',
    case when v_msg is not null then 'FAIL'
         when v_int = 1 and v_int2 = 1 and v_num1 = v_num2 and v_after = v_before - 1 then 'PASS'
         else 'FAIL' end,
    coalesce(v_msg, format('orders=%s items=%s order_number %s/%s stock %s -> %s',
                           v_int, v_int2, v_num1, v_num2, v_before, v_after)));

  ---------------------------------------------------------------------------
  -- H4  Two genuinely different checkouts still create two orders.
  ---------------------------------------------------------------------------
  v_msg := null; v_int := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    v_oid := 'S2TEST-' || gen_random_uuid()::text;
    v_oid2 := 'S2TEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
    perform public.create_order_with_items(jsonb_build_object('id', v_oid2, 'shop_id', v_shop), v_items);
    select count(*) into v_int from public.orders where id in (v_oid, v_oid2);
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step2_results values (2, 'H4 Two different checkouts still make two orders',
    case when v_msg is null and v_int = 2 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('%s order(s) created', v_int)));

  ---------------------------------------------------------------------------
  -- H4  Another customer cannot reuse someone else's order reference.
  ---------------------------------------------------------------------------
  if v_other is null then
    insert into public.breakq_step2_results values (3, 'H4 Order reference cannot be reused by another customer',
      'SKIP', 'Only one customer account exists.');
  else
    v_msg := null; v_txt := null;
    begin
      perform set_config('request.jwt.claims',
        json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
      execute 'set local role authenticated';
      v_oid := 'S2TEST-' || gen_random_uuid()::text;
      perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
      perform set_config('request.jwt.claims',
        json_build_object('sub', v_other, 'role', 'authenticated')::text, true);
      select r.pickup_token into v_txt
        from public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items) r;
      raise exception using errcode = 'ZZ001', message = 'rollback';
    exception
      when sqlstate 'ZZ001' then null;
      when others then v_msg := sqlerrm;
    end;
    insert into public.breakq_step2_results values (3, 'H4 Order reference cannot be reused by another customer',
      case when v_txt is not null then 'FAIL' when v_msg ilike '%already in use%' then 'PASS' else 'FAIL' end,
      coalesce(v_msg, 'the second customer received the first customer''s order'));
  end if;

  ---------------------------------------------------------------------------
  -- M1  The right expected_total goes through; a stale one is refused and
  --     leaves nothing behind.
  ---------------------------------------------------------------------------
  v_msg := null; v_total := null; v_int := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    select r.total_amount into v_total
      from public.create_order_with_items(
        jsonb_build_object('id', 'S2TEST-' || gen_random_uuid()::text, 'shop_id', v_shop), v_items) r;
    v_oid := 'S2TEST-' || gen_random_uuid()::text;
    select count(*) into v_int
      from public.create_order_with_items(
        jsonb_build_object('id', v_oid, 'shop_id', v_shop, 'expected_total', v_total), v_items);
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step2_results values (4, 'M1 Order with the correct expected total succeeds',
    case when v_msg is null and v_int = 1 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('total=%s', v_total)));

  v_msg := null; v_txt := null; v_int := null; v_before := null; v_after := null;
  begin
    select stock_qty into v_before from public.products where id = v_product;
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    v_oid := 'S2TEST-' || gen_random_uuid()::text;
    begin
      perform public.create_order_with_items(
        jsonb_build_object('id', v_oid, 'shop_id', v_shop, 'expected_total', 1), v_items);
    exception when others then
      v_txt := sqlerrm;
      get stacked diagnostics v_txt2 = pg_exception_detail;
    end;
    select count(*) into v_int from public.orders where id = v_oid;
    select stock_qty into v_after from public.products where id = v_product;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step2_results values (5, 'M1 A stale total is refused with the new total, nothing saved',
    case when v_msg is not null then 'FAIL'
         when v_txt = 'PRICE_CHANGED' and v_txt2 like '{"total"%' and v_int = 0 and v_after = v_before then 'PASS'
         else 'FAIL' end,
    coalesce(v_msg, format('error=%s detail=%s orders=%s stock %s -> %s', v_txt, v_txt2, v_int, v_before, v_after)));

  ---------------------------------------------------------------------------
  -- H3  A vendor's price edit is the price that gets charged.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null; v_int := null; v_before := null;
  begin
    select current_price into v_before from public.products where id = v_product;
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    update public.products set current_price = v_before + 7 where id = v_product
      returning weight_options->0->>'price' into v_txt;
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    select r.item_total into v_int
      from public.create_order_with_items(
        jsonb_build_object('id', 'S2TEST-' || gen_random_uuid()::text, 'shop_id', v_shop), v_items) r;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step2_results values (6, 'H3 Vendor price edit updates the charged price',
    case when v_msg is null and v_txt = (v_before + 7)::text and v_int = v_before + 7 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('price %s -> %s | size price=%s | customer charged %s', v_before, v_before + 7, v_txt, v_int)));

  ---------------------------------------------------------------------------
  -- H3  No single-size product is left with two different prices.
  ---------------------------------------------------------------------------
  select count(*) into v_int
    from public.products p
   where p.current_price is not null
     and case when jsonb_typeof(p.weight_options) = 'array'
              then jsonb_array_length(p.weight_options) else 0 end = 1
     and (p.weight_options->0->>'price') is distinct from p.current_price::text;
  insert into public.breakq_step2_results values (7, 'H3 Single-size products have one price',
    case when v_int = 0 then 'PASS' else 'FAIL' end, format('%s product(s) out of sync', v_int));

  ---------------------------------------------------------------------------
  -- Regression: vendor stock edits still work and don't touch the price.
  ---------------------------------------------------------------------------
  v_msg := null; v_int := null; v_txt := null; v_before := null;
  begin
    select current_price into v_before from public.products where id = v_product;
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    update public.products set stock_qty = stock_qty + 1 where id = v_product
      returning current_price, weight_options->0->>'price' into v_int, v_txt;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step2_results values (8, 'Vendor stock edit still works, price unchanged',
    case when v_msg is null and v_int = v_before and v_txt = v_before::text then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('price %s, size price %s (was %s)', v_int, v_txt, v_before)));

  ---------------------------------------------------------------------------
  -- M13  A new order keeps the shop's name.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    v_oid := 'S2TEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
    select shop_name into v_txt from public.orders where id = v_oid;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step2_results values (9, 'M13 Order remembers the shop name',
    case when v_msg is null and v_txt = v_shop_name then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('shop_name=%s (expected %s)', v_txt, v_shop_name)));

  ---------------------------------------------------------------------------
  -- Full lifecycle: place -> confirm -> preparing -> ready -> pickup scan ->
  -- completed, with the server timestamps stamped at each step.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null; v_token := null; v_txt2 := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    v_oid := 'S2TEST-' || gen_random_uuid()::text;
    select r.pickup_token into v_token
      from public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items) r;
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    update public.orders set status = 'Order Confirmed'  where id = v_oid;
    update public.orders set status = 'Preparing'        where id = v_oid;
    update public.orders set status = 'Ready for Pickup' where id = v_oid;
    select c.status into v_txt from public.complete_order_by_pickup_token(v_token) c;
    select format('confirmed=%s preparing=%s ready=%s completed=%s',
                  confirmed_at is not null, preparing_at is not null,
                  ready_at is not null, completed_at is not null)
      into v_txt2 from public.orders where id = v_oid;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step2_results values (10, 'Order lifecycle: confirm, prepare, ready, pickup scan',
    case when v_msg is null and v_txt = 'Completed'
              and v_txt2 = 'confirmed=t preparing=t ready=t completed=t' then 'PASS'
         else 'FAIL' end,
    coalesce(v_msg, format('final status=%s | %s', v_txt, v_txt2)));
end $$;


-- -----------------------------------------------------------------------------
-- Results.
-- -----------------------------------------------------------------------------
select seq, test, result, detail from public.breakq_step2_results order by seq;
