-- =============================================================================
--  BreakQ — Order status state machine
--
--  RLS decides WHO may update an order. Nothing decided WHICH change is legal,
--  so a vendor (or anyone with a valid token and curl) could move an order
--  backwards — Completed back to Preparing — or skip the queue entirely.
--
--  This trigger makes the status column obey one direction of travel.
--
--      Order Placed ──► Order Confirmed ──► Preparing ──► Ready for Pickup ──► Completed
--            │                 │                │                │
--            └─────────────────┴────────────────┴────────────────┴──► Cancelled
--
--  Completed and Cancelled are terminal. Nothing leaves them, ever.
--
--  One-step skips forward are allowed on purpose: shops with auto_confirm on
--  jump Order Placed -> Preparing, and that is a legitimate flow.
--
--  Safe to re-run.
-- =============================================================================

create or replace function public.enforce_order_status_transition()
returns trigger
language plpgsql
set search_path = public, extensions
as $$
declare
  v_old text := old.status;
  v_new text := new.status;
  v_ok  boolean;
begin
  if v_new is not distinct from v_old then
    return new;
  end if;

  if v_old in ('Completed', 'Cancelled') then
    raise exception 'Order % is already % and can no longer be changed.',
      coalesce(new.order_number::text, new.id), lower(v_old)
      using errcode = 'P0001';
  end if;

  v_ok := case v_old
            when 'Order Placed'     then v_new in ('Order Confirmed', 'Preparing', 'Cancelled')
            when 'Order Confirmed'  then v_new in ('Preparing', 'Ready for Pickup', 'Cancelled')
            when 'Preparing'        then v_new in ('Ready for Pickup', 'Cancelled')
            when 'Ready for Pickup' then v_new in ('Completed', 'Cancelled')
            else false
          end;

  if not v_ok then
    raise exception 'An order cannot go from "%" to "%".', v_old, v_new
      using errcode = 'P0001';
  end if;

  return new;
end;
$$;

drop trigger if exists trg_enforce_order_status_transition on public.orders;
create trigger trg_enforce_order_status_transition
  before update of status on public.orders
  for each row
  when (old.status is distinct from new.status)
  execute function public.enforce_order_status_transition();


-- =============================================================================
--  Verification. Rolls everything back — no order is modified.
--  Run the WHOLE file at once; the results table prints at the end.
-- =============================================================================

drop table if exists public.breakq_state_machine_results;
create table public.breakq_state_machine_results (
  seq int, transition text, expected text, result text, detail text
);

do $$
declare
  v_oid  text;
  v_msg  text;
  v_ok   boolean;
  v_seq  int := 0;
  t      record;
begin
  -- A real order to experiment on. Everything is rolled back afterwards.
  select o.id into v_oid
    from public.orders o
   where o.status not in ('Completed', 'Cancelled')
   order by o.created_at desc
   limit 1;

  if v_oid is null then
    insert into public.breakq_state_machine_results values
      (0, 'fixtures', '-', 'BLOCKED', 'No open order to test against. Place one and re-run.');
    return;
  end if;

  insert into public.breakq_state_machine_results values
    (0, 'fixtures', '-', 'INFO', 'testing against order ' || v_oid);

  for t in
    select * from (values
      ('Order Placed',     'Order Confirmed',  'allow'),
      ('Order Placed',     'Preparing',        'allow'),
      ('Order Placed',     'Cancelled',        'allow'),
      ('Preparing',        'Ready for Pickup', 'allow'),
      ('Ready for Pickup', 'Completed',        'allow'),
      ('Preparing',        'Order Placed',     'block'),
      ('Completed',        'Preparing',        'block'),
      ('Cancelled',        'Order Placed',     'block'),
      ('Order Placed',     'Completed',        'block'),
      ('Order Placed',     'Ready for Pickup', 'block')
    ) as v(from_status, to_status, expected)
  loop
    v_seq := v_seq + 1;
    v_msg := null;
    v_ok  := false;

    begin
      -- Put the order into the "from" state without tripping the guard, then
      -- attempt the real transition through it.
      alter table public.orders disable trigger trg_enforce_order_status_transition;
      update public.orders set status = t.from_status where id = v_oid;
      alter table public.orders enable trigger trg_enforce_order_status_transition;

      update public.orders set status = t.to_status where id = v_oid;
      v_ok := true;
      raise exception using errcode = 'ZZ001', message = 'rollback';
    exception
      when sqlstate 'ZZ001' then null;
      when others then v_msg := sqlerrm;
    end;

    insert into public.breakq_state_machine_results values (
      v_seq,
      t.from_status || ' -> ' || t.to_status,
      t.expected,
      case
        when t.expected = 'allow' and v_ok      then 'PASS'
        when t.expected = 'block' and not v_ok  then 'PASS'
        else 'FAIL'
      end,
      coalesce(v_msg, 'accepted'));
  end loop;
end $$;

select seq, transition, expected, result, detail
  from public.breakq_state_machine_results
 order by seq;

-- Tidy up when you are done:  drop table public.breakq_state_machine_results;
