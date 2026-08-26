-- ============================================================================
--  BreakQ — Revenue Model v2 (run once in Supabase SQL Editor)
--
--  WHAT CHANGED vs SUBSCRIPTION_MIGRATION.sql:
--    * Catalog size is NO LONGER the paywall. Free gets 500 items.
--    * Paywall is now ANALYTICS + PLACEMENT + BRANDING.
--    * New tiers: free / founding (₹99, 3-month intro) / advance (₹199) / pro (₹499)
--    * Razorpay replaces the manual WhatsApp upgrade flow.
--
--  Safe to re-run.
-- ============================================================================

-- 1. FEATURE-FLAG COLUMNS ON THE TIER CATALOG ---------------------------------
alter table public.subscription_tiers
  add column if not exists has_basic_analytics    boolean not null default false,
  add column if not exists has_full_analytics     boolean not null default false,
  add column if not exists has_priority_placement boolean not null default false,
  add column if not exists has_top_boost          boolean not null default false,
  add column if not exists hide_breakq_branding   boolean not null default false,
  add column if not exists has_whatsapp_alerts    boolean not null default false,
  add column if not exists has_multi_staff        boolean not null default false,
  add column if not exists has_competitor_pricing boolean not null default false,
  -- Founding Vendor is a limited-time intro offer, not permanent pricing.
  add column if not exists is_limited_time        boolean not null default false,
  add column if not exists offer_ends_at          timestamptz,
  add column if not exists tagline                text;

-- 2. RESET + RESEED THE CATALOG ----------------------------------------------
-- Old ids (starter / standard) are retired. Any shop still pointing at them is
-- migrated to the closest new tier in step 3 BEFORE we delete the rows.
update public.subscription_tiers set is_active = false where id in ('starter', 'standard');

insert into public.subscription_tiers (
  id, display_name, price_rupees, item_cap, priority_rank,
  can_promote, promote_daily_cap_rupees, commission_percent,
  has_basic_analytics, has_full_analytics, has_priority_placement, has_top_boost,
  hide_breakq_branding, has_whatsapp_alerts, has_multi_staff, has_competitor_pricing,
  is_limited_time, offer_ends_at, tagline, features, is_active
) values
  (
    'free', 'Free', 0, 500, 0,
    false, 0, 0.00,
    false, false, false, false,
    false, false, false, false,
    false, null,
    'Get listed and start taking orders',
    '["Up to 500 catalog items","Standard listing order","Order notifications","BreakQ branding on your storefront"]'::jsonb,
    true
  ),
  (
    'founding', 'Founding Vendor', 99, -1, 1,
    true, 50, 0.00,
    true, false, false, false,
    true, false, false, false,
    true, (now() + interval '3 months'),
    'Limited-time launch price for our first vendors',
    '["Unlimited catalog","Views + top searched products","Daily order count","No BreakQ branding","Locked at ₹99 for 3 months"]'::jsonb,
    true
  ),
  (
    'advance', 'Advance', 199, -1, 2,
    true, 100, 0.00,
    true, false, true, false,
    true, false, false, false,
    false, null,
    'Know what your customers are looking for',
    '["Unlimited catalog","Shop + product view counts","Top 5 searched/viewed products","Daily order count","Priority placement in your category","No BreakQ branding"]'::jsonb,
    true
  ),
  (
    'pro', 'Pro', 499, -1, 3,
    true, 200, 0.00,
    true, true, true, true,
    true, true, true, true,
    false, null,
    'Everything you need to outsell your street',
    '["Everything in Advance","Footfall + traffic source insights","Repeat customer percentage","Competitor price visibility","Top-of-category boost","Banner / boost slot eligibility","WhatsApp order alerts","Multi-staff login"]'::jsonb,
    true
  )
on conflict (id) do update set
  display_name           = excluded.display_name,
  price_rupees           = excluded.price_rupees,
  item_cap               = excluded.item_cap,
  priority_rank          = excluded.priority_rank,
  can_promote            = excluded.can_promote,
  promote_daily_cap_rupees = excluded.promote_daily_cap_rupees,
  has_basic_analytics    = excluded.has_basic_analytics,
  has_full_analytics     = excluded.has_full_analytics,
  has_priority_placement = excluded.has_priority_placement,
  has_top_boost          = excluded.has_top_boost,
  hide_breakq_branding   = excluded.hide_breakq_branding,
  has_whatsapp_alerts    = excluded.has_whatsapp_alerts,
  has_multi_staff        = excluded.has_multi_staff,
  has_competitor_pricing = excluded.has_competitor_pricing,
  is_limited_time        = excluded.is_limited_time,
  offer_ends_at          = excluded.offer_ends_at,
  tagline                = excluded.tagline,
  features               = excluded.features,
  is_active              = excluded.is_active;

