-- =============================================================================
--  BreakQ — Verification for ORDER_SERVER_AUTHORITY.sql
--
--  RUN THIS WHOLE FILE AT ONCE. Nothing to fill in.
--
--  It finds its own test shop, product and customer, runs six checks against
--  create_order_with_items, and prints a results table at the end.
--
--  Every test order is rolled back to a savepoint, so NO test order, NO stock
--  change and NO promo usage is ever saved. The only thing left behind is the
--  results table, which you can drop when you are done:
--      drop table public.breakq_test_results;
--
--  Re-run this before every release.
-- =============================================================================

drop table if exists public.breakq_test_results;

create table public.breakq_test_results (
  seq    int,
  test   text,
  result text,
  detail text
);

do $$
declare
  v_customer   uuid;
  v_shop       text;
  v_shop_name  text;
  v_product    text;
  v_prod_name  text;
  v_weight     text;
  v_price      int;
  v_stock      int;
  v_other_prod text;
  v_bad_shop   text;
  v_order      jsonb;
  v_oid        text;
  v_got_item   int;
  v_got_total  int;
  v_got_user   text;
  v_after      int;
  v_ok         boolean;
  v_msg        text;
begin
  ---------------------------------------------------------------------------
  -- Fixtures: a real approved shop with a real stock-tracked product,
  -- and a real customer who has a profiles row (orders.user_id FKs to it).
  ---------------------------------------------------------------------------
  select p.shop_id, p.id, p.name, s.name,
         coalesce(w.label, p.unit),
         coalesce(w.price, p.current_price),
         p.stock_qty
    into v_shop, v_product, v_prod_name, v_shop_name, v_weight, v_price, v_stock
    from public.products p
    join public.shops s
      on s.id = p.shop_id
     and s.status::text = 'approved'
     and coalesce(s.is_deleted, false) = false
     and coalesce(s.accepting_orders, true) = true
    left join lateral (
      select (wo->>'label') as label, (wo->>'price')::int as price
        from jsonb_array_elements(coalesce(p.weight_options, '[]'::jsonb)) wo
       limit 1
    ) w on true
   where p.in_stock is true
     and coalesce(p.is_active, true) = true
     and coalesce(p.is_restricted, false) = false
     and p.stock_qty is not null
     and p.stock_qty >= 2
   order by p.stock_qty desc
   limit 1;

  if v_product is null then
    insert into public.breakq_test_results values
      (0, 'Fixtures', 'BLOCKED',
       'No approved, accepting shop with a stock-tracked in-stock product (qty >= 2) was found.');
    return;
  end if;

  select u.id into v_customer
    from auth.users u
    join public.profiles pr on pr.id = u.id
   where pr.role = 'customer'
     and coalesce(pr.is_blocked, false) = false
   limit 1;

  if v_customer is null then
    insert into public.breakq_test_results values
      (0, 'Fixtures', 'BLOCKED', 'No customer profile found to test with.');
    return;
  end if;

  insert into public.breakq_test_results values
    (0, 'Fixtures', 'INFO',
     format('shop=%s (%s) | product=%s (%s) | weight=%s | real price=%s | stock=%s | customer=%s',
            v_shop_name, v_shop, v_prod_name, v_product, v_weight, v_price, v_stock, v_customer));

  -- Impersonate the customer. auth.uid() reads this setting; is_local = true
  -- keeps it scoped to this transaction.
  perform set_config('request.jwt.claims',
                     json_build_object('sub', v_customer, 'role', 'authenticated')::text,
                     true);

  -- Reusable order envelope. Every test overrides what it needs.
  v_order := jsonb_build_object(
    'customer_name',   'BreakQ Test',
    'customer_email',  'test@breakq.local',
    'customer_mobile', '0000000000',
    'status',          'Order Placed',
    'order_date',      'Verification run',
    'qr_code_payload', ''
  );

  ---------------------------------------------------------------------------
  -- TEST 1 — a lying client cannot set the price.
  -- Claims the order costs Rs.1 and each unit costs Rs.1. Expect the server
  -- to bill the real price x 2 and ignore every number sent.
  ---------------------------------------------------------------------------
  v_msg := null; v_got_item := null; v_got_total := null;
  begin
    select r.item_total, r.total_amount
      into v_got_item, v_got_total
      from public.create_order_with_items(
        v_order || jsonb_build_object(
          'id',             'TEST-' || gen_random_uuid()::text,
          'shop_id',        v_shop,
          'total_amount',   1,
          'promo_discount', 99999,
          'user_id',        '00000000-0000-0000-0000-000000000000'
        ),
        jsonb_build_array(jsonb_build_object(
          'product_id',   v_product,
          'weight_label', v_weight,
          'quantity',     2,
          'unit_price',   1,
          'line_total',   2
        ))
      ) r;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;

  insert into public.breakq_test_results values (
    1, 'Client cannot set the price',
    case when v_msg is not null              then 'ERROR'
         when v_got_item = v_price * 2       then 'PASS'
         else 'FAIL' end,
    coalesce(v_msg, format('expected item_total=%s, got %s (billed %s)',
                           v_price * 2, v_got_item, v_got_total)));

  ---------------------------------------------------------------------------
  -- TEST 2 — cannot order more than the shop has.
  ---------------------------------------------------------------------------
  v_msg := null; v_ok := false;
  begin
    perform public.create_order_with_items(
      v_order || jsonb_build_object(
        'id',      'TEST-' || gen_random_uuid()::text,
        'shop_id', v_shop
      ),
      jsonb_build_array(jsonb_build_object(
        'product_id',   v_product,
        'weight_label', v_weight,
        'quantity',     v_stock + 1000
      ))
    );
    v_ok := true;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;

  insert into public.breakq_test_results values (
    2, 'Cannot oversell stock',
    case when v_ok then 'FAIL' else 'PASS' end,
    coalesce(v_msg, 'the order went through when it should have been refused'));

  ---------------------------------------------------------------------------
  -- TEST 3 — a successful order actually reserves stock.
  ---------------------------------------------------------------------------
  v_msg := null; v_after := null;
  begin
    perform public.create_order_with_items(
      v_order || jsonb_build_object(
        'id',      'TEST-' || gen_random_uuid()::text,
        'shop_id', v_shop
      ),
      jsonb_build_array(jsonb_build_object(
        'product_id',   v_product,
        'weight_label', v_weight,
        'quantity',     1
      ))
    );
    select p.stock_qty into v_after from public.products p where p.id = v_product;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;

  insert into public.breakq_test_results values (
    3, 'Stock is reserved on order',
    case when v_msg is not null          then 'ERROR'
         when v_after = v_stock - 1      then 'PASS'
         else 'FAIL' end,
    coalesce(v_msg, format('stock before=%s, after=%s', v_stock, v_after)));

  ---------------------------------------------------------------------------
  -- TEST 4 — a product from another shop cannot be smuggled in.
  ---------------------------------------------------------------------------
  select p.id into v_other_prod
    from public.products p
   where p.shop_id is distinct from v_shop
     and p.shop_id is not null
   limit 1;

  if v_other_prod is null then
    insert into public.breakq_test_results values
      (4, 'Cannot mix shops in one order', 'SKIP', 'Only one shop has products.');
  else
    v_msg := null; v_ok := false;
    begin
      perform public.create_order_with_items(
        v_order || jsonb_build_object(
          'id',      'TEST-' || gen_random_uuid()::text,
          'shop_id', v_shop
        ),
        jsonb_build_array(jsonb_build_object(
          'product_id',   v_other_prod,
          'weight_label', '',
          'quantity',     1
        ))
      );
      v_ok := true;
      raise exception using errcode = 'ZZ001', message = 'rollback';
    exception
      when sqlstate 'ZZ001' then null;
      when others then v_msg := sqlerrm;
    end;

    insert into public.breakq_test_results values (
      4, 'Cannot mix shops in one order',
      case when v_ok then 'FAIL' else 'PASS' end,
      coalesce(v_msg, 'another shop''s product was accepted'));
  end if;

  ---------------------------------------------------------------------------
  -- TEST 5 — the buyer is always auth.uid(), never the payload.
  ---------------------------------------------------------------------------
  v_msg := null; v_got_user := null;
  v_oid := 'TEST-' || gen_random_uuid()::text;
  begin
    perform public.create_order_with_items(
      v_order || jsonb_build_object(
        'id',      v_oid,
        'shop_id', v_shop,
        'user_id', '00000000-0000-0000-0000-000000000000'
      ),
      jsonb_build_array(jsonb_build_object(
        'product_id',   v_product,
        'weight_label', v_weight,
        'quantity',     1
      ))
    );
    select o.user_id::text into v_got_user from public.orders o where o.id = v_oid;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;

  insert into public.breakq_test_results values (
    5, 'Buyer comes from the session, not the payload',
    case when v_msg is not null                 then 'ERROR'
         when v_got_user = v_customer::text     then 'PASS'
         else 'FAIL' end,
    coalesce(v_msg, format('signed in as %s, order recorded %s', v_customer, v_got_user)));

  ---------------------------------------------------------------------------
  -- TEST 6 — a shop that is not approved cannot take orders.
  ---------------------------------------------------------------------------
  select s.id into v_bad_shop
    from public.shops s
   where s.status::text <> 'approved'
      or coalesce(s.is_deleted, false)
      or coalesce(s.accepting_orders, true) = false
   limit 1;

  if v_bad_shop is null then
    insert into public.breakq_test_results values
      (6, 'Unapproved shop cannot take orders', 'SKIP',
       'Every shop is approved and accepting, so there is nothing to test against.');
  else
    v_msg := null; v_ok := false;
    begin
      perform public.create_order_with_items(
        v_order || jsonb_build_object(
          'id',      'TEST-' || gen_random_uuid()::text,
          'shop_id', v_bad_shop
        ),
        jsonb_build_array(jsonb_build_object(
          'product_id',   v_product,
          'weight_label', v_weight,
          'quantity',     1
        ))
      );
      v_ok := true;
      raise exception using errcode = 'ZZ001', message = 'rollback';
    exception
      when sqlstate 'ZZ001' then null;
      when others then v_msg := sqlerrm;
    end;

    insert into public.breakq_test_results values (
      6, 'Unapproved shop cannot take orders',
      case when v_ok then 'FAIL' else 'PASS' end,
      coalesce(v_msg, 'an unapproved shop accepted an order'));
  end if;
