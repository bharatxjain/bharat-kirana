-- =============================================================================
--  BreakQ — Step 4: vendor operations
--
--  Run the WHOLE file once, after SECURITY_STEP1.sql and STEP2_ORDER_RELIABILITY.sql.
--  Safe to re-run. No existing row is changed or deleted. Nothing here loosens
--  an existing rule except section 9, which lets APPROVED vendors upload
--  product photos (the feature the app already relies on).
--
--   1. orders.cancelled_by / cancel_reason exist (the notify-order-status
--      function already reads them).
--   2. An order can only become Completed through the pickup-code check
--      (complete_order_by_pickup_token) — not by a plain status edit from the app.
--   3. Every cancellation records who cancelled it: customer / vendor / system / admin.
--   4. vendor_cancel_order(order, reason): the shop cancels with a reason the
--      customer can see.
--   5. Orders: the app can only read or change orders the caller placed or
--      that belong to the caller's shop (restrictive, defence in depth).
--   6. Products: shop and moderation flag can't be changed from the app; no
--      zero/negative price, negative stock or blank name; stock 0 marks the
--      item unavailable and restocking from 0 makes it available again.
--   7. Products: only approved, live shops can change their catalogue, and
--      customers only see active items of approved, live shops.
--   8. Cancelling restores stock for EVERY line, including two lines of the
--      same product (previously only one was put back).
--   9. product-images: approved vendors may upload; only the uploader may
--      replace or delete their file.
--  10. One pending/approved shop per owner (stops duplicate registrations).
--
--  NOTE: section 8 replaces restore_stock_on_cancel(). Re-running the older
--  ORDER_SERVER_AUTHORITY.sql afterwards would bring back the old version.
--
--  Then run TEST_STEP4_VENDOR_OPERATIONS.sql.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 0. Pre-checks — stop before changing anything if the live database differs
--    from what this file expects.
-- -----------------------------------------------------------------------------
do $$
declare
  v_type text;
  v_check text;
begin
  if to_regprocedure('public.breakq_is_trusted_caller()') is null
     or to_regprocedure('public.is_current_user_blocked()') is null then
    raise exception 'Run SECURITY_STEP1.sql first — its helper functions are missing. Nothing was changed.';
  end if;

  select data_type into v_type from information_schema.columns
   where table_schema = 'public' and table_name = 'orders' and column_name = 'cancelled_by';
  if v_type is not null and v_type not in ('text', 'character varying') then
    raise exception 'orders.cancelled_by is % (expected text). Nothing was changed — please share this message.', v_type;
  end if;

  select data_type into v_type from information_schema.columns
   where table_schema = 'public' and table_name = 'orders' and column_name = 'cancel_reason';
  if v_type is not null and v_type not in ('text', 'character varying') then
    raise exception 'orders.cancel_reason is % (expected text). Nothing was changed — please share this message.', v_type;
  end if;

  -- A CHECK on cancelled_by could refuse the values this file writes.
  select string_agg(pg_get_constraintdef(c.oid), ' | ') into v_check
    from pg_constraint c
   where c.conrelid = 'public.orders'::regclass
     and c.contype = 'c'
     and pg_get_constraintdef(c.oid) ilike '%cancelled_by%';
  if v_check is not null then
    raise exception 'orders has a CHECK on cancelled_by: %. Nothing was changed — please share this message.', v_check;
  end if;
end
$$;


-- -----------------------------------------------------------------------------
-- 1. Cancellation attribution columns
-- -----------------------------------------------------------------------------
alter table public.orders
  add column if not exists cancelled_by  text,
  add column if not exists cancel_reason text;


-- -----------------------------------------------------------------------------
-- 2. Completed only through the pickup-code check
--
--    complete_order_by_pickup_token is SECURITY DEFINER, so it counts as a
--    trusted caller and still works; so does the web admin.
-- -----------------------------------------------------------------------------
create or replace function public.guard_order_completion()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
  if new.status = 'Completed'
     and old.status is distinct from 'Completed'
     and not public.breakq_is_trusted_caller() then
    raise exception 'Orders are completed by scanning the customer''s pickup QR or entering the order number on the Verify Pickup screen.'
      using errcode = 'P0001';
  end if;
  return new;
end;
$$;

drop trigger if exists a_guard_order_completion on public.orders;
create trigger a_guard_order_completion
  before update of status on public.orders
  for each row
  execute function public.guard_order_completion();


