-- =====================================================================
-- ORDER_ITEMS_MIGRATION.sql
-- Adds a proper relational order_items table so the vendor dashboard can
-- show the exact product breakdown of every incoming order.
--
-- Apply once in Supabase SQL editor. Idempotent — safe to re-run.
-- =====================================================================

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

-- =====================================================================
-- Done. Verify with:
--   select * from public.order_items order by created_at desc limit 5;
-- =====================================================================
