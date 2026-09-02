-- =====================================================================
-- ORDER_PICKUP_MIGRATION.sql
-- Adds:
--   * order_number  — short human-friendly per-shop counter (1001, 1002, …)
--   * pickup_token  — opaque secret embedded in the customer's QR
--   * pickup_token_consumed_at  — set when the vendor completes the pickup
--   * BEFORE INSERT trigger that fills the two fields for every new order
--   * Backfill so existing orders get valid values on first run
--
-- Apply once in Supabase SQL editor. Idempotent — safe to re-run.
-- Requires ORDER_ITEMS_MIGRATION.sql to have been applied first.
-- =====================================================================

-- pickup_token relies on gen_random_bytes; guarantee pgcrypto is loaded
-- (enabled by default on Supabase, but keeping the guard makes this
-- migration portable to a plain Postgres install).
create extension if not exists pgcrypto;

-- 1. Columns -----------------------------------------------------------
alter table public.orders add column if not exists order_number             integer;
alter table public.orders add column if not exists pickup_token             text;
alter table public.orders add column if not exists pickup_token_consumed_at timestamptz;

-- Each shop's order number sequence is independent, so #1042 at Yash Kirana
-- and #1042 at Alex Store never collide with each other.
create unique index if not exists uq_orders_shop_number
  on public.orders(shop_id, order_number)
  where shop_id is not null and order_number is not null;

-- Pickup token is globally unique (opaque and long enough not to collide).
create unique index if not exists uq_orders_pickup_token
  on public.orders(pickup_token)
  where pickup_token is not null;

-- 2. Trigger: auto-assign order_number + pickup_token on insert ---------
create or replace function public.assign_order_pickup_fields()
returns trigger
language plpgsql
security definer
as $$
begin
  if new.pickup_token is null then
    -- pgcrypto's gen_random_bytes → base64; 24 bytes = 32 chars after b64.
    -- Long enough to be non-guessable, short enough for a compact QR.
    new.pickup_token :=
      translate(encode(gen_random_bytes(24), 'base64'), '+/=', '-_');
  end if;

  if new.order_number is null and new.shop_id is not null then
    -- Serialise MAX(order_number)+1 lookup per shop so two concurrent
    -- inserts in the same shop can't allocate the same number. Advisory
    -- lock is released at transaction commit.
    perform pg_advisory_xact_lock(hashtext(new.shop_id));
    new.order_number := coalesce(
      (select max(order_number) + 1
         from public.orders
        where shop_id = new.shop_id),
      1001
    );
  end if;

  return new;
end;
$$;

drop trigger if exists trg_assign_order_pickup_fields on public.orders;
create trigger trg_assign_order_pickup_fields
  before insert on public.orders
  for each row execute function public.assign_order_pickup_fields();

-- 3. Backfill for historical rows --------------------------------------
-- Give every existing order without an order_number a per-shop sequence in
-- insertion (created_at) order so vendors' history stays chronologically
-- sensible. Then fill any missing pickup_tokens.
with numbered as (
  select id,
         shop_id,
         1000 + row_number() over (
           partition by shop_id order by created_at, id
         ) as new_number
  from public.orders
  where shop_id is not null
    and order_number is null
)
update public.orders o
   set order_number = n.new_number
  from numbered n
 where o.id = n.id;

update public.orders
   set pickup_token =
     translate(encode(gen_random_bytes(24), 'base64'), '+/=', '-_')
 where pickup_token is null;

-- 4. RPC: verify + complete a pickup by token --------------------------
-- The vendor scans the customer QR (= pickup_token). This function:
--   * finds the order by token
--   * enforces "vendor owns the shop the order belongs to" via auth.uid()
--   * refuses to complete an already-completed or cancelled order
--   * refuses to complete an order that isn't ready yet
--   * atomically flips status → 'Completed' and stamps consumed_at
-- Returns the completed order id so the client can navigate to its details.
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
  v public.orders;
  s public.shops;
begin
  select * into v from public.orders where pickup_token = p_token limit 1;
  if v.id is null then
    raise exception 'INVALID_TOKEN' using errcode = 'P0001';
  end if;

  select * into s from public.shops where id = v.shop_id;
  if s.id is null or s.owner_id is null or s.owner_id <> auth.uid() then
    raise exception 'NOT_YOUR_SHOP' using errcode = 'P0002';
  end if;

  if v.status = 'Cancelled' then
    raise exception 'ORDER_CANCELLED' using errcode = 'P0003';
  end if;
  if v.status = 'Completed' then
    raise exception 'ALREADY_COMPLETED' using errcode = 'P0004';
  end if;
  if v.status <> 'Ready for Pickup' then
    raise exception 'NOT_READY_FOR_PICKUP' using errcode = 'P0005';
  end if;

  update public.orders
     set status = 'Completed',
         pickup_token_consumed_at = now()
   where id = v.id
   returning * into v;

  return query select
    v.id, v.order_number, v.status, v.customer_name, v.total_amount;
end;
$$;

grant execute on function public.complete_order_by_pickup_token(text) to authenticated;

-- 5. RPC: look up an order for the vendor by order_number --------------
-- Manual alternative to scanning. Only returns rows for the caller's own
-- shop, so a vendor can't fish for another shop's orders.
create or replace function public.find_shop_order_by_number(p_order_number integer)
returns table (
  order_id       text,
  order_number   integer,
  status         text,
  customer_name  text,
  total_amount   integer,
  items_json     jsonb,
  pickup_token   text
)
language plpgsql
security definer
as $$
declare
  s public.shops;
begin
  select * into s from public.shops where owner_id = auth.uid() limit 1;
  if s.id is null then
    raise exception 'NO_SHOP_FOR_CALLER' using errcode = 'P0006';
  end if;

  return query
    select o.id, o.order_number, o.status, o.customer_name,
           o.total_amount, o.items_json, o.pickup_token
      from public.orders o
     where o.shop_id = s.id
       and o.order_number = p_order_number
     limit 1;
end;
$$;

grant execute on function public.find_shop_order_by_number(integer) to authenticated;

-- =====================================================================
-- Done. Verify with:
--   select id, shop_id, order_number, pickup_token, status
--     from public.orders order by created_at desc limit 5;
-- =====================================================================
