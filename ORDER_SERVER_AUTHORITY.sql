-- =============================================================================
--  BreakQ — Server authority over money and stock
--
--  Replaces create_order_with_items so the SERVER decides every rupee and
--  every unit of stock. The client now only says "who I am, which shop, which
--  products, how many". Everything else is looked up here.
--
--  What this closes:
--    1. total_amount / unit_price / line_total were taken from the client
--       verbatim. A modified APK could order ₹2000 of goods for ₹1.
--    2. Stock was never checked or decremented. Two customers could both buy
--       the last unit.
--    3. promo_discount was computed on the phone and trusted. valid_from /
--       valid_to / usage_limit were never enforced anywhere.
--    4. The cart and checkout judged promo codes separately. Both now call
--       evaluate_promo(), so the cart can't promise what checkout won't give.
--
--  Safe to re-run.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. app_settings — one row, the fee numbers the SERVER bills against.
--
--    This is the only source of fees. The app's cart reads this same row, so
--    the cart preview and the charged total always agree.
-- -----------------------------------------------------------------------------
create table if not exists public.app_settings (
  id                      int primary key default 1,
  handling_fee            int not null default 10,
  min_order_free_handling int not null default 200,
  free_handling_discount  int not null default 15,
  updated_at              timestamptz not null default now(),
  constraint app_settings_single_row check (id = 1)
);

-- Seeds a fresh database with no fees. Never overwrites an existing row.
insert into public.app_settings (id, handling_fee, min_order_free_handling, free_handling_discount)
values (1, 0, 0, 0)
on conflict (id) do nothing;

alter table public.app_settings enable row level security;

drop policy if exists app_settings_read on public.app_settings;
create policy app_settings_read on public.app_settings
  for select using (true);

-- No insert/update/delete policy on purpose: only the SQL editor (service role)
-- can change pricing. A compromised client cannot move the fee.


-- -----------------------------------------------------------------------------
-- 2. promo_codes — nothing to add. The live table already carries
--    discount_percent, discount_flat_rupees, min_order_amount,
--    max_discount_rupees, valid_from, valid_until, usage_limit, active,
--    applicable_shop_id and max_uses_per_customer.
--
--    Every one of those was being ignored: the phone computed the discount and
--    the server stored whatever it was told. They are all enforced below.
--
--    There is no times_used counter, so usage is counted from public.orders.
--    The `for update` lock taken on the promo row makes that count race-safe.
-- -----------------------------------------------------------------------------


-- -----------------------------------------------------------------------------
-- 3. orders — keep the bill breakdown so a receipt can be rebuilt later
--    without re-deriving it from Remote Config values that may have changed.
-- -----------------------------------------------------------------------------
alter table public.orders add column if not exists item_total        int;
alter table public.orders add column if not exists handling_fee      int;
alter table public.orders add column if not exists handling_discount int;


-- -----------------------------------------------------------------------------
-- 3b. Shared rules. Checkout AND the cart preview call these, so there is
--     exactly one definition of "what does this cost" and "is this promo good".
-- -----------------------------------------------------------------------------

-- Price of one unit for the chosen size. NULL when the product has sizes and
-- the label matches none of them (never silently falls back to current_price).
create or replace function public.resolve_unit_price(
  p_weight_options jsonb,
  p_current_price  int,
  p_weight_label   text
)
returns int
language sql
immutable
set search_path = public, extensions
as $$
  select case
           when jsonb_array_length(coalesce(p_weight_options, '[]'::jsonb)) > 0 then
             (select (w->>'price')::int
                from jsonb_array_elements(p_weight_options) w
               where w->>'label' = coalesce(p_weight_label, '')
               limit 1)
           else p_current_price
         end;
$$;

-- Every promo rule, in one place. reason is NULL when the code applies.
-- Callers that must be race-safe (checkout) lock the promo row first.
create or replace function public.evaluate_promo(
  p_code      text,
  p_shop_id   text,
  p_items_sum int,
  p_uid       uuid
)
returns table(promo_code text, discount int, reason text, min_order_amount int)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_code  text := nullif(upper(trim(coalesce(p_code, ''))), '');
  v_promo record;
  v_raw   int;
  v_disc  int;
