-- ============================================================================
--  BreakQ — Fix vendor registration RLS block on vendor_subscriptions
--
--  ROOT CAUSE
--  After a vendor's INSERT into public.shops succeeds, the trigger
--  auto_free_tier_on_shop_insert() tries to INSERT a starter subscription row
--  into public.vendor_subscriptions. That trigger runs as the calling role
--  (the authenticated vendor). The RLS policy "admin manages subs" only allows
--  admins to INSERT/UPDATE/DELETE on vendor_subscriptions. Result: 42501,
--  "new row violates row-level security policy".
--
--  FIX
--  Recreate the trigger function with SECURITY DEFINER so it executes as the
--  function owner (typically the schema owner) and skips RLS on that INSERT.
--  This is the same pattern we used for link_shop_to_profile() and is_admin().
--
--  This does not weaken security. The trigger only inserts one specific row
--  keyed to the shop that was just legitimately created; it cannot be called
--  from application code directly.
-- ============================================================================

create or replace function public.auto_free_tier_on_shop_insert()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  -- WHERE NOT EXISTS instead of ON CONFLICT DO NOTHING because the unique
  -- constraint on vendor_subscriptions is DEFERRABLE, which Postgres refuses
  -- as an ON CONFLICT arbiter. Same effect: only inserts if there's no active
  -- row for this shop yet.
  insert into public.vendor_subscriptions (shop_id, tier_id, status)
  select new.id, 'free', 'active'
  where not exists (
    select 1 from public.vendor_subscriptions
    where shop_id = new.id and status = 'active'
  );
  return new;
end;
$$;

-- Verify the fix landed.
select proname, prosecdef, pg_get_functiondef(oid)
from pg_proc
where proname = 'auto_free_tier_on_shop_insert';
-- prosecdef = true means SECURITY DEFINER is set.
