-- =============================================================================
--  BreakQ — Tests for STEP4_VENDOR_OPERATIONS.sql
--
--  RUN THE WHOLE FILE AT ONCE, after STEP4_VENDOR_OPERATIONS.sql.
--  Every test runs inside a savepoint that is rolled back: no order, product,
--  shop, stock or price is changed and no push/email is sent.
--
--  PASS = works as intended.  FAIL = broken.  SKIP = no suitable test data.
--  INFO = facts to read, not pass/fail.
--
--  Tidy up afterwards:  drop table public.breakq_step4_results;
-- =============================================================================

drop table if exists public.breakq_step4_results;
create table public.breakq_step4_results (seq int, test text, result text, detail text);

do $$
declare
  v_shop      text;
  v_owner     uuid;
  v_product   text;
  v_weight    text;
  v_shop2     text;
  v_owner2    uuid;
  v_product2  text;
  v_weight2   text;
  v_customer  uuid;
  v_items     jsonb;
  v_items2    jsonb;
  v_oid       text;
  v_tok       text;
  v_txt       text;
  v_txt2      text;
  v_txt3      text;
  v_msg       text;
  v_n         int;
  v_m         int;
  v_before    int;
  v_after     int;
  v_b1        boolean;
  v_b2        boolean;
  v_b3        boolean;
