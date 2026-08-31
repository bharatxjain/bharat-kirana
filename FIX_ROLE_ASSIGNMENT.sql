-- ============================================================================
--  BreakQ — Fix role assignment for vendors
--
--  Three things:
--    A. handle_new_user() reads `role` from signup metadata (defaults to
--       'customer' when the client didn't send one).
--    B. link_shop_to_profile() also flips role to 'vendor' on shop insert,
--       so a user's role and shop_id stay in sync.
--    C. One-time data heal: every existing profile that already has a shop_id
--       but is still marked 'customer' is corrected to 'vendor'. Admins are
--       never demoted.
--
--  Safe to re-run. Read-committed. No column additions.
-- ============================================================================


-- A. Read role from signup metadata --------------------------------------------
-- Same shape as FIX_PROFILE_METADATA.sql's version, but with a role field that
-- honours what the app sent instead of hardcoding 'customer'.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.profiles as pr (
    id, email, full_name, mobile_number, address,
    profile_completed, role, created_at
  )
  values (
    new.id,
    new.email,
    coalesce(nullif(new.raw_user_meta_data ->> 'full_name', ''),
             split_part(new.email, '@', 1)),
    coalesce(new.raw_user_meta_data ->> 'mobile', ''),
    coalesce(new.raw_user_meta_data ->> 'address', ''),
    (coalesce(new.raw_user_meta_data ->> 'full_name', '') <> ''
     and coalesce(new.raw_user_meta_data ->> 'mobile', '') <> ''),
    -- Only accept role values the app actually uses. Anything else falls back
    -- to 'customer' so a malicious client can't self-promote to admin.
    case
      when (new.raw_user_meta_data ->> 'role') in ('vendor', 'customer')
        then new.raw_user_meta_data ->> 'role'
      else 'customer'
    end,
    now()
  )
  on conflict (id) do update set
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


-- B. Shop-insert trigger promotes owner to vendor -----------------------------
-- Extends SHOPS_LINK_TRIGGER.sql: alongside setting shop_id, also flip role
-- to 'vendor' when the owner is still 'customer'. Admins/super_admins keep
-- their existing role (never silently demoted).
create or replace function public.link_shop_to_profile()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  update public.profiles
  set shop_id = new.id,
      role = case
               when role in ('admin', 'super_admin') then role
               else 'vendor'
             end
  where id = new.owner_id
    and (shop_id is null or shop_id = new.id);
  return new;
end;
$$;

drop trigger if exists on_shop_insert_link_profile on public.shops;
create trigger on_shop_insert_link_profile
  after insert on public.shops
  for each row
  when (new.owner_id is not null)
  execute function public.link_shop_to_profile();


-- C. One-time data heal --------------------------------------------------------
-- Any existing vendor whose profile still says 'customer' but has a real shop
-- pointer gets promoted. Skips admins/super_admins.
update public.profiles
set role = 'vendor'
where shop_id is not null
  and role = 'customer';


-- Sanity check -----------------------------------------------------------------
select role, count(*) as total
from public.profiles
group by role
order by role;