begin
  if v_code is null then
    return query select null::text, 0, 'NOT_FOUND'::text, 0;
    return;
  end if;

  select pc.code, pc.discount_percent, pc.discount_flat_rupees,
         pc.min_order_amount, pc.max_discount_rupees,
         pc.valid_from, pc.valid_until, pc.usage_limit, pc.active,
         pc.applicable_shop_id, pc.max_uses_per_customer
    into v_promo
    from public.promo_codes pc
   where upper(pc.code) = v_code;

  if not found then
    return query select null::text, 0, 'NOT_FOUND'::text, 0;
    return;
  end if;

  if not coalesce(v_promo.active, false) then
    return query select v_promo.code, 0, 'INACTIVE'::text, coalesce(v_promo.min_order_amount, 0);
    return;
  end if;

  if v_promo.valid_from is not null and now() < v_promo.valid_from then
    return query select v_promo.code, 0, 'NOT_STARTED'::text, coalesce(v_promo.min_order_amount, 0);
    return;
  end if;

  if v_promo.valid_until is not null and now() > v_promo.valid_until then
    return query select v_promo.code, 0, 'EXPIRED'::text, coalesce(v_promo.min_order_amount, 0);
    return;
  end if;

  if v_promo.applicable_shop_id is not null and v_promo.applicable_shop_id is distinct from p_shop_id then
    return query select v_promo.code, 0, 'WRONG_SHOP'::text, coalesce(v_promo.min_order_amount, 0);
    return;
  end if;

  if coalesce(p_items_sum, 0) < coalesce(v_promo.min_order_amount, 0) then
    return query select v_promo.code, 0, 'MIN_ORDER'::text, coalesce(v_promo.min_order_amount, 0);
    return;
  end if;

  if v_promo.usage_limit is not null and (
       select count(*) from public.orders o
        where o.promo_code = v_promo.code
          and o.status <> 'Cancelled'
     ) >= v_promo.usage_limit then
    return query select v_promo.code, 0, 'USAGE_LIMIT'::text, coalesce(v_promo.min_order_amount, 0);
    return;
  end if;

  if v_promo.max_uses_per_customer is not null and (
       select count(*) from public.orders o
        where o.promo_code = v_promo.code
          and o.user_id = p_uid
          and o.status <> 'Cancelled'
     ) >= v_promo.max_uses_per_customer then
    return query select v_promo.code, 0, 'PER_CUSTOMER_LIMIT'::text, coalesce(v_promo.min_order_amount, 0);
    return;
  end if;

  v_raw := case
             when coalesce(v_promo.discount_percent, 0) > 0
             then (coalesce(p_items_sum, 0) * v_promo.discount_percent) / 100
             else coalesce(v_promo.discount_flat_rupees, 0)
           end;

  if v_promo.max_discount_rupees is not null and v_promo.max_discount_rupees > 0 then
    v_raw := least(v_raw, v_promo.max_discount_rupees);
  end if;

  -- Never let a promo exceed the goods value.
  v_disc := greatest(least(v_raw, coalesce(p_items_sum, 0)), 0);

  if v_disc = 0 then
    return query select v_promo.code, 0, 'ZERO_DISCOUNT'::text, coalesce(v_promo.min_order_amount, 0);
    return;
  end if;

  return query select v_promo.code, v_disc, null::text, coalesce(v_promo.min_order_amount, 0);
end;
$$;

-- Internal only. evaluate_promo takes any user id, so exposing it would let a
-- caller ask about someone else's promo usage.
revoke all on function public.resolve_unit_price(jsonb, int, text) from public, anon, authenticated;
revoke all on function public.evaluate_promo(text, text, int, uuid) from public, anon, authenticated;


-- -----------------------------------------------------------------------------
-- 4. create_order_with_items — rewritten.
--
--    SECURITY DEFINER is required: decrementing products.stock_qty and
--    incrementing promo_codes.times_used are writes a customer has no RLS
--    grant for, and should never be given one. Because RLS no longer shields
--    this function, ownership is enforced explicitly below:
--      - user_id is taken from auth.uid(), never from the payload
--      - every product must belong to the shop the order is placed against
--    search_path is pinned so a rogue schema cannot shadow these tables.
-- -----------------------------------------------------------------------------
drop function if exists public.create_order_with_items(jsonb, jsonb);

