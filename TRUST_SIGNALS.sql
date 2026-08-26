-- ============================================================================
--  BreakQ — Customer Trust Signals (run once in Supabase SQL Editor)
--
--  1. shop_ratings table + the aggregate trigger the app already assumed existed
--  2. shops.years_in_business
--  3. Honest rating defaults (no more phantom 4.5 stars on brand-new shops)
--  4. products.stock_qty + realtime publication so availability badges are live
--
--  Safe to re-run.
-- ============================================================================

-- 1. SHOP RATINGS -------------------------------------------------------------
create table if not exists public.shop_ratings (
  id          uuid        primary key default gen_random_uuid(),
  shop_id     text        not null references public.shops(id) on delete cascade,
  order_id    text        not null,
  customer_id uuid        not null references auth.users(id) on delete cascade,
  rating      int         not null check (rating between 1 and 5),
  review      text        default '',
  created_at  timestamptz not null default now(),
  -- One rating per order. This is the whole anti-abuse story: a customer can
  -- only rate a shop they actually completed an order with, once.
  unique (order_id)
);

create index if not exists idx_shop_ratings_shop on public.shop_ratings(shop_id);
create index if not exists idx_shop_ratings_customer on public.shop_ratings(customer_id);

alter table public.shop_ratings enable row level security;

-- Anyone can read aggregate reviews.
drop policy if exists "read shop ratings" on public.shop_ratings;
create policy "read shop ratings" on public.shop_ratings
  for select using (true);

-- A customer may only insert a rating for THEIR OWN completed order at THAT shop.
-- Enforced in SQL so a tampered client can't post ratings for shops it never used.
drop policy if exists "rate only completed own orders" on public.shop_ratings;
create policy "rate only completed own orders" on public.shop_ratings
  for insert to authenticated
  with check (
    customer_id = auth.uid()
    and exists (
      select 1 from public.orders o
      where o.id = shop_ratings.order_id
        and o.user_id = auth.uid()
        and o.shop_id = shop_ratings.shop_id
        and o.status = 'Completed'
    )
  );

-- 2. SHOP COLUMNS -------------------------------------------------------------
alter table public.shops
  add column if not exists years_in_business int not null default 0,
  add column if not exists avg_rating   numeric(3,2) not null default 0,
  add column if not exists rating_count int          not null default 0;

-- 3. KILL PHANTOM RATINGS -----------------------------------------------------
-- Any shop carrying a seeded score with zero real reviews gets reset to 0, so
-- the app can honestly render "New shop".
update public.shops s
   set avg_rating = 0, rating_count = 0
 where not exists (select 1 from public.shop_ratings r where r.shop_id = s.id);

-- 4. AGGREGATE TRIGGER --------------------------------------------------------
-- GroceryViewModel.rateShop() bumps the local average optimistically and its
-- comment claims "the server trigger keeps DB truth" — this is that trigger.
create or replace function public.refresh_shop_rating()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  target_shop text := coalesce(new.shop_id, old.shop_id);
begin
  update public.shops
     set avg_rating = coalesce((
           select round(avg(rating)::numeric, 2)
             from public.shop_ratings where shop_id = target_shop
         ), 0),
         rating_count = (
           select count(*) from public.shop_ratings where shop_id = target_shop
         )
   where id = target_shop;
  return null;
end;
$$;

drop trigger if exists shop_ratings_aggregate on public.shop_ratings;
create trigger shop_ratings_aggregate
  after insert or update or delete on public.shop_ratings
  for each row execute function public.refresh_shop_rating();

-- Backfill for any ratings that already exist.
update public.shops s
   set avg_rating = coalesce((
         select round(avg(rating)::numeric, 2) from public.shop_ratings r where r.shop_id = s.id
       ), 0),
       rating_count = (
         select count(*) from public.shop_ratings r where r.shop_id = s.id
       );

-- 5. PRODUCT STOCK ------------------------------------------------------------
-- Deliberately NULLABLE. Three distinct states the customer badge relies on:
--   NULL -> vendor never declared a count      -> "Call to Confirm"
--   0    -> vendor says it's genuinely sold out -> "Out of Stock"
--   1-5  -> "Low Stock",  >5 -> "In Stock"
-- A NOT NULL DEFAULT 0 would collapse "unknown" and "sold out" into one value.
alter table public.products
  add column if not exists stock_qty int;

-- If an earlier migration created it as NOT NULL DEFAULT 0, relax it and treat
-- those rows as untracked rather than as false "sold out" claims.
do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'products'
      and column_name = 'stock_qty' and is_nullable = 'NO'
  ) then
    alter table public.products alter column stock_qty drop not null;
    alter table public.products alter column stock_qty drop default;
    update public.products set stock_qty = null where stock_qty = 0;
  end if;
end $$;

-- 6. REALTIME ON PRODUCTS -----------------------------------------------------
-- Lets a customer's availability badge update the moment a shopkeeper flips
-- the switch, without a manual refresh.
alter table public.products replica identity full;

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and tablename = 'products'
  ) then
    alter publication supabase_realtime add table public.products;
  end if;
end $$;

-- ============================================================================
-- VERIFY:
--   select id, name, years_in_business, avg_rating, rating_count from public.shops;
--   select id, name, in_stock, stock_qty from public.products limit 20;
--   select tablename from pg_publication_tables where pubname='supabase_realtime';
-- ============================================================================
