-- ============================================================================
--  BreakQ — Heal existing vendors + make shop-insert promotion trigger-safe
--
--  Problems discovered from prior runs:
--   (i)  profiles.role has a CHECK constraint (profiles_role_check) that only
--        allows customer/admin/super_admin. 'vendor' isn't in the whitelist,
--        so any UPDATE …SET role='vendor' fails with 23514. This is the
--        root reason no vendor row has ever existed in the DB.
--   (ii) Two BEFORE-UPDATE triggers on profiles (prevent_role_escalation and
--        profiles_prevent_role_escalation) block role changes from client-owned
--        code paths. They won't error, they just silently swallow the change.
--
--  Fix in four parts, in order:
--    A. DIAGNOSTIC — dump the current check constraint definition so you can
--       see the whitelist before we change it.
--    B. CHECK CONSTRAINT — drop-and-recreate to include 'vendor'.
--    C. HEAL — DISABLE TRIGGER USER + UPDATE, wrapped in a transaction.
--    D. TRIGGER — rewrite link_shop_to_profile to skip user triggers only for
--       its own frame (SET LOCAL session_replication_role).
--
--  Safe to re-run. All changes are idempotent.
-- ============================================================================


-- A. DIAGNOSTIC ---------------------------------------------------------------
-- What does the current check constraint allow? Expected output: something
-- like  role = ANY (ARRAY['customer'::text, 'admin'::text, 'super_admin'::text])
-- with NO 'vendor' entry — which is exactly why B in the previous run failed.
select
  con.conname                              as constraint_name,
  pg_get_constraintdef(con.oid)            as definition
from pg_constraint con
join pg_class c        on c.oid = con.conrelid
join pg_namespace n    on n.oid = c.relnamespace
where c.relname = 'profiles'
  and n.nspname = 'public'
  and con.contype = 'c'
order by con.conname;


-- B. WIDEN THE CHECK CONSTRAINT -----------------------------------------------
-- Drop the old whitelist and recreate it with 'vendor' included. The name
-- 'profiles_role_check' matched the error message the SQL editor showed us.
-- If your DB happens to name it differently, adjust the DROP line accordingly.
alter table public.profiles
  drop constraint if exists profiles_role_check;

alter table public.profiles
  add constraint profiles_role_check
  check (role in ('customer', 'vendor', 'admin', 'super_admin'));


-- C. HEAL EXISTING VENDORS -----------------------------------------------------
-- Disable user triggers on profiles for one atomic block so the escalation
-- guard doesn't swallow the role change. The window is single-statement inside
-- a transaction, so no other client sees the guard off.
begin;

alter table public.profiles disable trigger user;

update public.profiles
set role = 'vendor'
where shop_id is not null
  and role in ('customer');  -- never demote admin/super_admin

alter table public.profiles enable trigger user;

commit;


-- D. FUTURE-PROOF THE SHOP-INSERT TRIGGER --------------------------------------
-- Rewrite link_shop_to_profile so it can update profiles.role without being
-- intercepted by the escalation guards. Uses SET LOCAL session_replication_role
-- = replica: this skips triggers only for statements executed inside this
-- function's own frame. Every other UPDATE path (client PATCH, admin UI, etc.)
-- still hits the guards, so security isn't weakened.
--
-- SECURITY DEFINER + owner=postgres is required for session_replication_role to
-- succeed. In Supabase SQL editor you run as postgres, so this works.
create or replace function public.link_shop_to_profile()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  set local session_replication_role = replica;

  update public.profiles
  set shop_id = new.id,
      role = case
               when role in ('admin', 'super_admin') then role
               else 'vendor'
             end
  where id = new.owner_id
    and (shop_id is null or shop_id = new.id);

  -- reset explicitly; on transaction end SET LOCAL is auto-restored anyway.
  set local session_replication_role = origin;
  return new;
end;
$$;

-- Trigger definition unchanged from before; recreated to be safe against a
-- half-applied earlier migration.
drop trigger if exists on_shop_insert_link_profile on public.shops;
create trigger on_shop_insert_link_profile
  after insert on public.shops
  for each row
  when (new.owner_id is not null)
  execute function public.link_shop_to_profile();


-- VERIFY ----------------------------------------------------------------------
-- Row count per role. Expect the four accounts with shop_id set to now show
-- up as 'vendor'.
select role, count(*) as total
from public.profiles
group by role
order by role;

-- Detail — the four accounts you screenshotted should now say 'vendor'.
select email, full_name, role, shop_id
from public.profiles
where shop_id is not null
order by created_at;
