-- =============================================================================
--  BreakQ — Step 6: security fixes found in the final 19-risk verification
--
--  Run the WHOLE file once, after STEP5_NOTIFICATIONS_AND_PROMO_RLS.sql.
--  Safe to re-run. No existing row is changed or deleted.
--
--   1. R13  complete_order_by_pickup_token: a SIGNED-OUT caller holding a
--           pickup code could complete the order. The owner check was
--           `owner_id <> auth.uid()`, which is NULL (= "not refused") when
--           auth.uid() is NULL, and the function was executable by anon.
--           Now: NULL-safe check, and only signed-in users may call it.
--           find_shop_order_by_number gets the same execute rule.
--   2. R18  profiles INSERT: the app could insert its own profile row with
--           ANY column, including role = 'admin' or another shop's shop_id,
--           whenever that user had no profile row (e.g. after an admin deleted
--           it). INSERT is now limited to the same columns the app may UPDATE.
--   3. R2   register_device_token(token): when someone signs in on a phone,
--           that phone's push token is taken away from whoever used the phone
--           before, so their order pushes stop arriving on it even if their
--           logout couldn't reach the server.
--
--  Replaces complete_order_by_pickup_token. Do NOT re-run the older
--  ORDER_PICKUP_MIGRATION.sql, ORDER_LIFECYCLE_SAFETY.sql or
--  PICKUP_RPC_AMBIGUITY_FIX.sql afterwards — they would bring the hole back.
--
--  Then run TEST_STEP6_SECURITY_FIXES.sql.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 0. Pre-checks — stop before changing anything if the live database differs
--    from what this file expects.
-- -----------------------------------------------------------------------------
do $$
declare
  v_missing text;
begin
  if to_regprocedure('public.complete_order_by_pickup_token(text)') is null
     or to_regprocedure('public.find_shop_order_by_number(integer)') is null then
    raise exception 'Pickup functions are missing. Nothing was changed — please share this message.';
  end if;

  if not exists (select 1 from information_schema.columns
                  where table_schema = 'public' and table_name = 'orders'
                    and column_name = 'pickup_token_consumed_at') then
    raise exception 'orders.pickup_token_consumed_at is missing. Nothing was changed — please share this message.';
  end if;

  select string_agg(c, ', ') into v_missing
    from unnest(array['id', 'email', 'full_name', 'mobile_number', 'address', 'profile_photo_url',
                      'phone_verified', 'auth_provider', 'profile_completed', 'fcm_token',
                      'last_lat', 'last_lng']) c
   where not exists (select 1 from information_schema.columns
                      where table_schema = 'public' and table_name = 'profiles' and column_name = c);
  if v_missing is not null then
    raise exception 'profiles is missing column(s): %. Nothing was changed — please share this message.', v_missing;
  end if;

  select string_agg(c, ', ') into v_missing
    from unnest(array['user_id', 'token', 'platform', 'last_seen_at']) c
   where not exists (select 1 from information_schema.columns
                      where table_schema = 'public' and table_name = 'device_tokens' and column_name = c);
  if v_missing is not null then
    raise exception 'device_tokens is missing column(s): %. Nothing was changed — please share this message.', v_missing;
  end if;

  -- The app already upserts with on_conflict=token, so this index should exist.
  if not exists (
    select 1
      from pg_index i
      join pg_attribute a on a.attrelid = i.indrelid and a.attnum = i.indkey[0]
     where i.indrelid = 'public.device_tokens'::regclass
       and i.indisunique and i.indnkeyatts = 1 and a.attname = 'token'
  ) then
    raise exception 'device_tokens.token has no unique index. Nothing was changed — please share this message.';
  end if;
end $$;


