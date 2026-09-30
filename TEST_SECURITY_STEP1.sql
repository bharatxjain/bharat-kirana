-- =============================================================================
--  BreakQ — Tests for SECURITY_STEP1.sql
--
--  RUN THE WHOLE FILE AT ONCE, after SECURITY_STEP1.sql. Nothing is saved:
--  every test runs inside a savepoint that is rolled back, so no shop, order,
--  rating, profile or photo is changed, and no webhook/email/push is sent
--  (pg_net only sends after a commit).
--
--  Each test impersonates a real user (vendor, customer, signed-out, or a
--  fresh account) and tries something directly against the database — exactly
--  what a modified APK or curl could do.
--
--    PASS  = protected / still works as it should
--    FAIL  = hole still open, or a legitimate flow broke
--    SKIP  = no suitable test data
--    CHECK = refused, but for a reason other than the security rule (read detail)
--    INFO  = live configuration, for review (P17/P18)
--
--  Tidy up afterwards:  drop table public.breakq_sec_results;
--  Then also re-run TEST_RLS_WRITE_HOLES.sql and TEST_ORDER_SERVER_AUTHORITY.sql.
-- =============================================================================

drop table if exists public.breakq_sec_results;
create table public.breakq_sec_results (seq int, test text, result text, detail text);

do $$
declare
  v_shop          text;
  v_owner         uuid;
  v_product       text;
  v_weight        text;
  v_customer      uuid;
  v_cust_email    text;
  v_fresh         uuid;
  v_items         jsonb;
  v_oid           text;
  v_txt           text;
  v_txt2          text;
  v_int           int;
  v_before        int;
  v_bool          boolean;
  v_partner_was   boolean;
  v_name_was      text;
  v_msg           text;