create function public.create_order_with_items(
  p_order jsonb,
  p_items jsonb default '[]'::jsonb
)
returns table(
  order_number      int,
  pickup_token      text,
  total_amount      int,
  item_total        int,
  handling_fee      int,
  handling_discount int,
  promo_discount    int,
  promo_code        text
)
language plpgsql
security definer
-- `extensions` must stay on the path: the pickup-token trigger that fires
-- inside this function calls pgcrypto's gen_random_bytes, which Supabase
-- installs there, not in public.
set search_path = public, extensions
as $$
declare
  v_uid        uuid := auth.uid();
  v_order_id   text := nullif(trim(p_order->>'id'), '');
  v_shop_id    text := nullif(trim(p_order->>'shop_id'), '');
  v_promo_in   text := nullif(upper(trim(coalesce(p_order->>'promo_code', ''))), '');
  v_shop       record;
  v_item       jsonb;
  v_product    record;
  v_qty        int;
  v_weight     text;
  v_unit_price int;
  v_line       int;
  v_priced     jsonb := '[]'::jsonb;
  v_items_sum  int := 0;
  v_cfg        record;
  v_handling   int := 0;
  v_hdiscount  int := 0;
  v_promo_disc int := 0;
  v_promo_code text := null;
  v_total      int;
begin
  ---------------------------------------------------------------------------
  -- Identity and shape
  ---------------------------------------------------------------------------
  if v_uid is null then
    raise exception 'You are signed out. Please sign in and try again.'
      using errcode = 'P0001';
  end if;

  if v_order_id is null then
    raise exception 'MISSING_ORDER_ID' using errcode = 'P0001';
  end if;

  if v_shop_id is null then
    raise exception 'No shop selected for this order.' using errcode = 'P0001';
  end if;

  if jsonb_array_length(coalesce(p_items, '[]'::jsonb)) = 0 then
    raise exception 'Your cart is empty.' using errcode = 'P0001';
  end if;

  select s.id, s.name, s.status::text as status_text,
         coalesce(s.is_deleted, false)      as is_deleted,
         coalesce(s.accepting_orders, true) as accepting_orders
    into v_shop
    from public.shops s
   where s.id = v_shop_id;

  if not found or v_shop.is_deleted then
    raise exception 'That shop is no longer available.' using errcode = 'P0001';
  end if;

  -- status is the vendor_status enum, so compare as text.
  if v_shop.status_text <> 'approved' then
    raise exception '% is not approved to take orders.', v_shop.name
      using errcode = 'P0001';
  end if;

  if not v_shop.accepting_orders then
    raise exception '% has paused new orders right now.', v_shop.name
      using errcode = 'P0001';
  end if;

  ---------------------------------------------------------------------------
  -- Price and reserve every line.
  --
  -- FOR UPDATE locks each product row until this transaction ends, so two
  -- customers racing for the last unit are serialised: the second one waits,
  -- then reads the already-decremented value and fails the stock check.
  ---------------------------------------------------------------------------
  for v_item in select * from jsonb_array_elements(p_items)
  loop
    v_qty    := greatest(coalesce((v_item->>'quantity')::int, 1), 1);
    v_weight := coalesce(v_item->>'weight_label', '');

    select p.id, p.shop_id, p.name, p.brand, p.image_url, p.current_price,
           p.in_stock, p.stock_qty, p.weight_options,
           coalesce(p.is_active, true)      as is_active,
           coalesce(p.is_restricted, false) as is_restricted
      into v_product
      from public.products p
     where p.id = v_item->>'product_id'
       for update;

    if not found then
      raise exception 'A product in your cart is no longer available.'
        using errcode = 'P0001';
    end if;

    if v_product.shop_id is distinct from v_shop_id then
      raise exception '% belongs to a different shop. Please rebuild your cart.',
        v_product.name using errcode = 'P0001';
    end if;

    if not v_product.is_active or v_product.is_restricted then
      raise exception '% is no longer sold here.', v_product.name
        using errcode = 'P0001';
    end if;

    if coalesce(v_product.in_stock, true) = false then
      raise exception '% is out of stock.', v_product.name using errcode = 'P0001';
    end if;

    -- Price comes from the product row, never from the payload. When the
    -- product has weight variants the label must match one of them exactly;
    -- falling back to current_price here would let a client pick the cheapest
    -- price for the largest pack.
    v_unit_price := public.resolve_unit_price(
      v_product.weight_options, v_product.current_price, v_weight);

    if v_unit_price is null
       and jsonb_array_length(coalesce(v_product.weight_options, '[]'::jsonb)) > 0 then
      raise exception 'The selected size for % is no longer sold.', v_product.name
        using errcode = 'P0001';
    end if;

    if v_unit_price is null or v_unit_price <= 0 then
      raise exception '% is not priced correctly. Please tell the shop.', v_product.name
        using errcode = 'P0001';
    end if;

    -- stock_qty NULL means the shop does not track counts for this item
    -- ("Call to Confirm"), so availability is governed by in_stock alone.
    if v_product.stock_qty is not null then
      if v_product.stock_qty < v_qty then
        raise exception 'Only % left of %.', v_product.stock_qty, v_product.name
          using errcode = 'P0001';
      end if;

      update public.products
         set stock_qty = stock_qty - v_qty,
             in_stock  = (stock_qty - v_qty) > 0
       where id = v_product.id;
    end if;

    v_line      := v_unit_price * v_qty;
    v_items_sum := v_items_sum + v_line;

    v_priced := v_priced || jsonb_build_object(
      'product_id',   v_product.id,
      'product_name', coalesce(v_product.name, ''),
      'brand',        coalesce(v_product.brand, ''),
      'image_url',    coalesce(v_product.image_url, ''),
      'weight_label', v_weight,
      'unit_price',   v_unit_price,
      'quantity',     v_qty,
      'line_total',   v_line
    );
  end loop;

  ---------------------------------------------------------------------------
  -- Fees, from the database
  ---------------------------------------------------------------------------
  select a.handling_fee, a.min_order_free_handling, a.free_handling_discount
    into v_cfg
    from public.app_settings a
   where a.id = 1;

  v_handling  := coalesce(v_cfg.handling_fee, 0);
  v_hdiscount := case
                   when v_items_sum > coalesce(v_cfg.min_order_free_handling, 0)
                   then coalesce(v_cfg.free_handling_discount, 0)
                   else 0
                 end;

  ---------------------------------------------------------------------------
  -- Promo, validated and computed by evaluate_promo(). An invalid code is
  -- ignored rather than fatal — nobody should lose a whole cart because a code
  -- expired between opening the cart and tapping Place Order.
  --
  -- The promo row is locked first so two checkouts racing for the last use of
  -- a limited code are serialised: the second one waits, then counts the
  -- first one's committed order.
  ---------------------------------------------------------------------------
  if v_promo_in is not null then
    perform 1
       from public.promo_codes pc
      where upper(pc.code) = v_promo_in
        for update;

    select e.promo_code, e.discount
      into v_promo_code, v_promo_disc
      from public.evaluate_promo(v_promo_in, v_shop_id, v_items_sum, v_uid) e;

    if v_promo_disc is null or v_promo_disc <= 0 then
      v_promo_code := null;
      v_promo_disc := 0;
    end if;
  end if;

  v_total := greatest(v_items_sum + v_handling - v_hdiscount - v_promo_disc, 0);

  ---------------------------------------------------------------------------
  -- Persist. The BEFORE INSERT trigger assigns order_number and pickup_token.
  ---------------------------------------------------------------------------
  insert into public.orders (
    id, customer_name, customer_email, customer_mobile,
    total_amount, item_total, handling_fee, handling_discount,
    status, order_date, qr_code_payload, items_json,
    user_id, shop_id, promo_code, promo_discount
  ) values (
    v_order_id,
    coalesce(p_order->>'customer_name', ''),
    lower(trim(coalesce(p_order->>'customer_email', ''))),
    coalesce(p_order->>'customer_mobile', ''),
    v_total,
    v_items_sum,
    v_handling,
    v_hdiscount,
    coalesce(p_order->>'status', 'Order Placed'),
    coalesce(p_order->>'order_date', ''),
    coalesce(p_order->>'qr_code_payload', ''),
    v_priced,
    v_uid,
    v_shop_id,
    v_promo_code,
    v_promo_disc
  );

  insert into public.order_items (
    order_id, product_id, product_name, brand, image_url,
    weight_label, unit_price, quantity, line_total
  )
  select
    v_order_id,
    it->>'product_id',
    it->>'product_name',
    it->>'brand',
    it->>'image_url',
    it->>'weight_label',
    (it->>'unit_price')::int,
    (it->>'quantity')::int,
    (it->>'line_total')::int
  from jsonb_array_elements(v_priced) it;

  return query
    select o.order_number, o.pickup_token,
           v_total, v_items_sum, v_handling, v_hdiscount,
           v_promo_disc, v_promo_code
      from public.orders o
     where o.id = v_order_id;
