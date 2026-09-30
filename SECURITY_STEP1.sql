-- =============================================================================
--  BreakQ — Security Step 1
--
--  Closes, on the server:
--    SEC-1  vendor approving their own shop
--    SEC-2  vendor faking their shop rating / rating their own shop
--    SEC-3  non-approved shops readable by anyone
--    SEC-4  orders created with a forged status (RPC and direct INSERT)
--    SEC-5  extra order_items added to an existing order
--    SEC-7  customer cancel also changing total/shop/pickup token
--    SEC-8  any signed-in user replacing/deleting another shop's photo
--    SEC-10 vendor-visible customer identity coming from the app
--    SEC-13 blocked accounts still ordering / rating / editing
--
--  RUN THE WHOLE FILE ONCE. Safe to re-run. No row data is changed or deleted.
--  Then run TEST_SECURITY_STEP1.sql, TEST_RLS_WRITE_HOLES.sql and
--  TEST_ORDER_SERVER_AUTHORITY.sql.
--
--  "Trusted" below = the web admin (is_admin()), the service role (Edge
--  Functions, dashboard) and SECURITY DEFINER database functions such as
--  create_order_with_items and the rating trigger. Only direct anon /
--  authenticated writes from the app are restricted. No existing policy is
--  loosened; everything added is either a guard trigger or a RESTRICTIVE policy,
--  which can only take access away.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 0. Helpers
-- -----------------------------------------------------------------------------

-- Deliberately NOT security definer: it must see the caller's own role.
create or replace function public.breakq_is_trusted_caller()
returns boolean
language sql
stable
set search_path = public, pg_temp
as $$
  select current_user not in ('anon', 'authenticated') or public.is_admin();
$$;

revoke all on function public.breakq_is_trusted_caller() from public;
grant execute on function public.breakq_is_trusted_caller() to anon, authenticated, service_role;

create or replace function public.is_current_user_blocked()
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select coalesce(
    (select p.is_blocked from public.profiles p where p.id = auth.uid()),
    false
  );
$$;

revoke all on function public.is_current_user_blocked() from public;
grant execute on function public.is_current_user_blocked() to anon, authenticated, service_role;


-- -----------------------------------------------------------------------------
-- 1. shops — approval, partner flag and rating aggregates are server-owned
--    (SEC-1, SEC-2, SEC-13)
--
--    Protected columns are silently kept at their old value rather than
--    raising, because the app's updateShop() sends its cached is_partner on
--    every edit; a stale cache must not break a legitimate shop edit.
--    Columns that don't exist on the live table are simply skipped.
-- -----------------------------------------------------------------------------
create or replace function public.guard_shop_protected_columns()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
declare
  v_protected constant text[] := array[
    'id', 'owner_id', 'status', 'is_partner', 'avg_rating', 'rating_count',
    'rejection_reason', 'is_deleted', 'commission_enabled_at', 'created_at',
    'approved_at', 'approved_by', 'reviewed_at', 'reviewed_by',
    'verified_at', 'is_verified', 'is_featured'
  ];
  v_old  jsonb;
  v_keep jsonb;
begin
  if public.breakq_is_trusted_caller() then
    return new;
  end if;

  if public.is_current_user_blocked() then
    raise exception 'This account has been blocked. Please contact BreakQ support.'
      using errcode = 'P0001';
  end if;

  if tg_op = 'INSERT' then
    new := jsonb_populate_record(new, jsonb_build_object(
      'owner_id',              auth.uid(),
      'status',                'pending',
      'is_partner',            false,
      'avg_rating',            0,
      'rating_count',          0,
      'rejection_reason',      null,
      'is_deleted',            false,
      'commission_enabled_at', null,
      'approved_at',           null,
      'approved_by',           null,
      'reviewed_at',           null,
      'reviewed_by',           null,
      'verified_at',           null,
      'is_verified',           false,
      'is_featured',           false
    ));
    return new;
  end if;

  v_old := to_jsonb(old);
  select jsonb_object_agg(k, v_old -> k)
    into v_keep
    from unnest(v_protected) as k
   where v_old ? k;

  new := jsonb_populate_record(new, coalesce(v_keep, '{}'::jsonb));
  return new;
