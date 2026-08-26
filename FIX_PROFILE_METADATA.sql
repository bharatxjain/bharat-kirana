-- ============================================================================
--  BreakQ — Fix: mobile_number / full_name never reach public.profiles
--
--  ROOT CAUSE
--  signUp() puts full_name + mobile + address into auth.users.raw_user_meta_data,
--  but handle_new_user() wrote empty strings and ignored them. The app only
--  called updateProfile() when Supabase returned a session immediately — which
--  it doesn't when "Confirm email" is enabled (the default). So for every user
--  who verified via the emailed link, mobile_number stayed EMPTY forever.
--
--  Safe to re-run.
-- ============================================================================

-- 1. DIAGNOSTIC — what did signup actually store? ----------------------------
-- Run this first. If raw_user_meta_data shows a mobile but profiles doesn't,
-- this file is the fix. If metadata is ALSO empty, the signup form isn't
-- collecting it and the problem is client-side.
select
  u.email,
  u.raw_user_meta_data ->> 'full_name' as meta_name,
  u.raw_user_meta_data ->> 'mobile'    as meta_mobile,
  p.full_name                          as profile_name,
  p.mobile_number                      as profile_mobile
from auth.users u
left join public.profiles p on p.id = u.id
order by u.created_at desc
limit 20;

-- 2. BACKFILL EXISTING USERS --------------------------------------------------
-- Run this BEFORE the trigger change; it stands alone and fixes the 10 rows you
-- already have. coalesce(...,'') is deliberate: the blank cells may be NULL or
-- empty string, and `NULL = ''` is NULL, which would silently skip every row.
update public.profiles p
   set full_name     = coalesce(nullif(p.full_name, ''),
                                u.raw_user_meta_data ->> 'full_name',
                                split_part(u.email, '@', 1)),
       mobile_number = coalesce(nullif(p.mobile_number, ''),
                                u.raw_user_meta_data ->> 'mobile', ''),
       address       = coalesce(nullif(p.address, ''),
                                u.raw_user_meta_data ->> 'address', '')
  from auth.users u
 where u.id = p.id
   and (coalesce(p.full_name, '')     = ''
     or coalesce(p.mobile_number, '') = ''
     or coalesce(p.address, '')       = '');

-- Mark profiles complete where the backfill produced both name and mobile.
update public.profiles
   set profile_completed = true
 where coalesce(full_name, '') <> ''
   and coalesce(mobile_number, '') <> ''
   and coalesce(profile_completed, false) = false;


-- 3. INSPECT THE CURRENT TRIGGER ----------------------------------------------
-- STOP HERE and read the output. Your live trigger already copies full_name and
-- falls back to the email local-part, so it does more than a blank insert — it
-- may also seed columns (wallet_balance, role, loyalty_points) that step 4 does
-- not list. If it does, tell me before running step 4.
-- unnest() splits the definition one line per row; the results grid truncates a
-- single long cell and hides exactly the part we need to read.
select
  row_number() over () as ln,
  line
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
cross join lateral unnest(string_to_array(pg_get_functiondef(p.oid), E'\n')) as line
where n.nspname = 'public'
  and p.proname  = 'handle_new_user';


-- 4. FIXED TRIGGER ------------------------------------------------------------
-- Copies the signup metadata into profiles instead of discarding it. Works no
-- matter how the user verifies (in-app OTP or emailed link).
-- ONLY run this after reviewing step 3's output.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  -- Aliased as `pr` because ON CONFLICT DO UPDATE cannot reference the target
  -- by a schema-qualified name.
  insert into public.profiles as pr (
    id, email, full_name, mobile_number, address,
    profile_completed, role, created_at
  )
  values (
    new.id,
    new.email,
    -- Matches the existing trigger's behaviour: fall back to the email local-part.
    coalesce(nullif(new.raw_user_meta_data ->> 'full_name', ''),
             split_part(new.email, '@', 1)),
    coalesce(new.raw_user_meta_data ->> 'mobile', ''),
    coalesce(new.raw_user_meta_data ->> 'address', ''),
    -- Complete only if signup actually captured both name and mobile.
    (coalesce(new.raw_user_meta_data ->> 'full_name', '') <> ''
     and coalesce(new.raw_user_meta_data ->> 'mobile', '') <> ''),
    'customer',
    now()
  )
  on conflict (id) do update set
    -- Never overwrite a value the user has already edited in-app.
    full_name     = coalesce(nullif(pr.full_name, ''),     excluded.full_name),
    mobile_number = coalesce(nullif(pr.mobile_number, ''), excluded.mobile_number),
    address       = coalesce(nullif(pr.address, ''),       excluded.address);
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ============================================================================
-- VERIFY (re-run the query from step 1) — profile_mobile should now match
-- meta_mobile for every user whose signup captured one. Expect officialauracloths,
-- bjain5329 and officialbharatjain2004 to stay blank: they have no meta_mobile,
-- so there is nothing to recover for them.
-- ============================================================================


-- 5. CHECK RLS ON profiles ----------------------------------------------------
-- Steps 2 and 4 both ran with elevated privileges, so neither proves the APP can
-- write to profiles. If there is no UPDATE policy (or its check excludes
-- mobile_number's row), updateProfile() will fail silently and the column will
-- keep reverting to blank. Read-only.
select
  c.relrowsecurity as rls_enabled,
  pol.policyname,
  pol.cmd,
  pol.qual  as using_expr,
  pol.with_check as check_expr
from pg_policies pol
join pg_class c on c.relname = pol.tablename
where pol.schemaname = 'public'
  and pol.tablename  = 'profiles'
order by pol.cmd, pol.policyname;

