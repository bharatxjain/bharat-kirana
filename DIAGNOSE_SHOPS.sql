-- ============================================================================
--  BreakQ — Diagnose "no shops" + unexpected wallet values
--
--  HYPOTHESIS
--  Nothing was deleted. Before the RLS recursion fix, reads of profiles (and
--  anything whose policy references profiles) failed, so the app silently kept
--  showing GroceryRepository.getShops() — a hardcoded demo list — and a wallet
--  default of 0. Now that reads succeed, the app is displaying real data.
--
--  Every query here is READ-ONLY. Nothing is written.
-- ============================================================================


-- 1. HOW MANY SHOPS ACTUALLY EXIST? -------------------------------------------
-- If this returns 0, the app is correct to show "No local shops" and the demo
-- shops you saw before were never real.
select count(*) as total_shops from public.shops;

-- 1b. Break down by status and location. NearbyShopsScreen only displays shops
--     with status APPROVED, and filters by distance from lat/lng.
select
  id,
  name,
  status,
  lat,
  lng,
  case when lat is null or lng is null or (lat = 0 and lng = 0)
       then 'NO COORDS - cannot appear on map or in radius filter'
       else 'ok' end as coord_check
from public.shops
order by name;


-- 2. WHY IS THIS USER SEEING THE VENDOR DASHBOARD? ----------------------------
-- The app treats anyone with a non-null shop_id as a vendor:
--   isVendor = shopId != null || serverRole == VENDOR
-- If shop_id points at a shop row that does not exist, the vendor dashboard has
-- nothing to show and sits on the loading state.
select
  p.email,
  p.role,
  p.shop_id,
  p.wallet_balance,
  p.loyalty_points,
  s.id   as matched_shop_id,
  s.name as matched_shop_name,
  case
    when p.shop_id is null            then 'customer - not a vendor'
    when s.id is null                 then 'BROKEN: shop_id set but no such shop row'
    else 'ok'
  end as vendor_check
from public.profiles p
left join public.shops s on s.id = p.shop_id
order by p.email;


-- 3. DO OTHER TABLES HAVE THE SAME RECURSION PATTERN? -------------------------
-- profiles is fixed, but any policy on another table that inlines a subquery
-- against profiles will still be slow or fragile. This lists every policy in
-- the schema whose expression mentions profiles.
select
  tablename,
  policyname,
  cmd,
  coalesce(qual, '') || ' ' || coalesce(with_check, '') as expression
from pg_policies
where schemaname = 'public'
  and (coalesce(qual, '') || coalesce(with_check, '')) like '%profiles%'
  and tablename <> 'profiles'
order by tablename, policyname;


-- 4. CONFIRM NOTHING WAS LOST -------------------------------------------------
-- Row counts for the main tables. Compare against what you expect.
select 'profiles' as table_name, count(*) from public.profiles
union all select 'shops',        count(*) from public.shops
union all select 'products',     count(*) from public.products
union all select 'orders',       count(*) from public.orders;


-- 5. WHY IS EVERY ROW IDENTICAL? ----------------------------------------------
-- wallet_balance = 150 and loyalty_points = 350 for all 10 users, and
-- customer_mobile = '98765 43210' on every order. The app writes wallet/loyalty
-- nowhere and writes customer_mobile from the user's profile (which was blank),
-- so these are almost certainly demo column DEFAULTS baked into the schema.
select table_name, column_name, column_default
from information_schema.columns
where table_schema = 'public'
  and table_name in ('profiles', 'orders')
  and column_default is not null
order by table_name, ordinal_position;

-- 5b. Just the three suspicious columns, so the grid does not scroll them away.
--     A non-null default here proves the values are baked into the schema and
--     were never written by the app.
select table_name, column_name,
       coalesce(column_default, '(no default)') as column_default
from information_schema.columns
where table_schema = 'public'
  and (
    (table_name = 'profiles' and column_name in ('wallet_balance', 'loyalty_points'))
    or (table_name = 'orders' and column_name in ('customer_mobile', 'customer_name'))
  )
order by table_name, column_name;

-- 5c. Are the values actually uniform, or does it only look that way?
select
  count(*)                              as total_profiles,
  count(distinct wallet_balance)        as distinct_wallets,
  min(wallet_balance)                   as min_wallet,
  max(wallet_balance)                   as max_wallet,
  count(distinct loyalty_points)        as distinct_points
from public.profiles;


-- 6. ARE ANY ORDERS REAL? -----------------------------------------------------
-- insertOrder() always sets user_id when logged in, and writes '' for a blank
-- mobile. So a row with user_id NULL and mobile '98765 43210' cannot have come
-- from the app. Read-only.
select
  case
    when user_id is null and customer_mobile = '98765 43210'
      then 'SEED DATA - inserted directly into Supabase'
    when user_id is null
      then 'no user_id - placed logged-out, or seed'
    else 'REAL - created by the app'
  end as origin,
  count(*) as orders,
  min(created_at) as earliest,
  max(created_at) as latest
from public.orders
group by 1
order by orders desc;
-- ============================================================================