end;
$$;

-- "a_" makes it fire before every other BEFORE trigger on shops, so it judges
-- exactly what the client asked for.
drop trigger if exists a_guard_shop_protected_columns on public.shops;
create trigger a_guard_shop_protected_columns
  before insert or update on public.shops
  for each row
  execute function public.guard_shop_protected_columns();


-- -----------------------------------------------------------------------------
-- 2. shops — only approved shops are publicly readable (SEC-3)
--
--    RESTRICTIVE policies are ANDed with whatever permissive read policy
--    already exists, so this narrows access without needing its name.
--    Owners still see their own pending/rejected shop; admins see everything.
-- -----------------------------------------------------------------------------
drop policy if exists "shops_public_only_approved_anon" on public.shops;
create policy "shops_public_only_approved_anon" on public.shops
  as restrictive
  for select
  to anon
  using (status::text = 'approved');

drop policy if exists "shops_public_only_approved_auth" on public.shops;
create policy "shops_public_only_approved_auth" on public.shops
  as restrictive
  for select
  to authenticated
  using (
    status::text = 'approved'
    or owner_id = auth.uid()
    or public.is_admin()
  );


-- -----------------------------------------------------------------------------
-- 3. shop_ratings — no rating your own shop, no edits, no blocked raters
--    (SEC-2, SEC-13)
-- -----------------------------------------------------------------------------
drop policy if exists "ratings_not_own_shop_not_blocked" on public.shop_ratings;
create policy "ratings_not_own_shop_not_blocked" on public.shop_ratings
  as restrictive
  for insert
  to authenticated
  with check (
    not public.is_current_user_blocked()
    and not exists (
      select 1 from public.shops s
       where s.id = shop_ratings.shop_id
         and s.owner_id = auth.uid()
    )
  );

drop policy if exists "ratings_update_admin_only" on public.shop_ratings;
create policy "ratings_update_admin_only" on public.shop_ratings
  as restrictive
  for update
  to anon, authenticated
  using (public.is_admin())
  with check (public.is_admin());

drop policy if exists "ratings_delete_admin_only" on public.shop_ratings;
create policy "ratings_delete_admin_only" on public.shop_ratings
  as restrictive
  for delete
  to anon, authenticated
  using (public.is_admin());


-- -----------------------------------------------------------------------------
-- 4. create_order_with_items — server sets status and identity (SEC-4, SEC-10,
--    SEC-13). Same signature and return type, so grants are preserved.
--    Kept identical to section 4 of ORDER_SERVER_AUTHORITY.sql.
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
    user_id, shop_id, promo_code, promo_discount
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
-- 5. Orders and their items can only be created through the RPC (SEC-4, SEC-5)
--
--    create_order_with_items is SECURITY DEFINER, so it is not affected by
--    these. Only direct REST inserts/edits from the app are refused.
-- -----------------------------------------------------------------------------
drop policy if exists "orders_insert_only_via_rpc" on public.orders;
create policy "orders_insert_only_via_rpc" on public.orders
  as restrictive
  for insert
  to anon, authenticated
  with check (public.is_admin());

-- Superseded by the RPC; it let a customer append lines to any of their orders.
drop policy if exists "customers insert own order_items" on public.order_items;

drop policy if exists "order_items_insert_only_via_rpc" on public.order_items;
create policy "order_items_insert_only_via_rpc" on public.order_items
  as restrictive
  for insert
  to anon, authenticated
  with check (public.is_admin());

drop policy if exists "order_items_update_admin_only" on public.order_items;
create policy "order_items_update_admin_only" on public.order_items
  as restrictive
  for update
  to anon, authenticated
  using (public.is_admin())
  with check (public.is_admin());

drop policy if exists "order_items_delete_admin_only" on public.order_items;
create policy "order_items_delete_admin_only" on public.order_items
  as restrictive
  for delete
  to anon, authenticated
  using (public.is_admin());


