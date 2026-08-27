-- ============================================================================
--  BreakQ — Wipe test accounts only (safe to re-run whenever emails run out)
--
--  What this does:
--    - Truncates orders, products, shops, profiles, notifications
--    - Deletes every user from auth.users so their emails become reusable
--
--  What it does NOT do:
--    - Does NOT touch categories, subscription_tiers, promo_codes
--    - Does NOT touch schema, RLS, triggers, functions
--    - Does NOT touch storage buckets
--
--  Run all three statements. Order matters (FK dependencies).
-- ============================================================================


-- 1. Wipe public tables (CASCADE picks up shop_ratings, vendor_subscriptions,
--    subscription_payments, shop_view_events, promoted_placements).
truncate
  public.orders,
  public.products,
  public.shops,
  public.profiles,
  public.notifications
restart identity cascade;


-- 2. Delete every auth user. Frees up the emails for reuse. auth.identities,
--    auth.sessions, etc. are cleared automatically via ON DELETE CASCADE.
delete from auth.users;


-- 3. Verify. Expect zero rows for orders, products, shops, profiles,
--    notifications and auth.users. categories, subscription_tiers,
--    promo_codes stay as reference data.
select 'auth.users' as source, count(*) as row_count from auth.users
union all select 'profiles',     count(*) from public.profiles
union all select 'shops',        count(*) from public.shops
union all select 'products',     count(*) from public.products
union all select 'orders',       count(*) from public.orders
union all select 'notifications',count(*) from public.notifications;
-- ============================================================================
