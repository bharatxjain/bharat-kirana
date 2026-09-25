-- =============================================================================
--  BreakQ — Why can't the app see products?
--
--  The app fetches with NO access token:
--      GET /rest/v1/products?select=*        (SupabaseGroceryRepo.fetchProducts)
--  so PostgREST runs that query as the `anon` role, not as the signed-in user.
--  If the SELECT policy on products doesn't grant anon, the app gets an empty
--  array while the SQL editor (running as postgres) shows every row.
--
--  Read-only. Run the whole file.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Is RLS on, and is it FORCED (which would apply even to the owner)?
-- -----------------------------------------------------------------------------
select
  c.relname                as table_name,
  c.relrowsecurity         as rls_enabled,
  c.relforcerowsecurity    as rls_forced
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relname in ('products', 'shops', 'categories');


-- -----------------------------------------------------------------------------
-- 2. The policies themselves. `roles` must include anon (or be {public})
--    for the app's unauthenticated product fetch to return anything.
-- -----------------------------------------------------------------------------
select
  tablename,
  policyname,
  cmd,
  roles,
  qual        as using_expression,
  with_check
from pg_policies
where schemaname = 'public'
  and tablename in ('products', 'shops', 'categories')
order by tablename, cmd, policyname;


-- -----------------------------------------------------------------------------
-- 3. Table-level grants. RLS is not the only gate — anon also needs SELECT.
-- -----------------------------------------------------------------------------
select
  table_name,
  grantee,
  privilege_type
from information_schema.role_table_grants
where table_schema = 'public'
  and table_name in ('products', 'shops', 'categories')
  and grantee in ('anon', 'authenticated', 'public')
order by table_name, grantee, privilege_type;


-- -----------------------------------------------------------------------------
-- 4. Exactly what the app would get. This runs the SAME query the app runs,
--    as the SAME role the app uses.
--    Run these THREE statements ONE AT A TIME, in order.
-- -----------------------------------------------------------------------------
-- (4a)
set role anon;

-- (4b)  <- this number is what the app sees. If it is 0, RLS/grants are the bug.
select
  count(*)                                              as products_anon_can_see,
  count(*) filter (where shop_id = 's_1787839141966')   as yash_lala_visible
from public.products;

-- (4c)  IMPORTANT: always run this to drop back to your normal role.
reset role;


-- -----------------------------------------------------------------------------
-- 5. Ground truth as postgres, for comparison with 4b.
-- -----------------------------------------------------------------------------
select
  count(*)                                              as products_total,
  count(*) filter (where shop_id = 's_1787839141966')   as yash_lala_total
from public.products;
