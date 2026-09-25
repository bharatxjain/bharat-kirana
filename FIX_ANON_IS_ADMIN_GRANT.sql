-- =============================================================================
--  BreakQ — Fix: "permission denied for function is_admin"
--
--  The app fetches products with NO access token, so PostgREST evaluates the
--  query as the `anon` role. An RLS policy on products/shops/categories calls
--  public.is_admin(). anon has no EXECUTE on that function, so the policy
--  RAISES instead of returning false — the whole request fails and the app
--  receives nothing. Products look "deleted" while sitting safely in the table.
--
--  Fix: let anon execute the helper. It is SECURITY DEFINER and only reports
--  whether the CURRENT user is an admin, so a non-admin calling it learns
--  nothing and gains nothing — it simply returns false.
--
--  Safe to re-run.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Grant EXECUTE on every overload of is_admin (and any sibling helper the
--    policies might use). Looping covers is_admin(), is_admin(uuid), etc.
-- -----------------------------------------------------------------------------
do $$
declare
  fn record;
begin
  for fn in
    select p.oid::regprocedure as sig
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public'
       and p.proname in ('is_admin', 'is_super_admin', 'is_vendor')
  loop
    execute format('grant execute on function %s to anon, authenticated', fn.sig);
    raise notice 'granted execute on %', fn.sig;
  end loop;
end $$;


-- -----------------------------------------------------------------------------
-- 2. Which policies actually reference is_admin? This tells us whether the
--    products fetch really goes through it, and whether I introduced it.
--    (Nothing in ORDER_SERVER_AUTHORITY.sql touches these tables' policies.)
-- -----------------------------------------------------------------------------
select
  tablename,
  policyname,
  cmd,
  roles,
  coalesce(qual, '')       as using_expression,
  coalesce(with_check, '') as with_check_expression
from pg_policies
where schemaname = 'public'
  and (qual like '%is_admin%' or with_check like '%is_admin%')
order by tablename, cmd, policyname;


-- -----------------------------------------------------------------------------
-- 3. Confirm the grant landed. execute_granted must be true for both roles.
-- -----------------------------------------------------------------------------
select
  p.oid::regprocedure                                as function_signature,
  p.prosecdef                                        as security_definer,
  has_function_privilege('anon',          p.oid, 'EXECUTE') as anon_can_execute,
  has_function_privilege('authenticated', p.oid, 'EXECUTE') as auth_can_execute
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('is_admin', 'is_super_admin', 'is_vendor')
order by function_signature;
