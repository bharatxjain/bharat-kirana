-- ============================================================================
--  BreakQ — Fix infinite recursion in public.profiles RLS policies
--
--  CONFIRMED 2026-08-26 by simulating an authenticated read:
--    ERROR: 42P17: infinite recursion detected in policy for relation "profiles"
--
--  CAUSE
--  Three policies test for admin with an inline subquery against profiles:
--    "Admins can read all profiles"  EXISTS (SELECT 1 FROM profiles p WHERE ...)
--    "Users can view own profile"    (SELECT profiles_1.role FROM profiles ...)
--    "Admin can update any profile"  (SELECT profiles_1.role FROM profiles ...)
--  Reading profiles evaluates the policy, whose subquery reads profiles, which
--  evaluates the policy again. Postgres detects the cycle and aborts.
--
--  FIX
--  Move the admin test into a SECURITY DEFINER function. It runs as the owner,
--  so it does not re-enter RLS and the cycle is broken.
--
--  Steps 1 and 2 are safe to run now. Step 4 is NOT — read its warning.
--  No row data is inserted, updated or deleted by this file.
-- ============================================================================


-- 1. ADMIN HELPER -------------------------------------------------------------
create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select exists (
    select 1
    from public.profiles
    where id = auth.uid()
      and role in ('admin', 'super_admin')
  );
$$;

revoke all on function public.is_admin() from public, anon;
grant execute on function public.is_admin() to authenticated;


-- 2. REPLACE THE EIGHT POLICIES WITH THREE ------------------------------------
-- The three admin policies recurse; the other five are exact duplicates of each
-- other. All are replaced by equivalent non-recursive versions.
drop policy if exists "Users can insert own profile"       on public.profiles;
drop policy if exists "Users can insert their own profile" on public.profiles;
drop policy if exists "Users can read their own profile"   on public.profiles;
drop policy if exists "Users can view own profile"         on public.profiles;
drop policy if exists "Admins can read all profiles"       on public.profiles;
drop policy if exists "Users can update own profile"       on public.profiles;
drop policy if exists "Users can update their own profile" on public.profiles;
drop policy if exists "Admin can update any profile"       on public.profiles;

create policy "profiles_select" on public.profiles
  for select to authenticated
  using (auth.uid() = id or public.is_admin());

create policy "profiles_insert" on public.profiles
  for insert to authenticated
  with check (auth.uid() = id);

create policy "profiles_update" on public.profiles
  for update to authenticated
  using      (auth.uid() = id or public.is_admin())
  with check (auth.uid() = id or public.is_admin());

-- No DELETE policy is created. None existed before, so deletes stay blocked.


-- 3. VERIFY -------------------------------------------------------------------
-- 3a. The read that previously raised 42P17. Picks a real admin automatically.
--     rollback means nothing is written.
begin;
  select set_config(
    'request.jwt.claims',
    json_build_object(
      'sub',  (select id::text from public.profiles
                where role in ('admin','super_admin') limit 1),
      'role', 'authenticated'
    )::text,
    true
  );
  set local role authenticated;
  select id, email, role from public.profiles limit 5;   -- expect MANY rows
rollback;

-- 3b. Same read as an ordinary customer.
begin;
  select set_config(
    'request.jwt.claims',
    json_build_object(
      'sub',  (select id::text from public.profiles
                where coalesce(role,'customer') = 'customer' limit 1),
      'role', 'authenticated'
    )::text,
    true
  );
  set local role authenticated;
  select id, email, role from public.profiles;           -- expect EXACTLY 1 row
rollback;

-- 3c. PRIVILEGE ESCALATION TEST — this is the one that did not run earlier.
--     Today it is expected to SUCCEED, which is the security hole. Step 4 is
--     what closes it. rollback means nothing is written either way.
begin;
  select set_config(
    'request.jwt.claims',
    json_build_object(
      'sub',  (select id::text from public.profiles
                where coalesce(role,'customer') = 'customer' limit 1),
      'role', 'authenticated'
    )::text,
    true
  );
  set local role authenticated;
  update public.profiles
     set role = 'super_admin', wallet_balance = 999999
   where id = auth.uid()
  returning id, role, wallet_balance;   -- if this returns a row, escalation works
rollback;


