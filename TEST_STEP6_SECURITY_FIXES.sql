-- =============================================================================
--  BreakQ — Tests for STEP6_SECURITY_FIXES.sql + Step 6 live checks
--
--  RUN THE WHOLE FILE AT ONCE, after STEP6_SECURITY_FIXES.sql.
--  Every test runs inside a savepoint that is rolled back: no order, profile,
--  push token, shop or stock is changed and no push is sent.
--
--  PASS = works as intended.  FAIL = broken.  SKIP = no suitable test data.
--  INFO = facts for the Step 6 report, not pass/fail.
--
--  Tidy up afterwards:  drop table public.breakq_step6_results;
-- =============================================================================

drop table if exists public.breakq_step6_results;
create table public.breakq_step6_results (seq int, test text, result text, detail text);

do $$
declare
  v_shop     text;
  v_owner    uuid;
  v_product  text;
  v_weight   text;
  v_owner2   uuid;
  v_cust     uuid;
  v_cust2    uuid;
  v_items    jsonb;
  v_oid      text;
  v_tok      text;
  v_txt      text;
  v_msg      text;
  v_n        int;
  v_m        int;
  v_bad      text;
  v_fcm1     text;
  v_fcm2     text;
begin
  ---------------------------------------------------------------------------
  -- Fixtures
  ---------------------------------------------------------------------------
  select p.shop_id, s.owner_id, p.id, coalesce(p.weight_options->0->>'label', p.unit)
    into v_shop, v_owner, v_product, v_weight
    from public.products p
    join public.shops s on s.id = p.shop_id
   where s.status::text = 'approved'
     and not coalesce(s.is_deleted, false)
     and coalesce(s.accepting_orders, true)
     and s.owner_id is not null
     and coalesce(p.in_stock, true)
     and coalesce(p.is_active, true)
     and not coalesce(p.is_restricted, false)
     and (p.stock_qty is null or p.stock_qty >= 2)
   limit 1;

  select s.owner_id into v_owner2
    from public.shops s
   where s.owner_id is not null and s.owner_id is distinct from v_owner
     and not coalesce(s.is_deleted, false)
   limit 1;

  select pr.id into v_cust
    from public.profiles pr join auth.users u on u.id = pr.id
   where pr.role = 'customer' and not coalesce(pr.is_blocked, false)
     and pr.id is distinct from v_owner and pr.id is distinct from v_owner2
   limit 1;

  select pr.id into v_cust2
    from public.profiles pr join auth.users u on u.id = pr.id
   where pr.id is distinct from v_cust and pr.id is distinct from v_owner
   limit 1;

  if v_product is null or v_cust is null or v_cust2 is null then
    insert into public.breakq_step6_results values (0, 'Fixtures', 'BLOCKED',
      'Need an approved open shop with an in-stock product, a customer and one more account.');
    return;
  end if;

  insert into public.breakq_step6_results values (0, 'Fixtures', 'INFO',
    format('shop=%s product=%s | other shop owner=%s | customer=%s | other account=%s',
           v_shop, v_product, coalesce(v_owner2::text, 'none'), v_cust, v_cust2));

  v_items := jsonb_build_array(jsonb_build_object('product_id', v_product, 'weight_label', v_weight, 'quantity', 1));

  ---------------------------------------------------------------------------
  -- 1-4. R13 pickup completion
  ---------------------------------------------------------------------------
  -- 1. Signed-out caller with a valid pickup code.
  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', v_cust, 'role', 'authenticated')::text, true);
    v_oid := 'S6TEST-' || gen_random_uuid()::text;
    select r.pickup_token into v_tok
      from public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items) r;
    update public.orders set status = 'Order Confirmed'  where id = v_oid;
    update public.orders set status = 'Preparing'        where id = v_oid;
    update public.orders set status = 'Ready for Pickup' where id = v_oid;
    perform set_config('request.jwt.claims', '', true);
    execute 'set local role anon';
    begin
      select c.status into v_txt from public.complete_order_by_pickup_token(v_tok) c;
    exception when others then
      v_msg := sqlerrm;
    end;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := coalesce(v_msg, 'setup: ' || sqlerrm);
  end;
  insert into public.breakq_step6_results values (1, 'R13 Signed-out caller can''t complete an order with its pickup code',
    case when v_txt = 'Completed' then 'FAIL'
         when v_msg ilike 'setup:%' then 'ERROR'
         when v_msg is not null then 'PASS'
         else 'FAIL' end,
    coalesce(v_msg, 'status=' || coalesce(v_txt, '-')));

  -- 2. The customer can't complete their own order with their own code.
  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', v_cust, 'role', 'authenticated')::text, true);
    v_oid := 'S6TEST-' || gen_random_uuid()::text;
    select r.pickup_token into v_tok
      from public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items) r;
    update public.orders set status = 'Order Confirmed'  where id = v_oid;
    update public.orders set status = 'Preparing'        where id = v_oid;
    update public.orders set status = 'Ready for Pickup' where id = v_oid;
    execute 'set local role authenticated';
    begin
      select c.status into v_txt from public.complete_order_by_pickup_token(v_tok) c;
    exception when others then
      v_msg := sqlerrm;
    end;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := coalesce(v_msg, 'setup: ' || sqlerrm);
  end;
  insert into public.breakq_step6_results values (2, 'R13 Customer can''t mark their own order picked up',
    case when v_txt = 'Completed' then 'FAIL'
         when v_msg ilike '%NOT_YOUR_SHOP%' then 'PASS'
         when v_msg ilike 'setup:%' then 'ERROR'
         else 'FAIL' end,
    coalesce(v_msg, 'status=' || coalesce(v_txt, '-')));

  -- 3+4. The shop owner still completes it, and the code works only once.
  v_msg := null; v_txt := null; v_bad := null;
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', v_cust, 'role', 'authenticated')::text, true);
    v_oid := 'S6TEST-' || gen_random_uuid()::text;
    select r.pickup_token into v_tok
      from public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items) r;
    update public.orders set status = 'Order Confirmed'  where id = v_oid;
    update public.orders set status = 'Preparing'        where id = v_oid;
    update public.orders set status = 'Ready for Pickup' where id = v_oid;
    perform set_config('request.jwt.claims', json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    select c.status into v_txt from public.complete_order_by_pickup_token(v_tok) c;
    begin
      perform public.complete_order_by_pickup_token(v_tok);
      v_bad := 'second scan accepted';
    exception when others then
      v_bad := sqlerrm;
    end;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step6_results values (3, 'R13 Shop owner still completes a Ready order with the code',
    case when v_msg is not null then 'ERROR' when v_txt = 'Completed' then 'PASS' else 'FAIL' end,
    coalesce(v_msg, 'status=' || coalesce(v_txt, '-')));
  insert into public.breakq_step6_results values (4, 'R13 A pickup code works only once',
    case when v_msg is not null then 'ERROR' when v_bad ilike '%ALREADY_COMPLETED%' then 'PASS' else 'FAIL' end,
    coalesce(v_msg, v_bad, '-'));

  -- 5. Another shop's owner can't use this shop's code.
  if v_owner2 is null then
    insert into public.breakq_step6_results values (5, 'R13 Another shop can''t complete this shop''s order', 'SKIP',
      'Only one shop owner exists.');
  else
    v_msg := null; v_txt := null;
    begin
      perform set_config('request.jwt.claims', json_build_object('sub', v_cust, 'role', 'authenticated')::text, true);
      v_oid := 'S6TEST-' || gen_random_uuid()::text;
      select r.pickup_token into v_tok
        from public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items) r;
      update public.orders set status = 'Order Confirmed'  where id = v_oid;
      update public.orders set status = 'Preparing'        where id = v_oid;
      update public.orders set status = 'Ready for Pickup' where id = v_oid;
      perform set_config('request.jwt.claims', json_build_object('sub', v_owner2, 'role', 'authenticated')::text, true);
      execute 'set local role authenticated';
      begin
        select c.status into v_txt from public.complete_order_by_pickup_token(v_tok) c;
      exception when others then
        v_msg := sqlerrm;
      end;
      raise exception using errcode = 'ZZ001', message = 'rollback';
    exception
      when sqlstate 'ZZ001' then null;
      when others then v_msg := coalesce(v_msg, 'setup: ' || sqlerrm);
    end;
    insert into public.breakq_step6_results values (5, 'R13 Another shop can''t complete this shop''s order',
      case when v_txt = 'Completed' then 'FAIL'
           when v_msg ilike '%NOT_YOUR_SHOP%' then 'PASS'
           when v_msg ilike 'setup:%' then 'ERROR'
           else 'FAIL' end,
      coalesce(v_msg, 'status=' || coalesce(v_txt, '-')));
  end if;

  ---------------------------------------------------------------------------
  -- 6-7. R18 profile INSERT columns
  ---------------------------------------------------------------------------
  select string_agg(c, ', ') into v_bad
    from unnest(array['role', 'shop_id', 'is_blocked', 'wallet_balance', 'loyalty_points']) c
   where exists (select 1 from information_schema.columns
                  where table_schema = 'public' and table_name = 'profiles' and column_name = c)
     and has_column_privilege('authenticated', 'public.profiles', c, 'INSERT');
  insert into public.breakq_step6_results values (6, 'R18 App can''t insert role / shop_id / is_blocked / wallet / loyalty',
    case when v_bad is null then 'PASS' else 'FAIL' end,
    coalesce('still insertable: ' || v_bad, 'none of them are insertable'));

  -- 7. The app's profile save (an upsert) still works.
  v_msg := null; v_n := null;
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', v_cust, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    insert into public.profiles as p (id, email, full_name, mobile_number, address,
                                      profile_completed, phone_verified, auth_provider, fcm_token)
    select pr.id, pr.email, pr.full_name, pr.mobile_number, pr.address,
           pr.profile_completed, pr.phone_verified, pr.auth_provider, pr.fcm_token
      from public.profiles pr where pr.id = v_cust
    on conflict (id) do update
       set email = excluded.email, full_name = excluded.full_name,
           mobile_number = excluded.mobile_number, address = excluded.address,
           profile_completed = excluded.profile_completed, phone_verified = excluded.phone_verified,
           auth_provider = excluded.auth_provider, fcm_token = excluded.fcm_token;
    get diagnostics v_n = row_count;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step6_results values (7, 'R18 App profile save still works',
    case when v_msg is not null then 'ERROR' when v_n = 1 then 'PASS' else 'FAIL' end,
    coalesce(v_msg, format('%s row(s) saved', v_n)));

  ---------------------------------------------------------------------------
  -- 8-9. R2 push token follows the newest sign-in
  ---------------------------------------------------------------------------
  v_msg := null; v_n := null; v_m := null; v_fcm1 := null; v_fcm2 := null;
  v_tok := 'S6TESTTOKEN-' || replace(gen_random_uuid()::text, '-', '');
  begin
    execute 'set local role authenticated';
    perform set_config('request.jwt.claims', json_build_object('sub', v_cust, 'role', 'authenticated')::text, true);
    perform public.register_device_token(v_tok, 'android');
    perform set_config('request.jwt.claims', json_build_object('sub', v_cust2, 'role', 'authenticated')::text, true);
    perform public.register_device_token(v_tok, 'android');
    execute 'reset role';
    select count(*) into v_n from public.device_tokens where token = v_tok and user_id = v_cust;
    select count(*) into v_m from public.device_tokens where token = v_tok and user_id = v_cust2;
    select fcm_token into v_fcm1 from public.profiles where id = v_cust;
    select fcm_token into v_fcm2 from public.profiles where id = v_cust2;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_step6_results values (8, 'R2 Signing in on a phone takes its push token from the previous account',
    case when v_msg is not null then 'ERROR'
         when v_n = 0 and v_m = 1 and v_fcm1 is distinct from v_tok and v_fcm2 = v_tok then 'PASS'
         else 'FAIL' end,
    coalesce(v_msg, format('previous account rows=%s, new account rows=%s, previous profile token cleared=%s, new profile token set=%s',
                           v_n, v_m, v_fcm1 is distinct from v_tok, v_fcm2 = v_tok)));

  v_msg := null;
  begin
    perform set_config('request.jwt.claims', '', true);
    execute 'set local role anon';
    begin
      perform public.register_device_token('S6TESTTOKEN-' || replace(gen_random_uuid()::text, '-', ''), 'android');
    exception when others then
      v_msg := sqlerrm;
    end;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := coalesce(v_msg, 'setup: ' || sqlerrm);
  end;
  insert into public.breakq_step6_results values (9, 'R2 Signed-out caller can''t register a push token',
    case when v_msg is not null and v_msg not ilike 'setup:%' then 'PASS' else 'FAIL' end,
    coalesce(v_msg, 'accepted'));

  ---------------------------------------------------------------------------
  -- 10-11. R8 cross-shop access
  ---------------------------------------------------------------------------
  if v_owner2 is null then
    insert into public.breakq_step6_results values (10, 'R8 Another vendor can''t edit this shop', 'SKIP', 'Only one shop owner exists.');
    insert into public.breakq_step6_results values (11, 'R8 Another vendor can''t read this shop''s order lines', 'SKIP', 'Only one shop owner exists.');
  else
    v_msg := null; v_n := null;
    begin
      perform set_config('request.jwt.claims', json_build_object('sub', v_owner2, 'role', 'authenticated')::text, true);
      execute 'set local role authenticated';
      begin
        update public.shops set name = name where id = v_shop;
        get diagnostics v_n = row_count;
      exception when others then
        v_msg := sqlerrm; v_n := 0;
      end;
      raise exception using errcode = 'ZZ001', message = 'rollback';
    exception
      when sqlstate 'ZZ001' then null;
      when others then v_msg := coalesce(v_msg, 'setup: ' || sqlerrm);
    end;
    insert into public.breakq_step6_results values (10, 'R8 Another vendor can''t edit this shop',
      case when v_msg ilike 'setup:%' then 'ERROR' when v_n = 0 then 'PASS' else 'FAIL' end,
      coalesce(v_msg, format('changed %s row(s)', v_n)));

    v_msg := null; v_n := null; v_m := null;
    begin
      perform set_config('request.jwt.claims', json_build_object('sub', v_cust, 'role', 'authenticated')::text, true);
      v_oid := 'S6TEST-' || gen_random_uuid()::text;
      perform public.create_order_with_items(jsonb_build_object('id', v_oid, 'shop_id', v_shop), v_items);
      perform set_config('request.jwt.claims', json_build_object('sub', v_owner2, 'role', 'authenticated')::text, true);
      execute 'set local role authenticated';
      select count(*) into v_n from public.order_items where order_id = v_oid;
      select count(*) into v_m from public.orders where id = v_oid;
      raise exception using errcode = 'ZZ001', message = 'rollback';
    exception
      when sqlstate 'ZZ001' then null;
      when others then v_msg := sqlerrm;
    end;
    insert into public.breakq_step6_results values (11, 'R8 Another vendor can''t read this shop''s order or its lines',
      case when v_msg is not null then 'ERROR' when v_n = 0 and v_m = 0 then 'PASS' else 'FAIL' end,
      coalesce(v_msg, format('saw %s order(s), %s line(s)', v_m, v_n)));
  end if;

  ---------------------------------------------------------------------------
  -- 12+. INFO — live facts for risks that can't be settled from the code
  ---------------------------------------------------------------------------
  -- R18: accounts with no profile row (who could have used the old INSERT hole).
  select count(*) into v_n from auth.users u left join public.profiles p on p.id = u.id where p.id is null;
  insert into public.breakq_step6_results values (12, 'R18 Sign-in accounts without a profile row', 'INFO', v_n || ' account(s)');

  -- R18: any admin/super_admin profiles (check every one is expected).
  select string_agg(coalesce(email, id::text) || ' (' || role || ')', ', ') into v_txt
    from public.profiles where role in ('admin', 'super_admin');
  insert into public.breakq_step6_results values (13, 'R18 Admin accounts (check each is expected)', 'INFO', coalesce(v_txt, 'none'));

  -- R1: the same notification written more than once in the last 30 days.
  begin
    select count(*) into v_n from (
      select user_id, order_id, title
        from public.notifications
       where order_id is not null and created_at > now() - interval '30 days'
       group by 1, 2, 3 having count(*) > 1) d;
    v_txt := v_n || ' duplicate group(s)';
  exception when others then v_txt := 'could not check: ' || sqlerrm;
  end;
  insert into public.breakq_step6_results values (14, 'R1 Duplicate order notifications (last 30 days)', 'INFO', v_txt);

  -- R1: webhook triggers on orders (more than one would send every push twice).
  select string_agg(t.tgname::text || ' -> ' || p.proname::text, ', ') into v_txt
    from pg_trigger t join pg_proc p on p.oid = t.tgfoid
   where t.tgrelid = 'public.orders'::regclass and not t.tgisinternal
     and (p.proname = 'http_request' or pg_get_triggerdef(t.oid) ilike '%functions/v1%');
  insert into public.breakq_step6_results values (15, 'R1 Webhooks on orders', 'INFO', coalesce(v_txt, 'none found'));

  -- R4: one push token pointing at more than one account.
  select count(*) into v_n
    from public.device_tokens d join public.profiles p on p.fcm_token = d.token and p.id <> d.user_id;
  select count(*) into v_m from (
    select fcm_token from public.profiles where coalesce(fcm_token, '') <> '' group by 1 having count(*) > 1) d;
  insert into public.breakq_step6_results values (16, 'R2/R4 Push tokens shared by two accounts', 'INFO',
    format('device row vs other profile: %s | same token on several profiles: %s', v_n, v_m));

  -- R5: approved shops that are marked deleted (still listed on Home).
  select count(*) into v_n from public.shops where status::text = 'approved' and coalesce(is_deleted, false);
  insert into public.breakq_step6_results values (17, 'R5 Approved shops marked deleted', 'INFO', v_n || ' shop(s)');

  -- R17: tables streamed by Realtime.
  select string_agg(schemaname || '.' || tablename, ', ' order by tablename) into v_txt
    from pg_publication_tables where pubname = 'supabase_realtime';
  insert into public.breakq_step6_results values (18, 'R17 Tables in the Realtime publication', 'INFO', coalesce(v_txt, 'none'));

  -- R19: who may write notification campaigns.
  if to_regclass('public.notification_campaigns') is null then
    v_txt := 'table not found';
  else
    select format('row security on: %s | signed-in insert: %s, update: %s | anon insert: %s | rules: %s',
                  c.relrowsecurity,
                  has_table_privilege('authenticated', 'public.notification_campaigns', 'INSERT'),
                  has_table_privilege('authenticated', 'public.notification_campaigns', 'UPDATE'),
                  has_table_privilege('anon', 'public.notification_campaigns', 'INSERT'),
                  coalesce((select string_agg(pp.policyname || ' [' || pp.cmd || ', ' || array_to_string(pp.roles, ',') || '] '
                                              || coalesce(pp.with_check, pp.qual, ''), ' | ')
                              from pg_policies pp
                             where pp.schemaname = 'public' and pp.tablename = 'notification_campaigns'), 'none'))
      into v_txt
      from pg_class c where c.oid = 'public.notification_campaigns'::regclass;
  end if;
  insert into public.breakq_step6_results values (19, 'R19 notification_campaigns access', 'INFO', v_txt);
end $$;

select seq, test, result, detail from public.breakq_step6_results order by seq;
