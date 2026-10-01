-- =============================================================================
--  BreakQ — Tests for STEP5_NOTIFICATIONS_AND_PROMO_RLS.sql
--
--  RUN THE WHOLE FILE AT ONCE, after STEP5_NOTIFICATIONS_AND_PROMO_RLS.sql.
--  Every test runs inside a savepoint that is rolled back: no notification,
--  promo code, order or stock is changed and no push is sent.
--
--  PASS = works as intended.  FAIL = broken.  SKIP = no suitable test data.
--  INFO = facts to read, not pass/fail.
--
--  Tidy up afterwards:  drop table public.breakq_step5_results;
-- =============================================================================

drop table if exists public.breakq_step5_results;
create table public.breakq_step5_results (seq int, test text, result text, detail text);

do $$
declare
  v_cust     uuid;
  v_cust2    uuid;
  v_admin    uuid;
  v_shop     text;
  v_product  text;
  v_weight   text;
  v_code     text;
  v_oid      text;
  v_msg      text;
  v_reason   text;
  v_disc     int;
  v_ord_disc int;
  v_ord_code text;
  v_n        int;
begin
  ---------------------------------------------------------------------------
  -- Fixtures
  ---------------------------------------------------------------------------
  select pr.id into v_cust
    from public.profiles pr join auth.users u on u.id = pr.id
   where pr.role = 'customer' and not coalesce(pr.is_blocked, false)
   limit 1;

  select pr.id into v_cust2
    from public.profiles pr join auth.users u on u.id = pr.id
   where pr.id is distinct from v_cust
   limit 1;

  select pr.id into v_admin
    from public.profiles pr join auth.users u on u.id = pr.id
   where pr.role in ('admin', 'super_admin')
   limit 1;

  select p.shop_id, p.id, coalesce(p.weight_options->0->>'label', p.unit)
    into v_shop, v_product, v_weight
    from public.products p
    join public.shops s on s.id = p.shop_id
   where s.status::text = 'approved'
     and not coalesce(s.is_deleted, false)
     and coalesce(s.accepting_orders, true)
     and coalesce(p.in_stock, true)
     and coalesce(p.is_active, true)
     and not coalesce(p.is_restricted, false)
     and (p.stock_qty is null or p.stock_qty >= 1)
     and coalesce(p.current_price, 0) > 5
   limit 1;

  if v_cust is null or v_cust2 is null or v_product is null then
    insert into public.breakq_step5_results values (0, 'Fixtures', 'BLOCKED',
      'Need two accounts (one an unblocked customer) and an approved open shop with an in-stock product priced above ₹5.');
    return;
  end if;

  v_code := 'ZZS5' || upper(substr(md5(random()::text), 1, 8));

  insert into public.breakq_step5_results values (0, 'Fixtures', 'INFO',
    format('customer=%s other account=%s admin=%s shop=%s product=%s',
           v_cust, v_cust2, coalesce(v_admin::text, 'none'), v_shop, v_product));

  ---------------------------------------------------------------------------
  -- 1. "Clear all": a customer can delete their own notifications.
  ---------------------------------------------------------------------------
  v_msg := null; v_n := null;
  begin
    insert into public.notifications (user_id, title, message, is_read)
    values (v_cust, 'S5TEST', 'step 5 test', false),
           (v_cust, 'S5TEST', 'step 5 test', false);
    perform set_config('request.jwt.claims', json_build_object('sub', v_cust, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    delete from public.notifications where user_id = v_cust and title = 'S5TEST';
    get diagnostics v_n = row_count;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step5_results values (1, 'Customer can clear their own notifications',
    case when v_msg is not null then 'ERROR' when v_n = 2 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('deleted %s of 2', v_n)));

  ---------------------------------------------------------------------------
  -- 2. ...but not anyone else's.
  ---------------------------------------------------------------------------
  v_msg := null; v_n := null;
  begin
    insert into public.notifications (user_id, title, message, is_read)
    values (v_cust2, 'S5TEST', 'step 5 test', false);
    perform set_config('request.jwt.claims', json_build_object('sub', v_cust, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    delete from public.notifications where user_id = v_cust2;
    get diagnostics v_n = row_count;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step5_results values (2, 'Customer can''t delete another account''s notifications',
    case when v_msg is not null then 'ERROR' when v_n = 0 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('deleted %s row(s)', v_n)));

  ---------------------------------------------------------------------------
  -- 3. Marking as read still works.
  ---------------------------------------------------------------------------
  v_msg := null; v_n := null;
  begin
    insert into public.notifications (user_id, title, message, is_read)
    values (v_cust, 'S5TEST', 'step 5 test', false);
    perform set_config('request.jwt.claims', json_build_object('sub', v_cust, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    update public.notifications set is_read = true where user_id = v_cust and title = 'S5TEST';
    get diagnostics v_n = row_count;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step5_results values (3, 'Customer can still mark notifications as read',
    case when v_msg is not null then 'ERROR' when v_n = 1 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('%s row(s) updated', v_n)));

  ---------------------------------------------------------------------------
  -- 4. A signed-out visitor can't list promo codes.
  ---------------------------------------------------------------------------
  v_msg := null; v_n := null;
  begin
    insert into public.promo_codes (code, description, active, discount_flat_rupees, min_order_amount, valid_from)
    values (v_code, 'step 5 test', true, 5, 0, now() - interval '1 minute');
    perform set_config('request.jwt.claims', '', true);
    execute 'set local role anon';
    begin
      select count(*) into v_n from public.promo_codes where code = v_code;
    exception when insufficient_privilege then
      v_n := 0;
    end;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step5_results values (4, 'Signed-out visitor can''t list promo codes',
    case when v_msg is not null then 'ERROR' when v_n = 0 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('saw %s active test code(s)', v_n)));

  ---------------------------------------------------------------------------
  -- 5. A signed-in customer can't list promo codes either.
  ---------------------------------------------------------------------------
  v_msg := null; v_n := null;
  begin
    insert into public.promo_codes (code, description, active, discount_flat_rupees, min_order_amount, valid_from)
    values (v_code, 'step 5 test', true, 5, 0, now() - interval '1 minute');
    perform set_config('request.jwt.claims', json_build_object('sub', v_cust, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    begin
      select count(*) into v_n from public.promo_codes where code = v_code;
    exception when insufficient_privilege then
      v_n := 0;
    end;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step5_results values (5, 'Signed-in customer can''t list promo codes',
    case when v_msg is not null then 'ERROR' when v_n = 0 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('saw %s active test code(s)', v_n)));

  ---------------------------------------------------------------------------
  -- 6. Typing the code still works in the cart and at checkout.
  ---------------------------------------------------------------------------
  v_msg := null; v_reason := null; v_disc := null; v_ord_disc := null; v_ord_code := null;
  begin
    insert into public.promo_codes (code, description, active, discount_flat_rupees, min_order_amount, valid_from)
    values (v_code, 'step 5 test', true, 5, 0, now() - interval '1 minute');
    perform set_config('request.jwt.claims', json_build_object('sub', v_cust, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';

    select pp.reason, pp.discount into v_reason, v_disc
      from public.preview_promo(v_code, v_shop,
        jsonb_build_array(jsonb_build_object('product_id', v_product, 'weight_label', v_weight, 'quantity', 1))) pp;

    v_oid := 'S5TEST-' || gen_random_uuid()::text;
    select r.promo_discount, r.promo_code into v_ord_disc, v_ord_code
      from public.create_order_with_items(
        jsonb_build_object('id', v_oid, 'shop_id', v_shop, 'promo_code', v_code),
        jsonb_build_array(jsonb_build_object('product_id', v_product, 'weight_label', v_weight, 'quantity', 1))
      ) r;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step5_results values (6, 'Promo code still applies in the cart and at checkout',
    case when v_msg is not null then 'ERROR'
         when v_reason is null and v_disc > 0 and v_ord_disc = v_disc and v_ord_code = v_code then 'PASS'
         else 'FAIL' end,
    coalesce(v_msg, format('cart: reason=%s discount=%s | checkout: discount=%s code=%s',
                           coalesce(v_reason, 'OK'), coalesce(v_disc, 0),
                           coalesce(v_ord_disc, 0), coalesce(v_ord_code, '-'))));

  ---------------------------------------------------------------------------
  -- 7. Admins still see every code.
  ---------------------------------------------------------------------------
  if v_admin is null then
    insert into public.breakq_step5_results values (7, 'Admin can still list promo codes', 'SKIP',
      'No admin account found.');
  else
    v_msg := null; v_n := null;
    begin
      insert into public.promo_codes (code, description, active, discount_flat_rupees, min_order_amount, valid_from)
      values (v_code, 'step 5 test', true, 5, 0, now() - interval '1 minute');
      perform set_config('request.jwt.claims', json_build_object('sub', v_admin, 'role', 'authenticated')::text, true);
      execute 'set local role authenticated';
      select count(*) into v_n from public.promo_codes where code = v_code;
      raise exception using errcode = 'ZZ001', message = 'rollback';
    exception
      when sqlstate 'ZZ001' then null;
      when others then v_msg := sqlerrm;
    end;
    insert into public.breakq_step5_results values (7, 'Admin can still list promo codes',
      case when v_msg is not null then 'ERROR' when v_n = 1 then 'PASS' else 'FAIL' end,
      coalesce(v_msg, format('saw %s test code(s)', v_n)));
  end if;

  ---------------------------------------------------------------------------
  -- 8. INFO: rules now on the two tables.
  ---------------------------------------------------------------------------
  insert into public.breakq_step5_results
  select 8, 'Rules on notifications / promo_codes', 'INFO',
         string_agg(tablename || ': ' || policyname || ' (' || cmd || ')', ' | ' order by tablename, policyname)
    from pg_policies
   where schemaname = 'public' and tablename in ('notifications', 'promo_codes');
end $$;

select seq, test, result, detail from public.breakq_step5_results order by seq;
