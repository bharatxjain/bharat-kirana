-- ============================================================================
--  BreakQ — Subscription Tier Foundation (run once in Supabase SQL Editor)
--  Adds: subscription_tiers, vendor_subscriptions, promoted_placements
--  Also adds dormant commission columns on orders + shops (activate later)
--  Safe to re-run: uses IF NOT EXISTS + ON CONFLICT DO NOTHING everywhere.
-- ============================================================================

-- 1. TIER CATALOG ------------------------------------------------------------
create table if not exists public.subscription_tiers (
  id            text primary key,               -- 'free' | 'starter' | 'standard' | 'pro'
  display_name  text        not null,
  price_rupees  int         not null default 0, -- monthly
  item_cap      int         not null,           -- -1 = unlimited
  priority_rank int         not null default 0, -- higher = surfaced first
  can_promote   boolean     not null default false,
  promote_daily_cap_rupees int not null default 0,
  -- Reserved for post-1000-vendor launch. Zero today.
  commission_percent numeric(5,2) not null default 0.00,
  features      jsonb       not null default '[]'::jsonb,
  is_active     boolean     not null default true,
  created_at    timestamptz not null default now()
);

insert into public.subscription_tiers
  (id, display_name, price_rupees, item_cap, priority_rank, can_promote, promote_daily_cap_rupees, commission_percent, features)
values
  ('free',     'Free',     0,   10, 0, false,   0, 0.00,
    '["10 catalog items", "Basic listing", "Order notifications"]'::jsonb),
  ('starter',  'Starter',  99,  50, 1, true,   50, 0.00,
    '["50 catalog items", "Promoted placement (₹50/day)", "WhatsApp support"]'::jsonb),
  ('standard', 'Standard', 299, -1, 2, true,  100, 0.00,
    '["Unlimited catalog", "Priority ranking", "Promoted placement (₹100/day)", "Priority support"]'::jsonb),
  ('pro',      'Pro',      599, -1, 3, true,  200, 0.00,
    '["Unlimited catalog", "Top ranking + featured slot", "Advanced analytics", "Promoted placement (₹200/day)", "Dedicated support"]'::jsonb)
on conflict (id) do nothing;


-- 2. VENDOR SUBSCRIPTIONS ----------------------------------------------------
create table if not exists public.vendor_subscriptions (
  id            uuid        primary key default gen_random_uuid(),
  shop_id       text        not null references public.shops(id) on delete cascade,
  tier_id       text        not null references public.subscription_tiers(id),
  status        text        not null default 'active', -- 'active' | 'expired' | 'cancelled' | 'pending_payment'
  started_at    timestamptz not null default now(),
  expires_at    timestamptz,                            -- null for Free (never expires)
  payment_ref   text,                                   -- razorpay_order_id later
  amount_paid_rupees int  not null default 0,
  -- Freeze commission at time of activation so grandfathering "just works" later.
  commission_locked_at_percent numeric(5,2) not null default 0.00,
  created_at    timestamptz not null default now(),
  unique (shop_id, status) deferrable initially deferred
);

create index if not exists idx_vendor_subs_shop
  on public.vendor_subscriptions(shop_id) where status = 'active';


-- 3. PROMOTED PLACEMENTS -----------------------------------------------------
create table if not exists public.promoted_placements (
  id           uuid        primary key default gen_random_uuid(),
  shop_id      text        not null references public.shops(id) on delete cascade,
  daily_budget_rupees int  not null,
  active_from  timestamptz not null default now(),
  active_to    timestamptz not null,
  is_active    boolean     not null default true,
  total_charged_rupees int not null default 0,
  payment_ref  text,
  created_at   timestamptz not null default now()
);

create index if not exists idx_promoted_active
  on public.promoted_placements(shop_id, active_to) where is_active = true;


-- 4. COMMISSION-READY COLUMNS ON ORDERS (dormant now, populated later) --------
alter table public.orders
  add column if not exists platform_fee_rupees   int          not null default 0,
  add column if not exists commission_rupees     int          not null default 0,
  add column if not exists commission_rate_at_order numeric(5,2) not null default 0.00;

-- 5. GRANDFATHER FLAG ON SHOPS ------------------------------------------------
-- shops.commission_enabled_at = null means "still on subscription-only pricing".
-- When commission launches (post 1000 vendors), we stamp new shops with a date;
-- rows still null stay grandfathered for 3-6 months.
alter table public.shops
  add column if not exists commission_enabled_at timestamptz;