-- -----------------------------------------------------------------------------
-- 1. R13 — pickup completion only for the signed-in shop owner
--    Same behaviour as PICKUP_RPC_AMBIGUITY_FIX.sql except the owner check.
-- -----------------------------------------------------------------------------
create or replace function public.complete_order_by_pickup_token(p_token text)
returns table (
  order_id       text,
  order_number   integer,
  status         text,
  customer_name  text,
  total_amount   integer
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid     uuid := auth.uid();
  v         public.orders;
  s         public.shops;
  v_updated public.orders;
begin
  if v_uid is null then
    raise exception 'NOT_YOUR_SHOP' using errcode = 'P0002';
  end if;

  select * into v from public.orders o where o.pickup_token = p_token limit 1;
  if v.id is null then
    raise exception 'INVALID_TOKEN' using errcode = 'P0001';
  end if;

  select * into s from public.shops o where o.id = v.shop_id;
  if s.id is null or s.owner_id is distinct from v_uid then
    raise exception 'NOT_YOUR_SHOP' using errcode = 'P0002';
  end if;

  if v.status = 'Cancelled' then
    raise exception 'ORDER_CANCELLED' using errcode = 'P0003';
  end if;
  if v.status = 'Completed' then
    raise exception 'ALREADY_COMPLETED' using errcode = 'P0004';
  end if;

  update public.orders o
     set status                   = 'Completed',
         pickup_token_consumed_at = now()
   where o.id     = v.id
     and o.status = 'Ready for Pickup'
   returning * into v_updated;

  if v_updated.id is null then
    select * into v from public.orders o where o.id = v.id;
    if v.status = 'Cancelled' then
      raise exception 'ORDER_CANCELLED' using errcode = 'P0003';
    elsif v.status = 'Completed' then
      raise exception 'ALREADY_COMPLETED' using errcode = 'P0004';
    else
      raise exception 'NOT_READY_FOR_PICKUP' using errcode = 'P0005';
    end if;
  end if;

  return query select
    v_updated.id, v_updated.order_number, v_updated.status,
    v_updated.customer_name, v_updated.total_amount;
end;
$$;

revoke all on function public.complete_order_by_pickup_token(text) from public, anon;
grant execute on function public.complete_order_by_pickup_token(text) to authenticated;

revoke all on function public.find_shop_order_by_number(integer) from public, anon;
grant execute on function public.find_shop_order_by_number(integer) to authenticated;


-- -----------------------------------------------------------------------------
-- 2. R18 — the app may only INSERT the profile columns it may UPDATE
--    (same list as PROFILES_RLS_FIX.sql section 4). role, shop_id, is_blocked,
--    wallet_balance, loyalty_points and audit columns take their defaults.
--    The signup trigger and admin tools run as other roles and are unaffected.
-- -----------------------------------------------------------------------------
revoke insert on public.profiles from authenticated, anon;

grant insert (
  id,
  email,
  full_name,
  mobile_number,
  address,
  profile_photo_url,
  phone_verified,
  auth_provider,
  profile_completed,
  fcm_token,
  last_lat,
  last_lng
) on public.profiles to authenticated;


-- -----------------------------------------------------------------------------
-- 3. R2 — one phone, one account: the newest sign-in owns the push token
-- -----------------------------------------------------------------------------
create or replace function public.register_device_token(p_token text, p_platform text default 'android')
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid   uuid := auth.uid();
  v_token text := btrim(coalesce(p_token, ''));
begin
  if v_uid is null then
    raise exception 'Please sign in again.' using errcode = 'P0001';
  end if;
  if length(v_token) < 20 then
    raise exception 'Invalid push token.' using errcode = 'P0001';
  end if;

  delete from public.device_tokens where token = v_token and user_id is distinct from v_uid;
  update public.profiles set fcm_token = null where fcm_token = v_token and id <> v_uid;

  insert into public.device_tokens (user_id, token, platform, last_seen_at)
  values (v_uid, v_token, coalesce(nullif(btrim(p_platform), ''), 'android'), now())
  on conflict (token) do update
     set user_id      = excluded.user_id,
         platform     = excluded.platform,
         last_seen_at = excluded.last_seen_at;

  update public.profiles set fcm_token = v_token where id = v_uid;
end;
$$;

revoke all on function public.register_device_token(text, text) from public, anon;
grant execute on function public.register_device_token(text, text) to authenticated;


-- -----------------------------------------------------------------------------
-- Verify — who may run the three functions, and profile INSERT columns.
-- -----------------------------------------------------------------------------
select 'function' as kind, p.proname::text as name,
       format('anon can run: %s | signed-in can run: %s | security definer: %s',
              has_function_privilege('anon', p.oid, 'execute'),
              has_function_privilege('authenticated', p.oid, 'execute'),
              p.prosecdef) as detail
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in ('complete_order_by_pickup_token', 'find_shop_order_by_number', 'register_device_token')
union all
select 'profiles insert', c.column_name::text,
       format('signed-in can insert: %s',
              has_column_privilege('authenticated', 'public.profiles', c.column_name::text, 'INSERT'))
  from information_schema.columns c
 where c.table_schema = 'public' and c.table_name = 'profiles'
 order by 1, 2;