-- -----------------------------------------------------------------------------
-- 3. Who cancelled — stamped by the database, never sent by the app.
--    Runs after a_guard_order_client_writes, which only judges what the app sent.
-- -----------------------------------------------------------------------------
create or replace function public.stamp_order_cancellation()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
begin
  if new.status = 'Cancelled'
     and old.status is distinct from 'Cancelled'
     and new.cancelled_by is null then
    new.cancelled_by := case
      when v_uid is null then 'system'
      when v_uid = new.user_id then 'customer'
      when public.is_admin() then 'admin'
      when exists (select 1 from public.shops s where s.id = new.shop_id and s.owner_id = v_uid) then 'vendor'
      else null
    end;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_stamp_order_cancellation on public.orders;
create trigger trg_stamp_order_cancellation
  before update of status on public.orders
  for each row
  execute function public.stamp_order_cancellation();


-- -----------------------------------------------------------------------------
-- 4. Vendor cancellation with a reason
-- -----------------------------------------------------------------------------
create or replace function public.vendor_cancel_order(p_order_id text, p_reason text)
returns text
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_uid    uuid := auth.uid();
  v_reason text := left(nullif(btrim(coalesce(p_reason, '')), ''), 200);
  v_order  public.orders;
begin
  if v_uid is null then
    raise exception 'Please sign in again.' using errcode = 'P0001';
  end if;

  if public.is_current_user_blocked() then
    raise exception 'This account has been blocked. Please contact BreakQ support.'
      using errcode = 'P0001';
  end if;

  if v_reason is null or length(v_reason) < 3 then
    raise exception 'Please tell the customer why the order is being cancelled.'
      using errcode = 'P0001';
  end if;

  select * into v_order from public.orders o where o.id = p_order_id for update;
  if v_order.id is null then
    raise exception 'Order not found.' using errcode = 'P0001';
  end if;

  if not exists (select 1 from public.shops s where s.id = v_order.shop_id and s.owner_id = v_uid)
     and v_order.shop_id is distinct from (select p.shop_id from public.profiles p where p.id = v_uid) then
    raise exception 'This order belongs to another shop.' using errcode = '42501';
  end if;

  if v_order.status in ('Completed', 'Cancelled') then
    raise exception 'This order is already %.', lower(v_order.status) using errcode = 'P0001';
  end if;

  -- The state-machine trigger still decides which transitions are legal.
  update public.orders o
     set status        = 'Cancelled',
         cancelled_by  = 'vendor',
         cancel_reason = v_reason
   where o.id = v_order.id;

  return 'Cancelled';
end;
$$;

revoke all on function public.vendor_cancel_order(text, text) from public, anon;
grant execute on function public.vendor_cancel_order(text, text) to authenticated;


-- -----------------------------------------------------------------------------
-- 5. Orders — the app sees and edits only its own orders
--
--    RESTRICTIVE, so they are ANDed with the existing policies: the customer
--    who placed it, the shop it belongs to, or an admin. Older orders saved
--    without user_id stay visible to the customer through their email.
-- -----------------------------------------------------------------------------
drop policy if exists "orders_select_party_only" on public.orders;
create policy "orders_select_party_only" on public.orders
  as restrictive
  for select
  to authenticated
  using (
    public.is_admin()
    or user_id = auth.uid()
    or (user_id is null and lower(customer_email) = lower(coalesce(auth.jwt() ->> 'email', '')))
    or shop_id in (select s.id from public.shops s where s.owner_id = auth.uid())
    or shop_id = (select p.shop_id from public.profiles p where p.id = auth.uid())
  );

drop policy if exists "orders_update_party_only" on public.orders;
create policy "orders_update_party_only" on public.orders
  as restrictive
  for update
  to authenticated
  using (
    public.is_admin()
    or user_id = auth.uid()
    or shop_id in (select s.id from public.shops s where s.owner_id = auth.uid())
    or shop_id = (select p.shop_id from public.profiles p where p.id = auth.uid())
  )
  with check (
    public.is_admin()
    or user_id = auth.uid()
    or shop_id in (select s.id from public.shops s where s.owner_id = auth.uid())
    or shop_id = (select p.shop_id from public.profiles p where p.id = auth.uid())
  );


-- -----------------------------------------------------------------------------
-- 6a. Products — the shop and the moderation flag are server-owned
-- -----------------------------------------------------------------------------
create or replace function public.guard_product_protected_columns()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
  if public.breakq_is_trusted_caller() then
    return new;
  end if;

  if new.shop_id is distinct from old.shop_id then
    raise exception 'A product can''t be moved to another shop.' using errcode = 'P0001';
  end if;

  if coalesce(new.is_restricted, false) is distinct from coalesce(old.is_restricted, false) then
    raise exception 'Only BreakQ can change whether a product is restricted.' using errcode = 'P0001';
  end if;

  return new;
