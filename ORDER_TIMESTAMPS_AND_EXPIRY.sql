-- =============================================================================
--  BreakQ — Order status timestamps + auto-expiration of pending orders
--
--  Fixes the "30-hour pending order" problem end-to-end:
--    - Adds per-status timestamp columns so the customer's timeline shows
--      REAL times instead of the client's "Today, <now>" placeholder.
--    - Trigger stamps them on every status transition (idempotent per column
--      so restatements never overwrite earlier values).
--    - pg_cron job auto-cancels orders stuck in 'Order Placed' for > 3 hours.
--
--  Nothing here weakens RLS, deletes data or alters existing values. Old rows
--  simply have NULL timestamps for statuses that had already been reached
--  before this migration ran — new transitions will populate them going
--  forward. Safe to re-run.
-- =============================================================================

-- ── 1. New timestamp columns ────────────────────────────────────────────────
alter table public.orders
  add column if not exists confirmed_at  timestamptz,
  add column if not exists preparing_at  timestamptz,
  add column if not exists ready_at      timestamptz,
  add column if not exists completed_at  timestamptz,
  add column if not exists cancelled_at  timestamptz;

-- ── 2. Stamp them on status transition ─────────────────────────────────────
create or replace function public.stamp_order_status_at()
returns trigger
language plpgsql
as $$
begin
  if new.status is distinct from old.status then
    case new.status
      when 'Order Confirmed'  then new.confirmed_at := coalesce(new.confirmed_at, now());
      when 'Preparing'        then new.preparing_at := coalesce(new.preparing_at, now());
      when 'Ready for Pickup' then new.ready_at     := coalesce(new.ready_at, now());
      when 'Completed'        then new.completed_at := coalesce(new.completed_at, now());
      when 'Cancelled'        then new.cancelled_at := coalesce(new.cancelled_at, now());
      else null;
    end case;
  end if;
  return new;
end
$$;

drop trigger if exists trg_stamp_order_status_at on public.orders;
create trigger trg_stamp_order_status_at
  before update of status on public.orders
  for each row
  execute function public.stamp_order_status_at();

-- ── 3. Auto-expire abandoned orders (still 'Order Placed' > 3h) ────────────
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
     and o.created_at < now() - interval '3 hours';
  get diagnostics n = row_count;
  return n;
end
$$;

revoke all on function public.expire_pending_orders() from public, anon, authenticated;
grant execute on function public.expire_pending_orders() to service_role;

-- ── 4. pg_cron schedule (every 10 minutes) ─────────────────────────────────
create extension if not exists pg_cron;

do $$
begin
  begin
    perform cron.unschedule('expire-pending-orders');
  exception when others then null;
  end;
end
$$;

select cron.schedule(
  'expire-pending-orders',
  '*/10 * * * *',
  $cron$ select public.expire_pending_orders() $cron$
);

-- ── Verify ─────────────────────────────────────────────────────────────────
select column_name, data_type
  from information_schema.columns
 where table_schema = 'public'
   and table_name   = 'orders'
   and column_name in ('confirmed_at','preparing_at','ready_at','completed_at','cancelled_at')
 order by column_name;

select tgname, tgenabled
  from pg_trigger
 where tgrelid = 'public.orders'::regclass
   and not tgisinternal
   and tgname = 'trg_stamp_order_status_at';

select jobname, schedule, active
  from cron.job
 where jobname = 'expire-pending-orders';