end $$;


-- -----------------------------------------------------------------------------
-- Results. Everything should say PASS (or SKIP / INFO).
-- -----------------------------------------------------------------------------
select seq, test, result, detail
  from public.breakq_test_results
 order by seq;


-- =============================================================================
-- TEST 7 — the race. Two customers, one last unit. Must be done by hand,
-- because it needs two connections at the same time.
--
--   1. Note a product id and set its stock to exactly 1:
--        update public.products set stock_qty = 1, in_stock = true
--         where id = '<product id from the Fixtures row above>';
--
--   2. Open TWO SQL editor tabs. Run this in tab A, WITHOUT committing:
--        begin;
--        select set_config('request.jwt.claims',
--                 json_build_object('sub', '<customer uuid>', 'role','authenticated')::text, true);
--        select * from public.create_order_with_items(
--          jsonb_build_object('id','RACE-A','shop_id','<shop id>',
--            'customer_name','A','customer_email','a@t.local','customer_mobile','0',
--            'status','Order Placed','order_date','x','qr_code_payload',''),
--          jsonb_build_array(jsonb_build_object(
--            'product_id','<product id>','weight_label','<weight>','quantity',1)));
--
--   3. Run the same in tab B with id 'RACE-B'. It should HANG on the row lock.
--
--   4. Back in tab A:  commit;
--
--   5. Tab B should immediately finish with "Only 0 left of <product>".
--
--   PASS = tab B waits, then fails.  FAIL = both succeed.
--
--   6. Clean up: rollback; in tab B, delete the RACE-A order, restore stock_qty.
-- =============================================================================