-- 3d. Why did 3c leave role = 'customer' but let wallet_balance through?
--     RETURNING shows post-update values, so something reverted the role change.
--     Most likely a BEFORE UPDATE trigger, but confirm before assuming.
select t.tgname            as trigger_name,
       case t.tgtype::int & 2 when 2 then 'BEFORE' else 'AFTER' end as timing,
       pg_get_functiondef(t.tgfoid) as function_source
from pg_trigger t
join pg_class c on c.oid = t.tgrelid
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relname = 'profiles'
  and not t.tgisinternal;

-- 3e. Any CHECK constraint that might reject 'super_admin' outright.
select conname, pg_get_constraintdef(oid) as definition
from pg_constraint
where conrelid = 'public.profiles'::regclass
  and contype = 'c';


-- 4. COLUMN LOCKDOWN ----------------------------------------------------------
--
--  Confirmed by 3c: a customer can set their own wallet_balance to 999999.
--  role is already defended by the profiles_prevent_role_escalation trigger,
--  which is why 3c returned role='customer' without raising an error.
--  wallet_balance and loyalty_points have no such defence.
--
--  Revoke-then-regrant is required: Postgres does not decompose a table-level
--  UPDATE grant, so `revoke update (wallet_balance)` alone silently does nothing.
--
--  PREREQUISITE — both test devices must be running a build that drops
--  wallet_balance/loyalty_points from syncProfile()'s payload. Older APKs will
--  fail with "permission denied for column wallet_balance".
revoke update on public.profiles from authenticated, anon;

-- id is included deliberately: PostgREST puts the conflict target in the
-- ON CONFLICT DO UPDATE SET list, and the profiles_update WITH CHECK clause
-- (auth.uid() = id) already makes it impossible to point a row at another user.
grant update (
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

-- Deliberately NOT granted, i.e. server-owned from here on:
--   role            already trigger-protected; now grant-protected too
--   wallet_balance  the live hole 3c proved
--   loyalty_points  same class of problem
--   shop_id         isVendor is `shopId != null`, so writing it would let any
--                   customer promote themselves into the vendor dashboard
--   created_at, updated_at  audit columns


-- 5. VERIFY THE LOCKDOWN ------------------------------------------------------
-- 5a. Re-run the escalation. Expect: ERROR 42501 permission denied for column.
begin;
  select set_config(
    'request.jwt.claims',
    json_build_object(
      'sub',  (select id::text from public.profiles
                where coalesce(role,'customer') = 'customer' limit 1),
      'role', 'authenticated'
    )::text,
    true
  );
  set local role authenticated;
  update public.profiles
     set wallet_balance = 999999
   where id = auth.uid();          -- MUST FAIL
rollback;

-- 5b. A legitimate profile edit must still succeed.
begin;
  select set_config(
    'request.jwt.claims',
    json_build_object(
      'sub',  (select id::text from public.profiles
                where coalesce(role,'customer') = 'customer' limit 1),
      'role', 'authenticated'
    )::text,
    true
  );
  set local role authenticated;
  update public.profiles
     set full_name = 'RLS Test Name', mobile_number = '9999999999'
   where id = auth.uid()
  returning id, full_name, mobile_number;   -- MUST return 1 row
rollback;


-- 6. CONFIRM SECTION 4 LANDED CORRECTLY ---------------------------------------
-- If the revoke ran but the grant did not, `authenticated` ends up with NO
-- update permission at all and every profile edit fails. Read-only.
--
-- 6a. Expect ~12 rows for authenticated, and NO rows naming
--     wallet_balance, loyalty_points, role, shop_id, created_at or updated_at.
select grantee, column_name
from information_schema.column_privileges
where table_schema = 'public'
  and table_name   = 'profiles'
  and privilege_type = 'UPDATE'
  and grantee in ('anon', 'authenticated')
order by grantee, column_name;

-- 6b. Table-level privileges. UPDATE should NOT appear here for authenticated
--     any more; SELECT, INSERT and DELETE are unaffected by section 4.
select grantee, privilege_type
from information_schema.table_privileges
where table_schema = 'public'
  and table_name   = 'profiles'
  and grantee in ('anon', 'authenticated')
order by grantee, privilege_type;
-- ============================================================================


