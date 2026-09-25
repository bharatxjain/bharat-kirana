-- =============================================================================
--  BreakQ — Can someone read or change what they shouldn't?
--
--  RUN THE WHOLE FILE AT ONCE. Nothing is saved: every test runs inside a
--  savepoint that is rolled back, so no shop, product or status is changed,
--  and no webhook or email is sent (pg_net only sends after a commit).
--
--  Each test impersonates a real user (vendor, customer, or signed-out) and
--  attempts something the app never lets them do, straight against the
--  database — exactly what a modified APK or curl could do.
--
--    PASS = the database refused.   FAIL = the hole exists and needs fixing.
--
--  Tidy up afterwards:  drop table public.breakq_rls_results;
-- =============================================================================

drop table if exists public.breakq_rls_results;
create table public.breakq_rls_results (seq int, test text, result text, detail text);

do $$
declare
  v_shop     text;
  v_owner    uuid;
  v_product  text;
  v_customer uuid;
  v_txt      text;
  v_int      int;
  v_bool     boolean;
  v_msg      text;
begin
  select s.id, s.owner_id into v_shop, v_owner
    from public.shops s
   where s.owner_id is not null
     and exists (select 1 from public.products p where p.shop_id = s.id)
   limit 1;

  select p.id into v_product from public.products p where p.shop_id = v_shop limit 1;

  select pr.id into v_customer
    from public.profiles pr
   where pr.role = 'customer' and pr.id is distinct from v_owner
   limit 1;

  if v_shop is null or v_product is null or v_customer is null then
    insert into public.breakq_rls_results values
      (0, 'Fixtures', 'BLOCKED', 'Need a vendor shop with a product, plus a separate customer.');
    return;
  end if;

  insert into public.breakq_rls_results values
    (0, 'Fixtures', 'INFO',
     format('shop=%s owner=%s product=%s customer=%s', v_shop, v_owner, v_product, v_customer));

  ---------------------------------------------------------------------------
  -- 1. A vendor whose shop is pending cannot approve it themselves.
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
  insert into public.breakq_rls_results values (
    1, 'Vendor cannot approve their own shop',
    case when v_txt = 'approved' then 'FAIL' else 'PASS' end,
    coalesce(v_msg, case when v_txt is null then 'update refused'
                         else 'status after attempt: ' || v_txt end));

  ---------------------------------------------------------------------------
  -- 2. A vendor cannot give their own shop a fake rating.
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
  insert into public.breakq_rls_results values (
    2, 'Vendor cannot fake their own rating',
    case when v_int = 9999 then 'FAIL' else 'PASS' end,
    coalesce(v_msg, case when v_int is null then 'update refused'
                         else 'rating_count after attempt: ' || v_int end));

  ---------------------------------------------------------------------------
  -- 3. A vendor cannot lift an admin's restriction on their own product.
  ---------------------------------------------------------------------------
  v_msg := null; v_bool := null;
  begin
    update public.products set is_restricted = true where id = v_product;
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    update public.products set is_restricted = false where id = v_product
      returning is_restricted into v_bool;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_rls_results values (
    3, 'Vendor cannot lift an admin restriction on their product',
    case when v_bool = false then 'FAIL' else 'PASS' end,
    coalesce(v_msg, case when v_bool is null then 'update refused'
                         else 'is_restricted after attempt: ' || v_bool end));

  ---------------------------------------------------------------------------
  -- 4. Signed-out visitors cannot see a shop that isn't approved (its owner
  --    phone, UPI id, proof document and rejection reason live on that row).
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
  insert into public.breakq_rls_results values (
    4, 'Signed-out visitors cannot see non-approved shops',
    case when v_msg is not null then 'ERROR' when v_int > 0 then 'FAIL' else 'PASS' end,
    coalesce(v_msg, format('signed-out visitor saw %s row(s) of a rejected shop', v_int)));

  ---------------------------------------------------------------------------
  -- 5. Signed-out visitors cannot see a product an admin has restricted.
  ---------------------------------------------------------------------------
  v_msg := null; v_int := null;
  begin
    update public.products set is_restricted = true where id = v_product;
    perform set_config('request.jwt.claims', '{"role":"anon"}', true);
    execute 'set local role anon';
    select count(*) into v_int from public.products where id = v_product;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_rls_results values (
    5, 'Signed-out visitors cannot see restricted products',
    case when v_msg is not null then 'ERROR' when v_int > 0 then 'FAIL' else 'PASS' end,
    coalesce(v_msg, format('signed-out visitor saw %s restricted product(s)', v_int)));

  ---------------------------------------------------------------------------
  -- 6. A customer cannot edit a shop they don't own.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    update public.shops set name = name where id = v_shop returning id into v_txt;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_rls_results values (
    6, 'Customer cannot edit a shop they don''t own',
    case when v_txt is not null then 'FAIL' else 'PASS' end,
    coalesce(v_msg, case when v_txt is null then 'update refused' else 'customer updated the shop' end));

  ---------------------------------------------------------------------------
  -- 7. A customer cannot change a shop's prices.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    execute 'set local role authenticated';
    update public.products set current_price = current_price where id = v_product
      returning id into v_txt;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_rls_results values (
    7, 'Customer cannot edit a shop''s products',
    case when v_txt is not null then 'FAIL' else 'PASS' end,
    coalesce(v_msg, case when v_txt is null then 'update refused' else 'customer updated the product' end));

  ---------------------------------------------------------------------------
  -- 8. Someone who isn't signed in cannot create a shop.
  ---------------------------------------------------------------------------
  v_msg := null; v_txt := null;
  begin
    perform set_config('request.jwt.claims', '{"role":"anon"}', true);
    execute 'set local role anon';
    insert into public.shops (id, name, address)
    values ('RLSTEST-' || gen_random_uuid()::text, 'RLS test', 'nowhere')
    returning id into v_txt;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;
  insert into public.breakq_rls_results values (
    8, 'Signed-out visitor cannot create a shop',
    case when v_txt is not null then 'FAIL' else 'PASS' end,
    coalesce(v_msg, case when v_txt is null then 'insert refused' else 'a shop was created with no owner' end));
end $$;


-- -----------------------------------------------------------------------------
-- Results. PASS = protected. FAIL = hole that needs a fix.
-- -----------------------------------------------------------------------------
select seq, test, result, detail from public.breakq_rls_results order by seq;


-- =============================================================================
-- OPTIONAL — run this on its own afterwards. It shows the parts of each rule the
-- earlier query didn't include (restrictive vs permissive, and the insert/update
-- checks), plus every trigger that might protect these tables.
-- =============================================================================
-- select tablename, policyname, permissive, cmd, roles::text, with_check
--   from pg_policies
--  where schemaname = 'public' and tablename in ('products', 'shops')
--  order by tablename, cmd;
--
-- select event_object_table as tbl, trigger_name, action_timing,
--        event_manipulation as event, action_statement
--   from information_schema.triggers
--  where event_object_schema = 'public' and event_object_table in ('products', 'shops')
--  order by tbl, trigger_name;