end;
$$;

drop trigger if exists a_guard_product_protected_columns on public.products;
create trigger a_guard_product_protected_columns
  before update on public.products
  for each row
  execute function public.guard_product_protected_columns();


-- -----------------------------------------------------------------------------
-- 6b. Products — basic data checks, for every writer.
--     On UPDATE only the columns being changed are checked, so an old row with
--     bad data can still have its stock moved by an order or a cancellation.
-- -----------------------------------------------------------------------------
create or replace function public.validate_product_write()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
declare
  v_name    boolean := true;
  v_price   boolean := true;
  v_mrp     boolean := true;
  v_stock   boolean := true;
  v_weights boolean := true;
begin
  if tg_op = 'UPDATE' then
    v_name    := new.name           is distinct from old.name;
    v_price   := new.current_price  is distinct from old.current_price;
    v_mrp     := new.original_price is distinct from old.original_price;
    v_stock   := new.stock_qty      is distinct from old.stock_qty;
    v_weights := new.weight_options is distinct from old.weight_options;
  end if;

  if v_name and length(btrim(coalesce(new.name, ''))) = 0 then
    raise exception 'Product name can''t be empty.' using errcode = 'P0001';
  end if;

  if v_price and (new.current_price is null or new.current_price <= 0) then
    raise exception 'Price must be more than ₹0.' using errcode = 'P0001';
  end if;

  if v_mrp and new.original_price is not null and new.original_price < 0 then
    raise exception 'MRP can''t be negative.' using errcode = 'P0001';
  end if;

  if v_stock and new.stock_qty is not null and new.stock_qty < 0 then
    raise exception 'Stock can''t be negative.' using errcode = 'P0001';
  end if;

  if v_weights
     and jsonb_typeof(new.weight_options) = 'array'
     and exists (
       select 1
         from jsonb_array_elements(new.weight_options) w
        where w ? 'price'
          and case
                when (w ->> 'price') ~ '^\s*-?[0-9]+(\.[0-9]+)?\s*$' then (w ->> 'price')::numeric <= 0
                else true
              end
     ) then
    raise exception 'Every size needs a price above ₹0.' using errcode = 'P0001';
  end if;

  return new;
end;
$$;

drop trigger if exists a_validate_product_write on public.products;
create trigger a_validate_product_write
  before insert or update on public.products
  for each row
  execute function public.validate_product_write();


-- -----------------------------------------------------------------------------
-- 6c. Products — availability follows stock unless the writer sets it itself.
--     A manual "unavailable" with stock left on the shelf is kept.
-- -----------------------------------------------------------------------------
create or replace function public.sync_product_availability()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
  if new.stock_qty is not null
     and new.in_stock is not distinct from old.in_stock then
    if new.stock_qty = 0 then
      new.in_stock := false;
    elsif old.stock_qty = 0 and new.stock_qty > 0 then
      new.in_stock := true;
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_sync_product_availability on public.products;
create trigger trg_sync_product_availability
  before update of stock_qty on public.products
  for each row
  execute function public.sync_product_availability();


-- -----------------------------------------------------------------------------
-- 7. Products — only approved, live shops change their catalogue; customers
--    see only active, unrestricted items of approved, live shops. The owner
--    and admins still see everything of their own. (RESTRICTIVE.)
-- -----------------------------------------------------------------------------
drop policy if exists "products_write_approved_shop_insert" on public.products;
create policy "products_write_approved_shop_insert" on public.products
  as restrictive
  for insert
  to authenticated
  with check (
    public.is_admin()
    or exists (select 1 from public.shops s
                where s.id = products.shop_id
                  and s.status::text = 'approved'
                  and not coalesce(s.is_deleted, false))
  );

drop policy if exists "products_write_approved_shop_update" on public.products;
create policy "products_write_approved_shop_update" on public.products
  as restrictive
  for update
  to authenticated
  using (
    public.is_admin()
    or exists (select 1 from public.shops s
                where s.id = products.shop_id
                  and s.status::text = 'approved'
                  and not coalesce(s.is_deleted, false))
  )
  with check (
    public.is_admin()
    or exists (select 1 from public.shops s
                where s.id = products.shop_id
                  and s.status::text = 'approved'
                  and not coalesce(s.is_deleted, false))
  );

drop policy if exists "products_write_approved_shop_delete" on public.products;
create policy "products_write_approved_shop_delete" on public.products
  as restrictive
  for delete
  to authenticated
  using (
    public.is_admin()
    or exists (select 1 from public.shops s
                where s.id = products.shop_id
                  and s.status::text = 'approved'
                  and not coalesce(s.is_deleted, false))
  );

