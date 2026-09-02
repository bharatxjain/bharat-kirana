-- =====================================================================
-- ORDER_ITEMS_MIGRATION.sql
-- Adds a proper relational order_items table so the vendor dashboard can
-- show the exact product breakdown of every incoming order.
--
-- Apply once in Supabase SQL editor. Idempotent — safe to re-run.
-- =====================================================================

-- 0. Belt-and-suspenders: a JSONB snapshot on the orders row itself, so a
-- vendor / customer still sees line items even if the order_items relational
-- write is momentarily blocked (bad RLS, unapplied migration, network hiccup
-- between the two POSTs). The relational table is still the source of truth
-- for analytics and joins; this column is a client-side rendering fallback.
alter table public.orders
  add column if not exists items_json jsonb;

-- 1. Table -------------------------------------------------------------
create table if not exists public.order_items (
  id            bigserial primary key,
  order_id      text        not null references public.orders(id) on delete cascade,
  product_id    text        not null,
  -- Denormalised snapshot fields. The order stays readable even if the
  -- underlying product is later renamed, deleted, or reprice'd.
  product_name  text        not null,
  brand         text        default '',
  image_url     text        default '',
  weight_label  text        not null,        -- e.g. "500g", "1kg", "each"
  unit_price    integer     not null check (unit_price >= 0),
  quantity      integer     not null check (quantity > 0),
  line_total    integer     not null check (line_total >= 0),
  created_at    timestamptz not null default now()
);

-- 1a. Backwards-compat: if an earlier attempt created this table with a
-- narrower schema, add the missing snapshot columns idempotently. Without
-- this the backfill + trigger below would fail with "column does not exist"
-- because `create table if not exists` skips altering an existing table.
alter table public.order_items add column if not exists product_id    text;
alter table public.order_items add column if not exists product_name  text;
alter table public.order_items add column if not exists brand         text        default '';
alter table public.order_items add column if not exists image_url     text        default '';
alter table public.order_items add column if not exists weight_label  text;
alter table public.order_items add column if not exists unit_price    integer;
alter table public.order_items add column if not exists quantity      integer;
alter table public.order_items add column if not exists line_total    integer;
alter table public.order_items add column if not exists created_at    timestamptz not null default now();

create index if not exists idx_order_items_order_id
  on public.order_items(order_id);

-- 2. RLS ---------------------------------------------------------------
alter table public.order_items enable row level security;

-- Customers can read the line items of orders they placed.
drop policy if exists "customers read own order_items" on public.order_items;
create policy "customers read own order_items"
  on public.order_items
  for select
  using (
    exists (
      select 1 from public.orders o
      where o.id = order_items.order_id
        and o.user_id = auth.uid()
    )
  );

-- Vendors can read the line items of any order for a shop they own.
drop policy if exists "vendors read shop order_items" on public.order_items;
create policy "vendors read shop order_items"
  on public.order_items
  for select
  using (
    exists (
      select 1
      from public.orders o
      join public.shops s on s.id = o.shop_id
      where o.id = order_items.order_id
        and s.owner_id = auth.uid()
    )
  );

-- Authenticated users can insert line items only for orders they just placed.
drop policy if exists "customers insert own order_items" on public.order_items;
create policy "customers insert own order_items"
  on public.order_items
  for insert
  with check (
    exists (
      select 1 from public.orders o
      where o.id = order_items.order_id
        and o.user_id = auth.uid()
    )
  );

-- Admin passthrough (service_role always bypasses RLS).

-- 3. Realtime ----------------------------------------------------------
-- Optional: expose order_items to the realtime publication so the vendor UI
-- can react instantly. Silently no-ops if already published.
do $$
begin
  begin
    execute 'alter publication supabase_realtime add table public.order_items';
  exception when duplicate_object then null;
  end;
end $$;

-- 4. Trigger: keep orders.items_json in sync with the relational rows -----
-- This is the RECONCILER. The client used to write both the parent row's
-- items_json AND a separate order_items batch — a partial failure between
-- those two HTTP calls left orders with either NULL items_json or empty
-- children. Now the database itself regenerates items_json from order_items
-- on every insert / update / delete, so the two representations can never
-- drift apart again. This is why some historical Bharat orders showed "0
-- items": their parent row had items_json = NULL because the client build
-- that placed them predates the items_json column.
create or replace function public.sync_order_items_json()
returns trigger
language plpgsql
security definer
as $$
declare
  target_order text := coalesce(new.order_id, old.order_id);
begin
  update public.orders
     set items_json = coalesce((
       select jsonb_agg(
         jsonb_build_object(
           'product_id',   product_id,
           'product_name', product_name,
           'brand',        brand,
           'image_url',    image_url,
           'weight_label', weight_label,
           'unit_price',   unit_price,
           'quantity',     quantity,
           'line_total',   line_total
         )
         order by id
       )
       from public.order_items
       where order_id = target_order
     ), '[]'::jsonb)
   where id = target_order;
  return coalesce(new, old);
end;
$$;

drop trigger if exists trg_sync_order_items_json_ins on public.order_items;
create trigger trg_sync_order_items_json_ins
  after insert on public.order_items
  for each row execute function public.sync_order_items_json();

drop trigger if exists trg_sync_order_items_json_upd on public.order_items;
create trigger trg_sync_order_items_json_upd
  after update on public.order_items
  for each row execute function public.sync_order_items_json();

drop trigger if exists trg_sync_order_items_json_del on public.order_items;
create trigger trg_sync_order_items_json_del
  after delete on public.order_items
  for each row execute function public.sync_order_items_json();

-- 5. One-shot backfill for historical rows -------------------------------
-- Any orders row that never got items_json populated (older builds, partial
-- writes) picks up the JSONB snapshot from its existing relational rows.
-- Orders with NO relational rows either way stay NULL — those are legitimately
-- lost (nothing to reconstruct from) and will render "Item details not synced".
update public.orders o
   set items_json = coalesce((
     select jsonb_agg(
       jsonb_build_object(
         'product_id',   oi.product_id,
         'product_name', oi.product_name,
         'brand',        oi.brand,
         'image_url',    oi.image_url,
         'weight_label', oi.weight_label,
         'unit_price',   oi.unit_price,
         'quantity',     oi.quantity,
         'line_total',   oi.line_total
       )
       order by oi.id
     )
     from public.order_items oi
     where oi.order_id = o.id
   ), o.items_json)
 where o.items_json is null;

-- =====================================================================
-- Done. Verify with:
--   select id, jsonb_array_length(items_json) as item_count
--     from public.orders order by created_at desc limit 10;
-- =====================================================================