begin
  ---------------------------------------------------------------------------
  -- Fixtures
  ---------------------------------------------------------------------------
  select p.shop_id, s.owner_id, p.id, coalesce(p.weight_options->0->>'label', p.unit)
    into v_shop, v_owner, v_product, v_weight
    from public.products p
    join public.shops s on s.id = p.shop_id
    join public.profiles op on op.id = s.owner_id and not coalesce(op.is_blocked, false)
   where s.status::text = 'approved'
     and not coalesce(s.is_deleted, false)
     and coalesce(s.accepting_orders, true)
     and coalesce(p.in_stock, true)
     and coalesce(p.is_active, true)
     and not coalesce(p.is_restricted, false)
     and p.stock_qty is not null and p.stock_qty >= 6
   order by p.stock_qty desc
   limit 1;

  select p.shop_id, s.owner_id, p.id, coalesce(p.weight_options->0->>'label', p.unit)
    into v_shop2, v_owner2, v_product2, v_weight2
    from public.products p
    join public.shops s on s.id = p.shop_id
   where s.status::text = 'approved'
     and not coalesce(s.is_deleted, false)
     and coalesce(s.accepting_orders, true)
     and s.owner_id is not null
     and s.owner_id is distinct from v_owner
     and coalesce(p.in_stock, true)
     and coalesce(p.is_active, true)
     and not coalesce(p.is_restricted, false)
     and (p.stock_qty is null or p.stock_qty >= 1)
   limit 1;

  select pr.id into v_customer
    from public.profiles pr join auth.users u on u.id = pr.id
   where pr.role = 'customer' and not coalesce(pr.is_blocked, false)
     and pr.id is distinct from v_owner and pr.id is distinct from v_owner2
   limit 1;

  if v_product is null or v_customer is null then
    insert into public.breakq_step4_results values (0, 'Fixtures', 'BLOCKED',
      'Need an approved open shop (owner not blocked) with a stock-tracked product (qty >= 6) and a customer.');
    return;
  end if;

  insert into public.breakq_step4_results values (0, 'Fixtures', 'INFO',
    format('shop=%s product=%s | second shop=%s | customer=%s',
           v_shop, v_product, coalesce(v_shop2, 'none'), v_customer));

  v_items  := jsonb_build_array(jsonb_build_object('product_id', v_product, 'weight_label', v_weight, 'quantity', 1));
  v_items2 := jsonb_build_array(jsonb_build_object('product_id', v_product2, 'weight_label', v_weight2, 'quantity', 1));

  ---------------------------------------------------------------------------
  -- 1. A vendor can't mark an order Completed with a plain status edit.
  ---------------------------------------------------------------------------
  v_msg := null; v_n := null;
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    v_oid := 'S4TEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
    update public.orders set status = 'Order Confirmed'  where id = v_oid;
    update public.orders set status = 'Preparing'        where id = v_oid;
    update public.orders set status = 'Ready for Pickup' where id = v_oid;
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    begin
      update public.orders set status = 'Completed' where id = v_oid;
      get diagnostics v_n = row_count;
    exception when others then
      v_msg := sqlerrm;
    end;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := coalesce(v_msg, 'setup: ' || sqlerrm);
  end;
  insert into public.breakq_step4_results values (1, 'Vendor can''t complete an order without the pickup code',
    case when v_msg ilike '%pickup%' then 'PASS'
         when v_n = 1 then 'FAIL'
         when v_n = 0 then 'INFO'
         else 'ERROR' end,
    coalesce(v_msg, format('%s row(s) updated', v_n)));

  ---------------------------------------------------------------------------
  -- 2. The pickup code still completes it.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    v_oid := 'S4TEST-' || gen_random_uuid()::text;
    select r.pickup_token into v_tok
      from public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items) r;
    update public.orders set status = 'Order Confirmed'  where id = v_oid;
    update public.orders set status = 'Preparing'        where id = v_oid;
    update public.orders set status = 'Ready for Pickup' where id = v_oid;
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    select c.status into v_txt from public.complete_order_by_pickup_token(v_tok) c;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step4_results values (2, 'Pickup code still completes a Ready order',
    case when v_msg is null and v_txt = 'Completed' then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('status=%s', v_txt)));

  ---------------------------------------------------------------------------
  -- 3/4. Vendor cancels with a reason; who/why are saved; stock comes back
  --      for both lines of the same product.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null; v_txt2 := null; v_txt3 := null; v_before := null; v_after := null;
  begin
    select stock_qty into v_before from public.products where id = v_product;
    perform set_config('request.jwt.claims', json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    v_oid := 'S4TEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items || v_items);
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    perform public.vendor_cancel_order(v_oid, 'Item out of stock');
    select status, cancelled_by, cancel_reason into v_txt, v_txt2, v_txt3 from public.orders where id = v_oid;
    select stock_qty into v_after from public.products where id = v_product;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step4_results values
    (3, 'Vendor cancel saves who and why',
     case when v_msg is null and v_txt = 'Cancelled' and v_txt2 = 'vendor' and v_txt3 = 'Item out of stock' then 'PASS' else 'FAIL' end,
     coalesce(v_msg, format('status=%s cancelled_by=%s reason=%s', v_txt, coalesce(v_txt2, '(empty)'), coalesce(v_txt3, '(empty)')))),
    (4, 'Cancelling returns stock for every line (2 lines of one product)',
     case when v_msg is null and v_after = v_before then 'PASS' else 'FAIL' end,
     coalesce(v_msg, format('stock before order=%s, after cancel=%s', v_before, v_after)));

  ---------------------------------------------------------------------------
  -- 5. A reason is required.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    v_oid := 'S4TEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    begin
      v_txt := public.vendor_cancel_order(v_oid, '  ');
    exception when others then
      v_msg := sqlerrm;
    end;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := coalesce(v_msg, 'setup: ' || sqlerrm);
  end;
  insert into public.breakq_step4_results values (5, 'Vendor cancel needs a reason',
    case when v_msg ilike '%why%' then 'PASS' else 'FAIL' end,
    coalesce(v_msg, 'cancelled without a reason'));

  ---------------------------------------------------------------------------
  -- 6/7. Another shop's orders: can't cancel, read or change them.
  ---------------------------------------------------------------------------
  if v_shop2 is null then
    insert into public.breakq_step4_results values
      (6, 'Vendor can''t cancel another shop''s order', 'SKIP', 'Only one approved shop with products.'),
      (7, 'Vendor can''t read or change another shop''s orders', 'SKIP', 'Only one approved shop with products.');
  else
    v_msg := null; v_txt := null;
    begin
      perform set_config('request.jwt.claims', json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
      v_oid := 'S4TEST-' || gen_random_uuid()::text;
      perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop2), v_items2);
      perform set_config('request.jwt.claims', json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
      begin
        v_txt := public.vendor_cancel_order(v_oid, 'Testing another shop');
      exception when others then
        v_msg := sqlerrm;
      end;
      raise exception using errcode = 'ZZ001', message = 'rollback';
    exception
      when sqlstate 'ZZ001' then null;
      when others then v_msg := coalesce(v_msg, 'setup: ' || sqlerrm);
    end;
    insert into public.breakq_step4_results values (6, 'Vendor can''t cancel another shop''s order',
      case when v_msg ilike '%another shop%' then 'PASS' else 'FAIL' end,
      coalesce(v_msg, 'the other shop''s order was cancelled'));

    v_msg := null; v_n := null; v_m := null;
    begin
      perform set_config('request.jwt.claims', json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
      v_oid := 'S4TEST-' || gen_random_uuid()::text;
      perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop2), v_items2);
      perform set_config('request.jwt.claims', json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
      execute 'set local role authenticated';
      select count(*) into v_n from public.orders where id = v_oid;
      update public.orders set status = 'Order Confirmed' where id = v_oid;
      get diagnostics v_m = row_count;
      raise exception using errcode = 'ZZ001', message = 'rollback';
    exception
      when sqlstate 'ZZ001' then null;
      when others then v_msg := sqlerrm;
    end;
    insert into public.breakq_step4_results values (7, 'Vendor can''t read or change another shop''s orders',
      case when v_msg is null and v_n = 0 and v_m = 0 then 'PASS' else 'FAIL' end,
      coalesce(v_msg, format('saw %s row(s), changed %s row(s)', v_n, v_m)));
  end if;

  ---------------------------------------------------------------------------
  -- 8. Vendor can still confirm their own new order (regression).
  ---------------------------------------------------------------------------
  v_msg := null; v_n := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    v_oid := 'S4TEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    update public.orders set status = 'Order Confirmed' where id = v_oid;
    get diagnostics v_n = row_count;
    select status into v_txt from public.orders where id = v_oid;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step4_results values (8, 'Vendor can still confirm their own order',
    case when v_msg is null and v_n = 1 and v_txt = 'Order Confirmed' then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('%s row(s), status=%s', v_n, v_txt)));

  ---------------------------------------------------------------------------
  -- 9. Customer cancel still works, can still be read, and is recorded as 'customer'.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null; v_txt2 := null;
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    v_oid := 'S4TEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
    execute 'set local role authenticated';
    update public.orders set status = 'Cancelled' where id = v_oid;
    select status, cancelled_by into v_txt, v_txt2 from public.orders where id = v_oid;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step4_results values (9, 'Customer cancel works and is recorded as customer',
    case when v_msg is null and v_txt = 'Cancelled' and v_txt2 = 'customer' then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('status=%s cancelled_by=%s', v_txt, coalesce(v_txt2, '(empty — another trigger may clear it)'))));

  ---------------------------------------------------------------------------
  -- 10. A cancellation with no signed-in user (the expiry job) is 'system'.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    v_oid := 'S4TEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
    perform set_config('request.jwt.claims', '', true);
    update public.orders set status = 'Cancelled' where id = v_oid;
    select cancelled_by into v_txt from public.orders where id = v_oid;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step4_results values (10, 'Automatic cancellation is recorded as system',
    case when v_msg is null and v_txt = 'system' then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('cancelled_by=%s', coalesce(v_txt, '(empty)'))));

  ---------------------------------------------------------------------------
  -- 11-14. Product rules for the vendor app.
  ---------------------------------------------------------------------------
  v_msg := null; v_n := null;
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    update public.products set current_price = current_price + 1 where id = v_product;
    get diagnostics v_n = row_count;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step4_results values (11, 'Vendor can still change their own price',
    case when v_msg is null and v_n = 1 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('%s row(s) updated', v_n)));

  v_txt := null; v_txt2 := null; v_txt3 := null; v_msg := null;
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    begin
      update public.products set current_price = 0 where id = v_product;
      v_txt := 'allowed';
    exception when others then v_txt := sqlerrm;
    end;
    begin
      update public.products set stock_qty = -1 where id = v_product;
      v_txt2 := 'allowed';
    exception when others then v_txt2 := sqlerrm;
    end;
    begin
      update public.products set shop_id = 'S4TEST-other-shop' where id = v_product;
      v_txt3 := 'allowed';
    exception when others then v_txt3 := sqlerrm;
    end;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step4_results values
    (12, 'Vendor can''t set a price of 0',
     case when v_txt ilike '%more than%' then 'PASS' else 'FAIL' end, coalesce(v_msg, v_txt)),
    (13, 'Vendor can''t set negative stock',
     case when v_txt2 ilike '%negative%' then 'PASS' else 'FAIL' end, coalesce(v_msg, v_txt2)),
    (14, 'Vendor can''t move a product to another shop',
     case when v_txt3 ilike '%moved%' then 'PASS' else 'FAIL' end, coalesce(v_msg, v_txt3));

  ---------------------------------------------------------------------------
  -- 15. Availability follows stock; a manual "unavailable" is kept.
  ---------------------------------------------------------------------------
  v_msg := null; v_b1 := null; v_b2 := null; v_b3 := null;
  begin
    update public.products set in_stock = true where id = v_product;
    update public.products set stock_qty = 0 where id = v_product;
    select in_stock into v_b1 from public.products where id = v_product;
    update public.products set stock_qty = 5 where id = v_product;
    select in_stock into v_b2 from public.products where id = v_product;
    update public.products set in_stock = false where id = v_product;
    update public.products set stock_qty = 6 where id = v_product;
    select in_stock into v_b3 from public.products where id = v_product;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step4_results values (15, 'Stock 0 = unavailable, restock = available, manual off kept',
    case when v_msg is null and v_b1 = false and v_b2 = true and v_b3 = false then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('at 0: %s | restocked: %s | manual off then restocked: %s', v_b1, v_b2, v_b3)));

  ---------------------------------------------------------------------------
  -- 16. A shop that isn't approved can't change its catalogue, and customers
  --     don't see its products.
  ---------------------------------------------------------------------------
  v_msg := null; v_n := null; v_m := null;
  begin
    update public.shops set status = 'pending' where id = v_shop;
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    update public.products set current_price = current_price + 1 where id = v_product;
    get diagnostics v_n = row_count;
    perform set_config('request.jwt.claims', json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    select count(*) into v_m from public.products where id = v_product;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step4_results values (16, 'Unapproved shop: catalogue locked and hidden from customers',
    case when v_msg is null and v_n = 0 and v_m = 0 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('vendor changed %s row(s), customer saw %s row(s)', v_n, v_m)));

  ---------------------------------------------------------------------------
  -- 17. Inactive products are hidden from customers but not from their owner.
  ---------------------------------------------------------------------------
  v_msg := null; v_n := null; v_m := null;
  begin
    update public.products set is_active = false where id = v_product;
    perform set_config('request.jwt.claims', json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    select count(*) into v_n from public.products where id = v_product;
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    select count(*) into v_m from public.products where id = v_product;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step4_results values (17, 'Inactive product hidden from customers, visible to owner',
    case when v_msg is null and v_n = 0 and v_m = 1 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('customer saw %s, owner saw %s', v_n, v_m)));

  ---------------------------------------------------------------------------
  -- 18. An owner with a live shop can't register a second one.
  ---------------------------------------------------------------------------
  v_msg := null;
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    begin
      insert into public.shops (id, name, owner_name, address, phone)
      values ('S4TEST-' || gen_random_uuid()::text, 'Second shop', 'Test', 'Test address', '0000000000');
      v_msg := 'allowed';
    exception when others then
      v_msg := sqlerrm;
    end;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := coalesce(v_msg, 'setup: ' || sqlerrm);
  end;
  insert into public.breakq_step4_results values (18, 'Owner can''t register a second shop',
    case when v_msg ilike '%already have a shop%' then 'PASS'
         when v_msg = 'allowed' then 'FAIL'
         else 'INFO' end,
    v_msg);
end $$;


-- -----------------------------------------------------------------------------
-- 19-20. Facts to read.
-- -----------------------------------------------------------------------------
insert into public.breakq_step4_results
select 19, 'product-images upload rules', 'INFO',
       coalesce(string_agg(policyname || ' (' || cmd || ')', ', ' order by policyname), 'none')
  from pg_policies
 where schemaname = 'storage' and tablename = 'objects'
   and (coalesce(qual, '') ilike '%product-images%' or coalesce(with_check, '') ilike '%product-images%');

insert into public.breakq_step4_results
select 20, 'Does zz_protect_admin_columns touch cancellation fields?', 'INFO',
       coalesce((select case when pg_get_functiondef(t.tgfoid) ilike '%cancel%' then 'yes — it mentions cancel columns'
                             else 'no' end
                   from pg_trigger t
                  where t.tgrelid = 'public.orders'::regclass
                    and t.tgname = 'zz_protect_admin_columns'), 'trigger not found');


-- -----------------------------------------------------------------------------
-- Results.
-- -----------------------------------------------------------------------------
select seq, test, result, detail from public.breakq_step4_results order by seq;