end;
$$;

revoke all on function public.create_order_with_items(jsonb, jsonb) from public;
revoke all on function public.create_order_with_items(jsonb, jsonb) from anon;
grant execute on function public.create_order_with_items(jsonb, jsonb) to authenticated;


-- -----------------------------------------------------------------------------
-- 4b. preview_promo — what the cart calls before checkout.
--
--     Prices the cart with the same resolve_unit_price() and judges the code
--     with the same evaluate_promo() that create_order_with_items uses, so a
--     code shown as applied in the cart is one checkout will accept.
--     Read-only: no stock, no order, no lock.
-- -----------------------------------------------------------------------------
create or replace function public.preview_promo(
  p_code    text,
  p_shop_id text,
  p_items   jsonb default '[]'::jsonb
)
returns table(promo_code text, discount int, reason text, min_order_amount int, items_total int)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_uid uuid := auth.uid();
  v_sum int;
begin
  if v_uid is null then
    raise exception 'You are signed out. Please sign in and try again.'
      using errcode = 'P0001';
  end if;

  -- Lines checkout would refuse add nothing; checkout then rejects the cart.
  select coalesce(sum(
           public.resolve_unit_price(p.weight_options, p.current_price, it->>'weight_label')
           * greatest(coalesce((it->>'quantity')::int, 1), 1)
         ), 0)
    into v_sum
    from jsonb_array_elements(coalesce(p_items, '[]'::jsonb)) it
    join public.products p on p.id = it->>'product_id'
   where p.shop_id = p_shop_id
     and coalesce(p.is_active, true)
     and not coalesce(p.is_restricted, false);

  return query
    select e.promo_code, e.discount, e.reason, e.min_order_amount, v_sum
      from public.evaluate_promo(p_code, p_shop_id, v_sum, v_uid) e;
