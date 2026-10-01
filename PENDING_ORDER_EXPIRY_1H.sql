-- =============================================================================
--  BreakQ — Orders not accepted by the shop auto-cancel after 1 HOUR (was 3).
--
--  Only the time limit inside expire_pending_orders() changes. Everything else
--  stays as it is:
--    - same 'expire-pending-orders' cron job, same 10-minute schedule
--    - only orders still in 'Order Placed' are cancelled; Confirmed, Preparing,
--      Ready for Pickup and Completed orders are never touched
--    - stock comes back through the existing trg_restore_stock_on_cancel
--    - the customer is told through the existing order-status notification
--
--  Because the job runs every 10 minutes, an order is cancelled between 60
--  and 70 minutes after it was placed.
--
--  Run the WHOLE file at once. If the live function is not the version this
--  file expects, it stops with an error and changes nothing. Safe to re-run.
--  Then run TEST_PENDING_ORDER_EXPIRY.sql.
-- =============================================================================

-- Pre-check: refuse to overwrite a live function that differs from the known one.
do $$
declare
  v_live text;
  v_known text := regexp_replace(lower($body$
    declare
      n integer;
    begin
      update public.orders o
         set status       = 'Cancelled',
             cancelled_at = coalesce(o.cancelled_at, now())
       where o.status = 'Order Placed'
         and o.created_at < now() - interval '3 hours';
      get diagnostics n = row_count;
      return n;
    end
  $body$), '\s+', '', 'g');
begin
  select regexp_replace(lower(p.prosrc), '\s+', '', 'g') into v_live
    from pg_proc p
   where p.oid = 'public.expire_pending_orders()'::regprocedure;

  if v_live is distinct from v_known
     and v_live is distinct from replace(v_known, '''3hours''', '''1hour''') then
    raise exception 'expire_pending_orders() on this database is different from the repo version. Nothing was changed. Please share the output of: select pg_get_functiondef(''public.expire_pending_orders()''::regprocedure);';
  end if;
end
$$;

create or replace function public.expire_pending_orders()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  n integer;
begin
  update public.orders o
     set status       = 'Cancelled',
         cancelled_at = coalesce(o.cancelled_at, now())
   where o.status = 'Order Placed'
     and o.created_at < now() - interval '1 hour';
  get diagnostics n = row_count;
  return n;
end
$$;

revoke all on function public.expire_pending_orders() from public, anon, authenticated;
grant execute on function public.expire_pending_orders() to service_role;

-- ── Verify ─────────────────────────────────────────────────────────────────
select
  (select p.prosrc like '%interval ''1 hour''%'
     from pg_proc p where p.oid = 'public.expire_pending_orders()'::regprocedure) as one_hour_limit,
  (select j.schedule from cron.job j where j.jobname = 'expire-pending-orders')   as job_schedule,
  (select j.active   from cron.job j where j.jobname = 'expire-pending-orders')   as job_active;
