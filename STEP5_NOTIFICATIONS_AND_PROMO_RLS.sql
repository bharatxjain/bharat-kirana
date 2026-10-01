-- =============================================================================
--  BreakQ — Step 5: notifications "Clear all" + private promo codes
--
--  Run the WHOLE file once. Safe to re-run. No row is changed or deleted.
--
--   1. notifications: a signed-in user may delete their OWN notifications.
--      The app's "Clear all" button needs this; without it the delete changes
--      0 rows and the notifications come back on the next load.
--   2. promo_codes: removes "Anyone can view active promo codes", which let
--      anyone (even signed-out visitors) list every active code of every shop.
--      The app never reads this table: promo checks run inside the SECURITY
--      DEFINER functions evaluate_promo / preview_promo / create_order_with_items,
--      which don't need it. Admin access is unchanged.
--
--  Then run TEST_STEP5_NOTIFICATIONS_AND_PROMO_RLS.sql.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 0. Pre-checks — stop before changing anything if the live database differs
--    from what this file expects.
-- -----------------------------------------------------------------------------
do $$
declare
  v_missing text;
  v_invoker text;
begin
  if to_regclass('public.notifications') is null or to_regclass('public.promo_codes') is null then
    raise exception 'notifications or promo_codes table is missing. Nothing was changed.';
  end if;

  if not exists (
    select 1 from information_schema.columns
     where table_schema = 'public' and table_name = 'notifications' and column_name = 'user_id'
  ) then
    raise exception 'notifications.user_id is missing. Nothing was changed — please share this message.';
  end if;

  select string_agg(f, ', ') into v_missing
    from unnest(array['evaluate_promo', 'preview_promo', 'create_order_with_items']) f
   where not exists (
     select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname = f
   );
  if v_missing is not null then
    raise exception 'Missing function(s): %. Run ORDER_SERVER_AUTHORITY.sql / STEP2 first. Nothing was changed.', v_missing;
  end if;

  -- If any of these ran with the caller's rights, removing the read rule would break promo codes at checkout.
  select string_agg(distinct p.proname, ', ') into v_invoker
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'public'
     and p.proname in ('evaluate_promo', 'preview_promo', 'create_order_with_items')
     and not p.prosecdef;
  if v_invoker is not null then
    raise exception 'Not SECURITY DEFINER: %. Removing the promo read rule would break checkout. Nothing was changed.', v_invoker;
  end if;

  if not exists (
    select 1 from pg_policies
     where schemaname = 'public' and tablename = 'promo_codes'
       and cmd in ('ALL', 'SELECT')
       and policyname <> 'Anyone can view active promo codes'
  ) then
    raise exception 'promo_codes has no admin rule besides the public one; the admin panel would lose access. Nothing was changed.';
  end if;
end $$;


-- -----------------------------------------------------------------------------
-- 1. notifications — delete your own
-- -----------------------------------------------------------------------------
drop policy if exists notifications_delete_own on public.notifications;
create policy notifications_delete_own on public.notifications
  for delete to authenticated
  using (user_id = auth.uid());

grant delete on public.notifications to authenticated;


-- -----------------------------------------------------------------------------
-- 2. promo_codes — no public listing
-- -----------------------------------------------------------------------------
drop policy if exists "Anyone can view active promo codes" on public.promo_codes;


-- -----------------------------------------------------------------------------
-- Verify — the rules now on both tables.
-- -----------------------------------------------------------------------------
select tablename::text as on_table, policyname::text as name, cmd::text as command,
       array_to_string(roles, ',') as roles
  from pg_policies
 where schemaname = 'public' and tablename in ('notifications', 'promo_codes')
 order by 1, 2;