-- 3. MIGRATE ANY SHOP STILL ON A RETIRED TIER --------------------------------
update public.vendor_subscriptions set tier_id = 'founding' where tier_id = 'starter';
update public.vendor_subscriptions set tier_id = 'advance'  where tier_id = 'standard';

-- 4. RAZORPAY PAYMENT LEDGER --------------------------------------------------
-- Written by the verify-razorpay-payment Edge Function after signature checks
-- pass. The app NEVER writes here (RLS below enforces that).
create table if not exists public.subscription_payments (
  id                  uuid        primary key default gen_random_uuid(),
  shop_id             text        not null references public.shops(id) on delete cascade,
  tier_id             text        not null references public.subscription_tiers(id),
  razorpay_order_id   text        not null,
  razorpay_payment_id text,
  razorpay_signature  text,
  amount_rupees       int         not null,
  status              text        not null default 'created', -- created | paid | failed | refunded
  created_at          timestamptz not null default now(),
  verified_at         timestamptz,
  unique (razorpay_order_id)
);

create index if not exists idx_sub_payments_shop
  on public.subscription_payments(shop_id, created_at desc);

alter table public.subscription_payments enable row level security;

-- Vendor can read their own payment history; nobody can write from the client.
drop policy if exists "vendor reads own payments" on public.subscription_payments;
create policy "vendor reads own payments" on public.subscription_payments
  for select using (
    exists (select 1 from public.shops s
            where s.id = subscription_payments.shop_id
              and s.owner_id = auth.uid())
    or exists (select 1 from public.profiles p
               where p.id = auth.uid() and p.role in ('admin','super_admin'))
  );

-- 5. RELAX THE ITEM-CAP TRIGGER ----------------------------------------------
-- Free is now 500 items, paid tiers are unlimited. The trigger stays as a
-- backstop but will effectively never fire for a real kirana shop.
create or replace function public.enforce_tier_item_cap()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  cap        int;
  current_ct int;
begin
  select st.item_cap into cap
  from public.vendor_subscriptions vs
  join public.subscription_tiers st on st.id = vs.tier_id
  where vs.shop_id = new.shop_id and vs.status = 'active'
  limit 1;

  if cap is null then cap := 500; end if;
  if cap = -1 then return new; end if;

  select count(*) into current_ct from public.products where shop_id = new.shop_id;

  if current_ct >= cap then
    raise exception 'TIER_CAP_REACHED: shop % has hit its % item limit', new.shop_id, cap
      using errcode = 'P0001';
  end if;

  return new;
end;
$$;

-- 6. VENDOR ANALYTICS COUNTERS -----------------------------------------------
-- Powers the "views count" / "top searched products" features that Advance and
-- Pro pay for. Incremented from the customer app; read only by the shop owner.
create table if not exists public.shop_view_events (
  id          bigserial   primary key,
  shop_id     text        not null references public.shops(id) on delete cascade,
  product_id  text,
  event_type  text        not null, -- 'shop_view' | 'product_view' | 'search_hit'
  search_term text,
  viewer_id   uuid,
  created_at  timestamptz not null default now()
);

create index if not exists idx_view_events_shop_time
  on public.shop_view_events(shop_id, created_at desc);
create index if not exists idx_view_events_product
  on public.shop_view_events(shop_id, product_id) where product_id is not null;

alter table public.shop_view_events enable row level security;

-- Any signed-in customer can log a view; only the shop owner (or admin) reads.
drop policy if exists "anyone logs a view" on public.shop_view_events;
create policy "anyone logs a view" on public.shop_view_events
  for insert to authenticated with check (true);

drop policy if exists "owner reads own analytics" on public.shop_view_events;
create policy "owner reads own analytics" on public.shop_view_events
  for select using (
    exists (select 1 from public.shops s
            where s.id = shop_view_events.shop_id
              and s.owner_id = auth.uid())
    or exists (select 1 from public.profiles p
               where p.id = auth.uid() and p.role in ('admin','super_admin'))
  );

-- 7. SHOP DOCUMENT COLUMNS (from Round 7.2) ----------------------------------
alter table public.shops
  add column if not exists image_url          text,
  add column if not exists business_proof_url text;

-- ============================================================================
-- VERIFY:
--   select id, display_name, price_rupees, item_cap, has_basic_analytics,
--          has_full_analytics, is_limited_time
--     from public.subscription_tiers where is_active order by price_rupees;
--   select shop_id, tier_id, status from public.vendor_subscriptions;
-- ============================================================================