begin
  ---------------------------------------------------------------------------
  -- Fixtures
  ---------------------------------------------------------------------------
  select p.shop_id, s.owner_id, p.id, coalesce(w.label, p.unit, '')
    into v_shop, v_owner, v_product, v_weight
    from public.products p
    join public.shops s
      on s.id = p.shop_id
     and s.status::text = 'approved'
     and coalesce(s.is_deleted, false) = false
     and coalesce(s.accepting_orders, true) = true
     and s.owner_id is not null
    left join lateral (
      select wo->>'label' as label
        from jsonb_array_elements(coalesce(p.weight_options, '[]'::jsonb)) wo
       limit 1
    ) w on true
   where coalesce(p.in_stock, true)
     and coalesce(p.is_active, true)
     and not coalesce(p.is_restricted, false)
     and (p.stock_qty is null or p.stock_qty >= 1)
   limit 1;

  select pr.id, lower(trim(u.email))
    into v_customer, v_cust_email
    from public.profiles pr
    join auth.users u on u.id = pr.id
   where pr.role = 'customer'
     and coalesce(pr.is_blocked, false) = false
     and pr.id is distinct from v_owner
   limit 1;

  -- An account that has never ordered and owns no shop (for the shop-insert test).
  select pr.id into v_fresh
    from public.profiles pr
    join auth.users u on u.id = pr.id
   where not exists (select 1 from public.orders o where o.user_id = pr.id)
     and not exists (select 1 from public.shops s where s.owner_id = pr.id)
     and coalesce(pr.is_blocked, false) = false
     and coalesce(pr.role, 'customer') not in ('admin', 'super_admin')
   limit 1;

  if v_shop is null or v_customer is null then
    insert into public.breakq_sec_results values
      (0, 'Fixtures', 'BLOCKED',
       'Need an approved, open shop with an owner and an in-stock product, plus a separate customer.');
    return;
  end if;

  v_items := jsonb_build_array(jsonb_build_object(
    'product_id', v_product, 'weight_label', v_weight, 'quantity', 1));

  select name, coalesce(is_partner, false) into v_name_was, v_partner_was
    from public.shops where id = v_shop;

  insert into public.breakq_sec_results values
    (0, 'Fixtures', 'INFO',
     format('shop=%s owner=%s product=%s customer=%s fresh_account=%s',
            v_shop, v_owner, v_product, v_customer, coalesce(v_fresh::text, 'none')));

  ---------------------------------------------------------------------------
  -- SEC-1  Vendor cannot approve their own shop.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null;
  begin
    update public.shops set status = 'pending' where id = v_shop;
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    update public.shops set status = 'approved' where id = v_shop
      returning status::text into v_txt;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (1, 'SEC-1 Vendor cannot approve own shop',
    case when v_txt = 'approved' then 'FAIL' else 'PASS' end,
    coalesce(v_msg, 'status after attempt: ' || coalesce(v_txt, '(update refused)')));

  ---------------------------------------------------------------------------
  -- SEC-2  Vendor cannot fake their rating.
  ---------------------------------------------------------------------------
  v_msg := null; v_int := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    update public.shops set rating_count = 9999, avg_rating = 5 where id = v_shop
      returning rating_count into v_int;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (2, 'SEC-2 Vendor cannot fake rating',
    case when v_int = 9999 then 'FAIL' else 'PASS' end,
    coalesce(v_msg, 'rating_count after attempt: ' || coalesce(v_int::text, '(update refused)')));

  ---------------------------------------------------------------------------
  -- Regression: vendor can still edit their shop; partner flag is kept.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null; v_bool := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    update public.shops
       set name = name || ' (t)', is_partner = not v_partner_was
     where id = v_shop
     returning name, is_partner into v_txt, v_bool;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (3, 'Vendor can still edit own shop (partner flag kept)',
    case when v_msg is not null then 'FAIL'
         when v_txt = v_name_was || ' (t)' and v_bool = v_partner_was then 'PASS'
         else 'FAIL' end,
    coalesce(v_msg, format('name=%s is_partner=%s (was %s)', v_txt, v_bool, v_partner_was)));

  ---------------------------------------------------------------------------
  -- SEC-1  A new shop cannot be inserted already approved / rated.
  ---------------------------------------------------------------------------
  if v_fresh is null then
    insert into public.breakq_sec_results values (4, 'SEC-1 New shop cannot start approved', 'SKIP',
      'No account without orders and without a shop to test with.');
  else
    v_msg := null; v_txt := null; v_bool := null; v_int := null;
    begin
      perform set_config('request.jwt.claims',
        json_build_object('sub', v_fresh, 'role', 'authenticated')::text, true);
      execute 'set local role authenticated';
      insert into public.shops (
        id, name, owner_name, owner_id, address, phone, lat, lng, primary_category,
        years_in_business, is_partner, accepting_orders, auto_confirm, packing_time,
        open_time, close_time, status, avg_rating, rating_count)
      values (
        'SECTEST-' || gen_random_uuid()::text, 'Security test shop', 'Test', v_fresh,
        'nowhere', '0000000000', 0, 0, 'Grocery',
        0, true, true, true, 15,
        '08:00', '21:00', 'approved', 5, 999)
      returning status::text, is_partner, rating_count into v_txt, v_bool, v_int;
      raise exception using errcode = 'ZZ001', message = 'rollback';
    exception
      when sqlstate 'ZZ001' then null;
      when others then v_msg := sqlerrm;
    end;
    insert into public.breakq_sec_results values (4, 'SEC-1 New shop cannot start approved',
      case when v_msg is not null then 'CHECK'
           when v_txt = 'pending' and coalesce(v_int, 0) = 0 and v_bool is false then 'PASS'
           else 'FAIL' end,
      coalesce(v_msg, format('status=%s is_partner=%s rating_count=%s', v_txt, v_bool, v_int)));
  end if;

  ---------------------------------------------------------------------------
  -- SEC-3  Non-approved shops are private.
  ---------------------------------------------------------------------------
  v_msg := null; v_int := null;
  begin
    update public.shops set status = 'rejected' where id = v_shop;
    perform set_config('request.jwt.claims', '{"role":"anon"}', true);
    execute 'set local role anon';
    select count(*) into v_int from public.shops where id = v_shop;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (5, 'SEC-3 Signed-out visitor cannot see a rejected shop',
    case when v_msg is not null then 'ERROR' when v_int > 0 then 'FAIL' else 'PASS' end,
    coalesce(v_msg, format('saw %s row(s)', v_int)));

  v_msg := null; v_int := null;
  begin
    update public.shops set status = 'pending' where id = v_shop;
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    select count(*) into v_int from public.shops where id = v_shop;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (6, 'SEC-3 Another customer cannot see a pending shop',
    case when v_msg is not null then 'ERROR' when v_int > 0 then 'FAIL' else 'PASS' end,
    coalesce(v_msg, format('saw %s row(s)', v_int)));

  v_msg := null; v_int := null;
  begin
    update public.shops set status = 'pending' where id = v_shop;
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    select count(*) into v_int from public.shops where id = v_shop;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (7, 'Owner can still see own pending shop',
    case when v_msg is not null then 'ERROR' when v_int = 1 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('saw %s row(s)', v_int)));

  v_msg := null; v_int := null;
  begin
    perform set_config('request.jwt.claims', '{"role":"anon"}', true);
    execute 'set local role anon';
    select count(*) into v_int from public.shops where status::text = 'approved';
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (8, 'Approved shops still visible to everyone',
    case when v_msg is not null then 'ERROR' when v_int > 0 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('signed-out visitor sees %s approved shop(s)', v_int)));

  ---------------------------------------------------------------------------
  -- SEC-4 / SEC-10  The order RPC ignores a forged status and identity.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null; v_txt2 := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    v_oid := 'SECTEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(
      jsonb_build_object('id', v_oid, 'shop_id', v_shop, 'status', 'Completed',
                         'customer_email', 'fake@evil.test', 'customer_name', 'Fake Name',
                         'order_date', 'forged', 'qr_code_payload', 'forged'),
      v_items);
    select o.status, o.customer_email into v_txt, v_txt2 from public.orders o where o.id = v_oid;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (9, 'SEC-4/10 Order RPC sets status and email itself (and still works)',
    case when v_msg is not null then 'FAIL'
         when v_txt = 'Order Placed' and v_txt2 = v_cust_email then 'PASS'
         else 'FAIL' end,
    coalesce(v_msg, format('status=%s email=%s (expected Order Placed / %s)', v_txt, v_txt2, v_cust_email)));

  ---------------------------------------------------------------------------
  -- SEC-4  No direct INSERT into orders.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    insert into public.orders (
      id, customer_name, customer_email, customer_mobile, total_amount, status,
      order_date, qr_code_payload, items_json, user_id, shop_id)
    values (
      'SECTEST-' || gen_random_uuid()::text, 'x', v_cust_email, '0', 1, 'Completed',
      'x', '', '[]'::jsonb, v_customer, v_shop)
    returning id into v_txt;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (10, 'SEC-4 Customer cannot insert an order directly',
    case when v_txt is not null then 'FAIL'
         when v_msg ilike '%row-level security%' then 'PASS'
         else 'CHECK' end,
    coalesce(v_msg, 'order was inserted'));

  ---------------------------------------------------------------------------
  -- SEC-5  No extra items on an existing order.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    v_oid := 'SECTEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
    insert into public.order_items (
      order_id, product_id, product_name, brand, image_url,
      weight_label, unit_price, quantity, line_total)
    values (v_oid, v_product, 'Free extra', '', '', v_weight, 0, 99, 0)
    returning order_id into v_txt;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (11, 'SEC-5 Customer cannot add items to an existing order',
    case when v_txt is not null then 'FAIL'
         when v_msg ilike '%row-level security%' then 'PASS'
         else 'CHECK' end,
    coalesce(v_msg, 'extra item was inserted'));

  ---------------------------------------------------------------------------
  -- SEC-7  Cancel cannot also change the bill.
  ---------------------------------------------------------------------------
  v_msg := null; v_int := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    v_oid := 'SECTEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
    update public.orders set status = 'Cancelled', total_amount = 1
     where id = v_oid
     returning total_amount into v_int;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (12, 'SEC-7 Customer cancel cannot change the total',
    case when v_int = 1 then 'FAIL' else 'PASS' end,
    coalesce(v_msg, 'total after attempt: ' || coalesce(v_int::text, '(update refused)')));

  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    v_oid := 'SECTEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
    update public.orders set status = 'Cancelled' where id = v_oid returning status into v_txt;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (13, 'Customer can still cancel a new order',
    case when v_txt = 'Cancelled' then 'PASS' else 'FAIL' end,
    coalesce(v_msg, 'status after cancel: ' || coalesce(v_txt, '(update refused)')));

  ---------------------------------------------------------------------------
  -- Vendor order handling: status still works, money does not move.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    v_oid := 'SECTEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    update public.orders set status = 'Order Confirmed' where id = v_oid returning status into v_txt;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (14, 'Vendor can still confirm an order',
    case when v_txt = 'Order Confirmed' then 'PASS' else 'FAIL' end,
    coalesce(v_msg, 'status after confirm: ' || coalesce(v_txt, '(update refused)')));

  v_msg := null; v_int := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    v_oid := 'SECTEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    update public.orders set status = 'Order Confirmed', total_amount = 99999
     where id = v_oid
     returning total_amount into v_int;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (15, 'SEC-7 Vendor cannot change an order total',
    case when v_int = 99999 then 'FAIL' else 'PASS' end,
    coalesce(v_msg, 'total after attempt: ' || coalesce(v_int::text, '(update refused)')));

  ---------------------------------------------------------------------------
  -- SEC-13  A blocked customer cannot order.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null;
  begin
    update public.profiles set is_blocked = true where id = v_customer;
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    v_oid := 'SECTEST-' || gen_random_uuid()::text;
    perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
    v_txt := v_oid;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (16, 'SEC-13 Blocked customer cannot order',
    case when v_txt is not null then 'FAIL'
         when v_msg ilike '%blocked%' then 'PASS'
         else 'CHECK' end,
    coalesce(v_msg, 'order was created'));

  ---------------------------------------------------------------------------
  -- SEC-2  A shop owner cannot rate their own shop.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null;
  begin
    v_oid := 'SECTEST-' || gen_random_uuid()::text;
    insert into public.orders (
      id, customer_name, customer_email, customer_mobile, total_amount, status,
      order_date, qr_code_payload, items_json, user_id, shop_id)
    values (v_oid, 'x', 'x', '0', 0, 'Completed', 'x', '', '[]'::jsonb, v_owner, v_shop);
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    insert into public.shop_ratings (shop_id, order_id, customer_id, rating, review)
    values (v_shop, v_oid, v_owner, 5, 'security test')
    returning id::text into v_txt;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (17, 'SEC-2 Shop owner cannot rate own shop',
    case when v_txt is not null then 'FAIL'
         when v_msg ilike '%row-level security%' then 'PASS'
         else 'CHECK' end,
    coalesce(v_msg, 'rating was inserted'));

  ---------------------------------------------------------------------------
  -- Regression: a real customer can still rate, and the average still updates.
  ---------------------------------------------------------------------------
  v_msg := null; v_int := null; v_before := null;
  begin
    v_oid := 'SECTEST-' || gen_random_uuid()::text;
    insert into public.orders (
      id, customer_name, customer_email, customer_mobile, total_amount, status,
      order_date, qr_code_payload, items_json, user_id, shop_id)
    values (v_oid, 'x', v_cust_email, '0', 0, 'Completed', 'x', '', '[]'::jsonb, v_customer, v_shop);
    select rating_count into v_before from public.shops where id = v_shop;
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    insert into public.shop_ratings (shop_id, order_id, customer_id, rating, review)
    values (v_shop, v_oid, v_customer, 4, 'security test');
    select rating_count into v_int from public.shops where id = v_shop;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (18, 'Customer can still rate; shop average updates',
    case when v_msg is not null then 'FAIL' when v_int = v_before + 1 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('rating_count %s -> %s', v_before, v_int)));

  ---------------------------------------------------------------------------
  -- SEC-8  Nobody can overwrite another account's shop photo.
  ---------------------------------------------------------------------------
  select count(*) into v_before
    from storage.objects
   where bucket_id = 'shop-images' and owner is distinct from v_customer;

  if v_before = 0 then
    insert into public.breakq_sec_results values (19, 'SEC-8 Customer cannot modify another shop''s photo', 'SKIP',
      'No shop photos uploaded by other accounts.');
  else
    v_msg := null; v_int := null;
    begin
      perform set_config('request.jwt.claims',
        json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
      execute 'set local role authenticated';
      with u as (
        update storage.objects set metadata = metadata
         where bucket_id = 'shop-images' and owner is distinct from v_customer
        returning 1
      )
      select count(*) into v_int from u;
      raise exception using errcode = 'ZZ001', message = 'rollback';
    exception
      when sqlstate 'ZZ001' then null;
      when others then v_msg := sqlerrm;
    end;
    insert into public.breakq_sec_results values (19, 'SEC-8 Customer cannot modify another shop''s photo',
      case when v_msg is not null then 'CHECK' when v_int = 0 then 'PASS' else 'FAIL' end,
      coalesce(v_msg, format('%s of %s photo(s) were writable', v_int, v_before)));
  end if;

  ---------------------------------------------------------------------------
  -- P18  Server-owned profile columns are not writable by the app.
  ---------------------------------------------------------------------------
  select string_agg(c, ', ') into v_txt
    from unnest(array['role', 'shop_id', 'wallet_balance', 'loyalty_points', 'is_blocked']) as c
   where exists (select 1 from information_schema.columns
                  where table_schema = 'public' and table_name = 'profiles' and column_name = c)
     and has_column_privilege('authenticated', 'public.profiles', c, 'UPDATE');
  insert into public.breakq_sec_results values (20, 'P18 App cannot write role/shop_id/wallet/loyalty/is_blocked',
    case when v_txt is null then 'PASS' else 'FAIL' end,
    coalesce('writable by authenticated: ' || v_txt, 'none of them are writable'));

  ---------------------------------------------------------------------------
  -- P17  notifications / device_tokens belong to their owner.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    insert into public.notifications (user_id, title, message, is_read)
    values (v_owner, 'security test', 'forged', false)
    returning user_id::text into v_txt;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (21, 'P17 Customer cannot send a notification to someone else',
    case when v_txt is not null then 'FAIL'
         when v_msg ilike '%row-level security%' then 'PASS'
         else 'CHECK' end,
    coalesce(v_msg, 'notification was inserted for another user'));

  v_msg := null; v_int := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    select count(*) into v_int from public.notifications where user_id is distinct from v_customer;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (22, 'P17 Customer cannot read other people''s notifications',
    case when v_msg is not null then 'ERROR' when v_int = 0 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('saw %s notification(s) of other users', v_int)));

  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    insert into public.device_tokens (user_id, token, platform)
    values (v_owner, 'sectest-' || gen_random_uuid()::text, 'android')
    returning user_id::text into v_txt;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (23, 'P17 Customer cannot register a push token for someone else',
    case when v_txt is not null then 'FAIL'
         when v_msg ilike '%row-level security%' then 'PASS'
         else 'CHECK' end,
    coalesce(v_msg, 'token row was inserted for another user'));

  v_msg := null; v_int := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    with u as (
      update public.device_tokens set user_id = v_customer
       where user_id is distinct from v_customer
      returning 1
    )
    select count(*) into v_int from u;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_sec_results values (24, 'P17 Customer cannot take over other people''s push tokens',
    case when v_msg is not null then 'CHECK' when v_int = 0 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('%s token row(s) were re-pointed', v_int)));

  ---------------------------------------------------------------------------
  -- INFO — live configuration the repository cannot show.
  ---------------------------------------------------------------------------
  select string_agg(c.relname || '=' || case when c.relrowsecurity then 'on' else 'OFF' end, ', ' order by c.relname)
    into v_txt
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public'
     and c.relkind = 'r'
     and c.relname in ('shops', 'products', 'orders', 'order_items', 'profiles', 'shop_ratings',
                       'notifications', 'device_tokens', 'promo_codes', 'categories',
                       'customer_addresses', 'wishlists', 'subscription_payments',
                       'vendor_subscriptions', 'shop_view_events', 'app_settings');
  insert into public.breakq_sec_results values (25, 'P17 Row-level security switched on',
    case when v_txt like '%OFF%' then 'FAIL' else 'PASS' end, v_txt);

  select string_agg(format('%s (%s)', policyname, cmd), ', ') into v_txt
    from pg_policies
   where schemaname = 'storage' and tablename = 'objects'
     and (coalesce(qual, '') like '%product-images%' or coalesce(with_check, '') like '%product-images%');
  select string_agg(format('%s public=%s', id, public), ', ') into v_txt2
    from storage.buckets where id = 'product-images';
  insert into public.breakq_sec_results values (26, 'P17 product-images bucket rules', 'INFO',
    coalesce(v_txt2, 'bucket not found') || ' | ' || coalesce(v_txt, 'no policies mention product-images'));

  select string_agg(t.tgname::text, ' > ' order by t.tgname) into v_txt
    from pg_trigger t
   where t.tgrelid = 'public.orders'::regclass
     and not t.tgisinternal
     and (t.tgtype::int & 2) = 2      -- BEFORE
     and (t.tgtype::int & 16) = 16;   -- UPDATE
  insert into public.breakq_sec_results values (27, 'Order guard fires first among BEFORE UPDATE triggers',
    case when v_txt like 'a_guard_order_client_writes%' then 'PASS' else 'CHECK' end, v_txt);

  begin
    execute 'select string_agg(jobname || '' ('' || schedule || '')'', '', '') from cron.job' into v_txt;
  exception when others then v_txt := 'pg_cron not readable: ' || sqlerrm;
  end;
  insert into public.breakq_sec_results values (28, 'Scheduled jobs (is vendor subscription expiry automated?)', 'INFO',
    coalesce(v_txt, 'no jobs'));
end $$;


-- -----------------------------------------------------------------------------
-- Results.
-- -----------------------------------------------------------------------------
select seq, test, result, detail from public.breakq_sec_results order by seq;
