-- =============================================================================
--  BreakQ — complete_order_by_pickup_token: qualify `status` refs
--
--  The RPC declares `RETURNS TABLE (..., status text, ...)`. Inside a plpgsql
--  function whose RETURNS TABLE names a column called `status`, any bare
--  reference to `status` in a query (e.g. the UPDATE's WHERE clause) is
--  ambiguous between the OUT column and `public.orders.status`. Postgres
--  raises "column reference status is ambiguous" and the pickup fails.
--
--  Fix: alias the target table so every mention of `status` inside the
--  UPDATE is unambiguously `public.orders.status`. SET-column names on the
--  left of the assignment always refer to the target table and don't need
--  qualification. Same for pre-flight IF checks and the post-UPDATE reread.
--
--  Safe to re-run.
-- =============================================================================

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
  select * into v from public.orders o where o.pickup_token = p_token limit 1;
  if v.id is null then
    raise exception 'INVALID_TOKEN' using errcode = 'P0001';
  end if;

  select * into s from public.shops o where o.id = v.shop_id;
  if s.id is null or s.owner_id is null or s.owner_id <> auth.uid() then
    raise exception 'NOT_YOUR_SHOP' using errcode = 'P0002';
  end if;

  if v.status = 'Cancelled' then
    raise exception 'ORDER_CANCELLED' using errcode = 'P0003';
  end if;
  if v.status = 'Completed' then
    raise exception 'ALREADY_COMPLETED' using errcode = 'P0004';
  end if;

  update public.orders o
     set status                   = 'Completed',
         pickup_token_consumed_at = now()
   where o.id     = v.id
     and o.status = 'Ready for Pickup'
   returning * into v_updated;

  if v_updated.id is null then
    select * into v from public.orders o where o.id = v.id;
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
-- Verify
-- -----------------------------------------------------------------------------
select proname, prosecdef
  from pg_proc
 where proname = 'complete_order_by_pickup_token';