-- 6. AUTO-ENROLL EVERY EXISTING + FUTURE SHOP ON FREE TIER --------------------
insert into public.vendor_subscriptions (shop_id, tier_id, status, started_at)
select id, 'free', 'active', now()
from public.shops
where not exists (
  select 1 from public.vendor_subscriptions vs
  where vs.shop_id = shops.id and vs.status = 'active'
)
on conflict do nothing;

-- Trigger: any new shop → auto-create a Free-tier active subscription row.
create or replace function public.auto_free_tier_on_shop_insert()
returns trigger language plpgsql as $$
begin
  insert into public.vendor_subscriptions (shop_id, tier_id, status)
  values (new.id, 'free', 'active')
  on conflict do nothing;
  return new;
end;
$$;

drop trigger if exists shops_free_tier_trigger on public.shops;
create trigger shops_free_tier_trigger
  after insert on public.shops
  for each row execute function public.auto_free_tier_on_shop_insert();


-- 7. RLS POLICIES ------------------------------------------------------------
alter table public.subscription_tiers   enable row level security;
alter table public.vendor_subscriptions enable row level security;
alter table public.promoted_placements  enable row level security;

-- subscription_tiers: everyone can read (public catalog).
drop policy if exists "read tiers" on public.subscription_tiers;
create policy "read tiers" on public.subscription_tiers
  for select using (true);

-- vendor_subscriptions: vendor sees their own; admins see all.
drop policy if exists "vendor reads own sub" on public.vendor_subscriptions;
create policy "vendor reads own sub" on public.vendor_subscriptions
  for select using (
    exists (select 1 from public.shops s
            where s.id = vendor_subscriptions.shop_id
              and s.owner_id = auth.uid())
    or exists (select 1 from public.profiles p
               where p.id = auth.uid() and p.role in ('admin','super_admin'))
  );

-- Only admin (or service role via Edge Function later) can INSERT/UPDATE subs
-- so vendors can't self-upgrade without paying.
drop policy if exists "admin manages subs" on public.vendor_subscriptions;
create policy "admin manages subs" on public.vendor_subscriptions
  for all using (
    exists (select 1 from public.profiles p
            where p.id = auth.uid() and p.role in ('admin','super_admin'))
  );

-- promoted_placements: vendor sees + creates their own; admin sees all.
drop policy if exists "vendor reads own placement" on public.promoted_placements;
create policy "vendor reads own placement" on public.promoted_placements
  for select using (
    exists (select 1 from public.shops s
            where s.id = promoted_placements.shop_id
              and s.owner_id = auth.uid())
    or exists (select 1 from public.profiles p
               where p.id = auth.uid() and p.role in ('admin','super_admin'))
  );


-- 8. ENFORCE ITEM CAP ON PRODUCT INSERT --------------------------------------
-- Prevents a Free-tier shop from ever having >10 rows, Starter >50, etc.
-- Raises a friendly error the app can catch.
create or replace function public.enforce_tier_item_cap()
returns trigger language plpgsql as $$
declare
  cap        int;
  current_ct int;
begin
  select st.item_cap into cap
  from public.vendor_subscriptions vs
  join public.subscription_tiers st on st.id = vs.tier_id
  where vs.shop_id = new.shop_id and vs.status = 'active'
  limit 1;

  -- If no active subscription (shouldn't happen after step 6) → treat as Free.
  if cap is null then cap := 10; end if;

  -- -1 means unlimited.
  if cap = -1 then return new; end if;

  select count(*) into current_ct
  from public.products where shop_id = new.shop_id;

  if current_ct >= cap then
    raise exception 'TIER_CAP_REACHED: shop % has hit its % item limit — upgrade plan to add more', new.shop_id, cap
      using errcode = 'P0001';
  end if;

  return new;
end;
$$;

drop trigger if exists products_tier_cap_trigger on public.products;
create trigger products_tier_cap_trigger
  before insert on public.products
  for each row execute function public.enforce_tier_item_cap();


-- ============================================================================
-- DONE. Verify with:
--   select * from public.subscription_tiers order by price_rupees;
--   select shop_id, tier_id, status from public.vendor_subscriptions;
-- ============================================================================