end;
$$;

revoke all on function public.preview_promo(text, text, jsonb) from public, anon;
grant execute on function public.preview_promo(text, text, jsonb) to authenticated;


-- -----------------------------------------------------------------------------
-- 5. Restore stock when an order is cancelled or auto-expired.
--    Without this, every cancellation permanently loses inventory.
-- -----------------------------------------------------------------------------
create or replace function public.restore_stock_on_cancel()
returns trigger
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
  if new.status = 'Cancelled' and old.status is distinct from 'Cancelled' then
    update public.products p
       set stock_qty = coalesce(p.stock_qty, 0) + oi.quantity,
           in_stock  = true
      from public.order_items oi
     where oi.order_id = new.id
       and p.id = oi.product_id
       and p.stock_qty is not null;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_restore_stock_on_cancel on public.orders;
create trigger trg_restore_stock_on_cancel
  after update of status on public.orders
  for each row
  execute function public.restore_stock_on_cancel();


-- -----------------------------------------------------------------------------
-- 6. Fee configuration.
--
--    Whatever is in public.app_settings is what customers are charged AND what
--    the cart shows. To change fees, update that one row — for example:
--
--      update public.app_settings
--         set handling_fee = 5, min_order_free_handling = 200,
--             free_handling_discount = 5, updated_at = now()
--       where id = 1;
--
--    This file deliberately does not overwrite the row, so re-running it can
--    never silently reset live fees.
-- -----------------------------------------------------------------------------


-- -----------------------------------------------------------------------------
-- Verify
-- -----------------------------------------------------------------------------
select 'app_settings' as checked, * from public.app_settings;

select 'promo_codes' as checked, code, active, discount_percent, discount_flat_rupees,
       min_order_amount, max_discount_rupees, valid_from, valid_until,
       usage_limit, max_uses_per_customer, applicable_shop_id
  from public.promo_codes order by code;

select 'function' as checked, p.proname, p.prosecdef as security_definer,
       has_function_privilege('authenticated', p.oid, 'EXECUTE') as app_can_call
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in ('create_order_with_items', 'preview_promo', 'evaluate_promo', 'resolve_unit_price')
 order by p.proname;