drop policy if exists "products_visible_anon" on public.products;
create policy "products_visible_anon" on public.products
  as restrictive
  for select
  to anon
  using (
    coalesce(is_active, true)
    and not coalesce(is_restricted, false)
    and exists (select 1 from public.shops s
                 where s.id = products.shop_id
                   and s.status::text = 'approved'
                   and not coalesce(s.is_deleted, false))
  );

drop policy if exists "products_visible_auth" on public.products;
create policy "products_visible_auth" on public.products
  as restrictive
  for select
  to authenticated
  using (
    public.is_admin()
    or exists (select 1 from public.shops s where s.id = products.shop_id and s.owner_id = auth.uid())
    or products.shop_id = (select p.shop_id from public.profiles p where p.id = auth.uid())
    or (
      coalesce(is_active, true)
      and not coalesce(is_restricted, false)
      and exists (select 1 from public.shops s
                   where s.id = products.shop_id
                     and s.status::text = 'approved'
                     and not coalesce(s.is_deleted, false))
    )
  );


-- -----------------------------------------------------------------------------
-- 8. Restore stock for every cancelled line, summed per product.
--    Availability is handled by trg_sync_product_availability (section 6c).
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
       set stock_qty = p.stock_qty + r.qty
      from (select oi.product_id, sum(oi.quantity)::int as qty
              from public.order_items oi
             where oi.order_id = new.id
             group by oi.product_id) r
     where p.id = r.product_id
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
-- 9. product-images — approved vendors upload; only the uploader replaces or
--    deletes. Public read is unchanged.
-- -----------------------------------------------------------------------------
drop policy if exists "product-images-vendor-insert" on storage.objects;
create policy "product-images-vendor-insert"
  on storage.objects for insert
  to authenticated
  with check (
    bucket_id = 'product-images'
    and not public.is_current_user_blocked()
    and exists (select 1 from public.shops s
                 where s.owner_id = auth.uid()
                   and s.status::text = 'approved'
                   and not coalesce(s.is_deleted, false))
  );

drop policy if exists "product-images-owner-update" on storage.objects;
create policy "product-images-owner-update"
  on storage.objects for update
  to authenticated
  using (bucket_id = 'product-images' and (owner = auth.uid() or public.is_admin()))
  with check (bucket_id = 'product-images' and (owner = auth.uid() or public.is_admin()));

drop policy if exists "product-images-owner-delete" on storage.objects;
create policy "product-images-owner-delete"
  on storage.objects for delete
  to authenticated
  using (bucket_id = 'product-images' and (owner = auth.uid() or public.is_admin()));


-- -----------------------------------------------------------------------------
-- 10. One pending or approved shop per owner
-- -----------------------------------------------------------------------------
create or replace function public.guard_one_active_shop_per_owner()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
  if public.breakq_is_trusted_caller() then
    return new;
  end if;

  if exists (select 1 from public.shops s
              where s.owner_id = auth.uid()
                and s.status::text in ('pending', 'approved')
                and not coalesce(s.is_deleted, false)) then
    raise exception 'You already have a shop registered with BreakQ.' using errcode = 'P0001';
  end if;
  return new;
end;
$$;

drop trigger if exists a_guard_one_active_shop_per_owner on public.shops;
create trigger a_guard_one_active_shop_per_owner
  before insert on public.shops
  for each row
  execute function public.guard_one_active_shop_per_owner();


-- -----------------------------------------------------------------------------
-- Verify — what this file installed.
-- -----------------------------------------------------------------------------
select 'trigger'::text as kind, c.relname::text as on_table, t.tgname::text as name
  from pg_trigger t
  join pg_class c on c.oid = t.tgrelid
 where not t.tgisinternal
   and t.tgname in ('a_guard_order_completion', 'trg_stamp_order_cancellation',
                    'a_guard_product_protected_columns', 'a_validate_product_write',
                    'trg_sync_product_availability', 'trg_restore_stock_on_cancel',
                    'a_guard_one_active_shop_per_owner')
union all
select 'policy', tablename::text, policyname::text
  from pg_policies
 where policyname in ('orders_select_party_only', 'orders_update_party_only',
                      'products_write_approved_shop_insert', 'products_write_approved_shop_update',
                      'products_write_approved_shop_delete', 'products_visible_anon',
                      'products_visible_auth', 'product-images-vendor-insert',
                      'product-images-owner-update', 'product-images-owner-delete')
union all
select 'function', 'public', 'vendor_cancel_order'
 where to_regprocedure('public.vendor_cancel_order(text, text)') is not null
order by 1, 2, 3;
