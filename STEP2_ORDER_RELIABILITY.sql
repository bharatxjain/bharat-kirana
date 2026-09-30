-- =============================================================================
--  BreakQ — Step 2: order reliability
--
--    H3  One price: for single-size products, current_price and the size
--        price in weight_options are kept identical by a trigger, so the
--        price a vendor edits is the price the server charges.
--    H4  Retrying the same checkout returns the existing order instead of
--        creating a second one (no second stock hold, no second notification).
--    M1  The app sends the total the customer saw (expected_total). If the
--        server's total differs, nothing is created and the app asks again.
--    M13 Each new order keeps the shop's name and address as they were at
--        purchase time.
--
--  RUN THE WHOLE FILE ONCE, after SECURITY_STEP1.sql. Safe to re-run.
--  No rows are deleted. Section 2 aligns drifted size prices with the price
--  the vendor last set (products.current_price) — see the note there.
--
--  ORDER_SERVER_AUTHORITY.sql carries the same create_order_with_items.
--  SECURITY_STEP1.sql has an OLDER copy: if you ever re-run that file,
--  re-run this one afterwards.
--
--  Then run TEST_STEP2_ORDER_RELIABILITY.sql, TEST_SECURITY_STEP1.sql and
--  TEST_ORDER_SERVER_AUTHORITY.sql.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Shop snapshot on orders (M13)
-- -----------------------------------------------------------------------------
alter table public.orders add column if not exists shop_name    text;
alter table public.orders add column if not exists shop_address text;


-- -----------------------------------------------------------------------------
-- 2. One price for single-size products (H3)
--
--    The vendor app edits products.current_price, but checkout charges the
--    size price inside weight_options. Every product the app creates has
--    exactly one size, so for those the two numbers must always be the same:
--      - current_price changed        -> the size price follows it
--      - only the size price changed  -> current_price follows it
--    Products with several sizes are left alone (each size has its own price).
-- -----------------------------------------------------------------------------
create or replace function public.sync_single_variant_price()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
declare
  v_size_price int;
begin
  if new.current_price is null or jsonb_typeof(new.weight_options) is distinct from 'array' then
    return new;
  end if;
  if jsonb_array_length(new.weight_options) <> 1 then
    return new;
  end if;

  v_size_price := round((new.weight_options->0->>'price')::numeric)::int;
  if v_size_price is not distinct from new.current_price then
    return new;
  end if;

  if tg_op = 'UPDATE'
     and new.current_price is not distinct from old.current_price
     and v_size_price is not null then
    new.current_price := v_size_price;
  else
    new.weight_options := jsonb_set(new.weight_options, '{0,price}', to_jsonb(new.current_price));
  end if;

  return new;
end;
$$;

drop trigger if exists trg_sync_single_variant_price on public.products;
create trigger trg_sync_single_variant_price
  before insert or update on public.products
  for each row
  execute function public.sync_single_variant_price();

-- Existing drift: the inventory screen shows and edits current_price, so that
-- is the price the vendor last chose. Align the size price with it.
update public.products p
   set weight_options = jsonb_set(p.weight_options, '{0,price}', to_jsonb(p.current_price))
 where p.current_price is not null
   and case when jsonb_typeof(p.weight_options) = 'array'
            then jsonb_array_length(p.weight_options) else 0 end = 1
   and (p.weight_options->0->>'price') is distinct from p.current_price::text;