-- -----------------------------------------------------------------------------
-- 6. orders — the app may change the status and nothing else (SEC-7, SEC-10,
--    SEC-13)
--
--    Customer cancel and vendor accept/prepare/ready all send only `status`.
--    Money, shop, customer, pickup token, items, cancellation attribution etc.
--    can only be changed by trusted server code or an admin. Who may make a
--    given status change is still decided by the existing RLS policies and
--    the state-machine trigger.
-- -----------------------------------------------------------------------------
create or replace function public.guard_order_client_writes()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
declare
  v_skip text[];
begin
  if public.breakq_is_trusted_caller() then
    return new;
  end if;

  if public.is_current_user_blocked() then
    raise exception 'This account has been blocked. Please contact BreakQ support.'
      using errcode = 'P0001';
  end if;

  -- Generated columns are still NULL in a BEFORE trigger; don't compare them.
  select coalesce(array_agg(a.attname::text), '{}')
    into v_skip
    from pg_attribute a
   where a.attrelid = tg_relid
     and a.attgenerated <> ''
     and not a.attisdropped;

  if (to_jsonb(new) - 'status' - v_skip) is distinct from (to_jsonb(old) - 'status' - v_skip) then
    raise exception 'Only the order status can be changed from the app.'
      using errcode = 'P0001';
  end if;

  return new;
end;
$$;

-- Fires first, before the timestamp/state-machine triggers modify the row.
drop trigger if exists a_guard_order_client_writes on public.orders;
create trigger a_guard_order_client_writes
  before update on public.orders
  for each row
  execute function public.guard_order_client_writes();


-- -----------------------------------------------------------------------------
-- 7. shop-images — only the uploader (or an admin) can overwrite or delete a
--    photo (SEC-8). Public read and new uploads are unchanged. Supabase sets
--    storage.objects.owner from the uploader's JWT, the same rule
--    shop-documents already relies on.
-- -----------------------------------------------------------------------------
drop policy if exists "shop-images-authenticated-update" on storage.objects;
create policy "shop-images-authenticated-update"
  on storage.objects for update
  to authenticated
  using (bucket_id = 'shop-images' and (owner = auth.uid() or public.is_admin()))
  with check (bucket_id = 'shop-images' and (owner = auth.uid() or public.is_admin()));

drop policy if exists "shop-images-authenticated-delete" on storage.objects;
create policy "shop-images-authenticated-delete"
  on storage.objects for delete
  to authenticated
  using (bucket_id = 'shop-images' and (owner = auth.uid() or public.is_admin()));


-- -----------------------------------------------------------------------------
-- 8. products — blocked vendors cannot change their catalogue (SEC-13)
-- -----------------------------------------------------------------------------
drop policy if exists "products_insert_not_blocked" on public.products;
create policy "products_insert_not_blocked" on public.products
  as restrictive
  for insert
  to authenticated
  with check (not public.is_current_user_blocked());

drop policy if exists "products_update_not_blocked" on public.products;
create policy "products_update_not_blocked" on public.products
  as restrictive
  for update
  to authenticated
  using (not public.is_current_user_blocked())
  with check (not public.is_current_user_blocked());

drop policy if exists "products_delete_not_blocked" on public.products;
create policy "products_delete_not_blocked" on public.products
  as restrictive
  for delete
  to authenticated
  using (not public.is_current_user_blocked());


-- -----------------------------------------------------------------------------
-- Verify — what this file installed.
-- -----------------------------------------------------------------------------
select 'policy'::text as kind, (schemaname || '.' || tablename)::text as on_table,
       policyname::text as name, permissive::text, cmd::text
  from pg_policies
 where policyname in (
   'shops_public_only_approved_anon', 'shops_public_only_approved_auth',
   'ratings_not_own_shop_not_blocked', 'ratings_update_admin_only', 'ratings_delete_admin_only',
   'orders_insert_only_via_rpc', 'order_items_insert_only_via_rpc',
   'order_items_update_admin_only', 'order_items_delete_admin_only',
   'shop-images-authenticated-update', 'shop-images-authenticated-delete',
   'products_insert_not_blocked', 'products_update_not_blocked', 'products_delete_not_blocked')
union all
select 'trigger', c.relname::text, t.tgname::text, '-', '-'
  from pg_trigger t
  join pg_class c on c.oid = t.tgrelid
 where t.tgname in ('a_guard_shop_protected_columns', 'a_guard_order_client_writes')
order by 1, 2, 3;
