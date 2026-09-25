-- =============================================================================
--  BreakQ — Move a vendor account back to customer
--
--  One email = one account. profiles.id IS auth.users.id, so registering a
--  shop never created a second account — the shop-insert trigger
--  (link_shop_to_profile) just flipped role 'customer' -> 'vendor' and set
--  shop_id on the SAME row. The app then routes by role and never shows the
--  customer screens again, which makes the order history look deleted.
--
--  Nothing was ever lost. This puts the role back.
--
--  To use this for another account, find-and-replace the email below.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. BEFORE — what this account currently holds.
-- -----------------------------------------------------------------------------
select
  'BEFORE' as stage,
  u.id as auth_user_id,
  u.email,
  p.role,
  p.shop_id,
  (select count(*) from public.orders o             where o.user_id  = u.id) as orders,
  (select count(*) from public.customer_addresses a where a.user_id  = u.id) as addresses,
  (select count(*) from public.wishlists w          where w.user_id  = u.id) as wishlist,
  (select count(*) from public.shops s              where s.owner_id = u.id) as shops_owned
from auth.users u
join public.profiles p on p.id = u.id
where lower(u.email) = lower('itzalexparker2001@gmail.com');


-- -----------------------------------------------------------------------------
-- 2. Demote to customer.
--
--    profiles has two BEFORE-UPDATE guards (prevent_role_escalation and
--    profiles_prevent_role_escalation) that SILENTLY swallow role changes —
--    no error, the write just doesn't happen. They must be disabled for this
--    one statement or the role stays 'vendor'. Same pattern as
--    FIX_ROLE_HEAL.sql section C.
--
--    shop_id must be cleared too. MainScreen routes role=vendor to the vendor
--    dashboard, which then sends anyone with no shop_id to VendorRegistration
--    with no way back. role='vendor' + shop_id=null is the worst possible
--    state, so both have to move together.
-- -----------------------------------------------------------------------------
begin;

alter table public.profiles disable trigger user;

update public.profiles p
   set role    = 'customer',
       shop_id = null
  from auth.users u
 where u.id = p.id
   and lower(u.email) = lower('itzalexparker2001@gmail.com')
   and p.role = 'vendor';   -- never demote an admin by accident

alter table public.profiles enable trigger user;

commit;


-- -----------------------------------------------------------------------------
-- 3. The shop this account owned is now unreachable — nobody can open its
--    dashboard, so orders placed against it would never be seen.
--
--    Review it first:
-- -----------------------------------------------------------------------------
select
  'ORPHANED SHOP' as stage,
  s.id, s.name, s.status::text as status, s.is_deleted, s.accepting_orders,
  (select count(*) from public.products pr where pr.shop_id = s.id) as products,
  (select count(*) from public.orders   o  where o.shop_id  = s.id) as orders
from public.shops s
join auth.users u on u.id = s.owner_id
where lower(u.email) = lower('itzalexparker2001@gmail.com');

-- If that shop was only a test, stop it taking orders. Uncomment to apply:
--
-- update public.shops s
--    set accepting_orders = false,
--        is_deleted       = true
--   from auth.users u
--  where u.id = s.owner_id
--    and lower(u.email) = lower('itzalexparker2001@gmail.com');


-- -----------------------------------------------------------------------------
-- 4. AFTER — confirm the role flipped and the data is all still there.
-- -----------------------------------------------------------------------------
select
  'AFTER' as stage,
  u.id as auth_user_id,
  u.email,
  p.role,
  p.shop_id,
  (select count(*) from public.orders o             where o.user_id = u.id) as orders,
  (select count(*) from public.customer_addresses a where a.user_id = u.id) as addresses,
  (select count(*) from public.wishlists w          where w.user_id = u.id) as wishlist
from auth.users u
join public.profiles p on p.id = u.id
where lower(u.email) = lower('itzalexparker2001@gmail.com');