-- -----------------------------------------------------------------------------
-- 3. create_order_with_items (H4, M1, M13). Same signature and return type,
--    so existing grants are kept. Identical to section 4 of
--    ORDER_SERVER_AUTHORITY.sql.
-- -----------------------------------------------------------------------------
create or replace function public.create_order_with_items(
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
  v_email      text;
  v_name       text;
  v_mobile     text;
  v_expected   int := nullif(trim(coalesce(p_order->>'expected_total', '')), '')::int;
  v_owner_uid  uuid;
begin
  ---------------------------------------------------------------------------
  -- Identity and shape
  ---------------------------------------------------------------------------
  if v_uid is null then
    raise exception 'You are signed out. Please sign in and try again.'
      using errcode = 'P0001';
  end if;

  if public.is_current_user_blocked() then
    raise exception 'This account has been blocked. Please contact BreakQ support.'
      using errcode = 'P0001';
  end if;

  if v_order_id is null then
    raise exception 'MISSING_ORDER_ID' using errcode = 'P0001';
  end if;

  -- A retry of the same checkout (e.g. the first response was lost) gets the
  -- order that already exists, with no second stock hold or notification.
  perform pg_advisory_xact_lock(hashtext('breakq_order:' || v_order_id));
  select o.user_id into v_owner_uid from public.orders o where o.id = v_order_id;
  if found then
    if v_owner_uid is distinct from v_uid then
      raise exception 'This order reference is already in use. Please try again.'
        using errcode = 'P0001';
    end if;
    return query
      select o.order_number::int, o.pickup_token::text, o.total_amount::int,
             o.item_total::int, o.handling_fee::int, o.handling_discount::int,
             o.promo_discount::int, o.promo_code::text
        from public.orders o
       where o.id = v_order_id;
    return;
  end if;

  if v_shop_id is null then
    raise exception 'No shop selected for this order.' using errcode = 'P0001';
  end if;

  if jsonb_array_length(coalesce(p_items, '[]'::jsonb)) = 0 then
    raise exception 'Your cart is empty.' using errcode = 'P0001';
  end if;

  select s.id, s.name, s.status::text as status_text,
         coalesce(s.address, '')            as address,
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

  -- The app sends the total the customer saw. If the server's figure differs
  -- (price, fee or promo changed), nothing is created and the app asks again.
  if v_expected is not null and v_expected <> v_total then
    raise exception 'PRICE_CHANGED'
      using errcode = 'P0001',
            detail  = json_build_object(
              'total',             v_total,
              'item_total',        v_items_sum,
              'handling_fee',      v_handling,
              'handling_discount', v_hdiscount,
              'promo_discount',    v_promo_disc,
              'promo_code',        v_promo_code
            )::text;
  end if;

  ---------------------------------------------------------------------------
  -- Persist. The BEFORE INSERT trigger assigns order_number and pickup_token.
  ---------------------------------------------------------------------------
  -- Who the vendor sees comes from the account, not the app. The payload
  -- name/mobile are only used when the profile has none yet.
  select lower(trim(u.email)) into v_email from auth.users u where u.id = v_uid;
  select nullif(trim(p.full_name), ''), nullif(trim(p.mobile_number), '')
    into v_name, v_mobile
    from public.profiles p
   where p.id = v_uid;

  insert into public.orders (
    id, customer_name, customer_email, customer_mobile,
    total_amount, item_total, handling_fee, handling_discount,
    status, order_date, qr_code_payload, items_json,
    user_id, shop_id, promo_code, promo_discount,
    shop_name, shop_address
  ) values (
    v_order_id,
    coalesce(v_name, nullif(trim(p_order->>'customer_name'), ''), ''),
    coalesce(v_email, ''),
    coalesce(v_mobile, nullif(trim(p_order->>'customer_mobile'), ''), ''),
    v_total,
    v_items_sum,
    v_handling,
    v_hdiscount,
    'Order Placed',
    'Today, ' || to_char(now() at time zone 'Asia/Kolkata', 'FMHH12:MI AM'),
    -- Same format as buildCustomerQrPayload() in the app.
    'BREAKQ:USER:' || coalesce(nullif(v_email, ''), 'unknown') || ':REF:' || upper(right(v_order_id, 4)),
    v_priced,
    v_uid,
    v_shop_id,
    v_promo_code,
    v_promo_disc,
    v_shop.name,
    v_shop.address
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
-- Verify
-- -----------------------------------------------------------------------------
select 'orders.shop_name column' as checked,
       exists (select 1 from information_schema.columns
                where table_schema = 'public' and table_name = 'orders'
                  and column_name = 'shop_name')::text as ok
union all
select 'price sync trigger',
       exists (select 1 from pg_trigger
                where tgname = 'trg_sync_single_variant_price'
                  and tgrelid = 'public.products'::regclass)::text
union all
select 'single-size products still out of sync (expect 0)',
       count(*)::text
  from public.products p
 where p.current_price is not null
   and case when jsonb_typeof(p.weight_options) = 'array'
            then jsonb_array_length(p.weight_options) else 0 end = 1
   and (p.weight_options->0->>'price') is distinct from p.current_price::text;
