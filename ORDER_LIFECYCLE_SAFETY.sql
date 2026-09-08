-- =============================================================================
--  BreakQ — Order lifecycle safety (Task 9)
--
--  H1: Make complete_order_by_pickup_token atomic (close TOCTOU race with a
--      concurrent customer cancel).
--  M1: Add a BEFORE UPDATE trigger that blocks any transition out of a
--      terminal state (Completed, Cancelled). Defense-in-depth for the
--      "vendor revives a cancelled order" scenario.
--
--  Safe to re-run.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- H1: Atomic pickup completion
--
-- The old body did:
--   SELECT * INTO v ... ; check v.status ; UPDATE ... WHERE id = v.id ;
-- so a race between the SELECT and the UPDATE could resurrect a just-cancelled
-- order to Completed. The new UPDATE has `AND status = 'Ready for Pickup'` in
-- its WHERE, making the check-and-set atomic under Postgres MVCC.
-- -----------------------------------------------------------------------------
create or replace function public.complete_order_by_pickup_token(p_token text)
returns table (
  order_id       text,
  order_number   integer,
  status         text,
  customer_name  text,
  total_amount   integer
)
language plpgsql
security definer
as $$
declare
  v         public.orders;
  s         public.shops;
  v_updated public.orders;
begin
  select * into v from public.orders where pickup_token = p_token limit 1;
  if v.id is null then
    raise exception 'INVALID_TOKEN' using errcode = 'P0001';
  end if;

  select * into s from public.shops where id = v.shop_id;
  if s.id is null or s.owner_id is null or s.owner_id <> auth.uid() then
    raise exception 'NOT_YOUR_SHOP' using errcode = 'P0002';
  end if;

  -- Cheap pre-flight for pretty error codes. The real safety is the UPDATE
  -- guard below — these checks are hint-only, not authoritative.
  if v.status = 'Cancelled' then
    raise exception 'ORDER_CANCELLED' using errcode = 'P0003';
  end if;
  if v.status = 'Completed' then
    raise exception 'ALREADY_COMPLETED' using errcode = 'P0004';
  end if;

  update public.orders
     set status                   = 'Completed',
         pickup_token_consumed_at = now()
   where id     = v.id
     and status = 'Ready for Pickup'
   returning * into v_updated;

  if v_updated.id is null then
    -- A concurrent transaction changed the row between our SELECT and UPDATE.
    -- Re-read and raise the accurate error.
    select * into v from public.orders where id = v.id;
    if v.status = 'Cancelled' then
      raise exception 'ORDER_CANCELLED' using errcode = 'P0003';
    elsif v.status = 'Completed' then
      raise exception 'ALREADY_COMPLETED' using errcode = 'P0004';
    else
      raise exception 'NOT_READY_FOR_PICKUP' using errcode = 'P0005';
    end if;
  end if;

  return query select
    v_updated.id, v_updated.order_number, v_updated.status,
    v_updated.customer_name, v_updated.total_amount;
end;
$$;

-- -----------------------------------------------------------------------------
-- M1: Block any transition out of a terminal state.
--
-- Deliberately minimal — this does NOT enforce the full forward ladder,
-- because a live app sometimes needs admin overrides. It only refuses the
-- objectively wrong cases: reviving a Completed or Cancelled order. Every
-- forward transition already used by the vendor UI keeps working.
-- -----------------------------------------------------------------------------
create or replace function public.prevent_terminal_order_regression()
returns trigger
language plpgsql
as $$
begin
  if old.status in ('Completed', 'Cancelled') and new.status <> old.status then
    raise exception 'CANNOT_REVIVE_TERMINAL_ORDER: was %, cannot change to %',
                    old.status, new.status
      using errcode = 'P0001';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_prevent_terminal_order_regression on public.orders;
create trigger trg_prevent_terminal_order_regression
  before update of status on public.orders
  for each row execute function public.prevent_terminal_order_regression();

-- -----------------------------------------------------------------------------
-- Verify
-- -----------------------------------------------------------------------------
select tgname as trigger_name, tgenabled
  from pg_trigger
 where tgrelid = 'public.orders'::regclass
   and not tgisinternal
 order by tgname;

select proname, prosecdef
  from pg_proc
 where proname in ('complete_order_by_pickup_token', 'prevent_terminal_order_regression')
 order by proname;
