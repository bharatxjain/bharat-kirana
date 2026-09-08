-- =============================================================================
--  BreakQ — Order creation atomicity + RLS policy cleanup
--
--  Task 5: Wrap orders + order_items INSERT in one transaction via an RPC.
--  Task 8: Drop obsolete duplicate policies on public.orders and
--          public.customer_addresses.
--
--  Safe to re-run.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Task 5: create_order_with_items
--
-- SECURITY INVOKER (the default) so RLS still applies. The caller must be an
-- authenticated user whose auth.uid() matches the p_order.user_id — enforced
-- by the existing "Customer can insert orders" WITH CHECK.
--
-- The order.id is client-generated (unchanged behaviour); the BEFORE INSERT
-- trigger `trg_assign_order_pickup_fields` populates order_number and
-- pickup_token. We return those so the client can render the correct QR/label
-- without a follow-up SELECT.
-- -----------------------------------------------------------------------------
create or replace function public.create_order_with_items(
  p_order jsonb,
  p_items jsonb default '[]'::jsonb
)
returns table(order_number int, pickup_token text)
language plpgsql
as $$
declare
  v_order_id text := p_order->>'id';
begin
  if v_order_id is null or v_order_id = '' then
    raise exception 'MISSING_ORDER_ID' using errcode = 'P0001';
  end if;

  insert into public.orders (
    id,
    customer_name,
    customer_email,
    customer_mobile,
    total_amount,
    status,
    order_date,
    qr_code_payload,
    items_json,
    user_id,
    shop_id,
    promo_code,
    promo_discount
  ) values (
    v_order_id,
    coalesce(p_order->>'customer_name', ''),
    lower(trim(coalesce(p_order->>'customer_email', ''))),
    coalesce(p_order->>'customer_mobile', ''),
    coalesce((p_order->>'total_amount')::int, 0),
    coalesce(p_order->>'status', 'Order Placed'),
    coalesce(p_order->>'order_date', ''),
    coalesce(p_order->>'qr_code_payload', ''),
    coalesce(p_order->'items_json', '[]'::jsonb),
    nullif(p_order->>'user_id', '')::uuid,
    nullif(p_order->>'shop_id', ''),
    nullif(p_order->>'promo_code', ''),
    coalesce((p_order->>'promo_discount')::int, 0)
  );

  -- One INSERT SELECT across the items array. Failure of any row (bad column,
  -- CHECK violation, RLS reject) aborts the whole function and rolls back the
  -- parent orders row — atomicity, achieved.
  if jsonb_array_length(coalesce(p_items, '[]'::jsonb)) > 0 then
    insert into public.order_items (
      order_id,
      product_id,
      product_name,
      brand,
      image_url,
      weight_label,
      unit_price,
      quantity,
      line_total
    )
    select
      v_order_id,
      item->>'product_id',
      coalesce(item->>'product_name', ''),
      coalesce(item->>'brand', ''),
      coalesce(item->>'image_url', ''),
      coalesce(item->>'weight_label', ''),
      coalesce((item->>'unit_price')::int, 0),
      coalesce((item->>'quantity')::int, 1),
      coalesce((item->>'line_total')::int, 0)
    from jsonb_array_elements(coalesce(p_items, '[]'::jsonb)) as item;
  end if;

  return query
    select o.order_number, o.pickup_token
      from public.orders o
     where o.id = v_order_id;
end;
$$;

revoke all on function public.create_order_with_items(jsonb, jsonb) from public;
grant execute on function public.create_order_with_items(jsonb, jsonb) to authenticated;

-- -----------------------------------------------------------------------------
-- Task 8: drop obsolete duplicate policies
--
-- All KEEP decisions are documented in the code review. Nothing is removed
-- that changes the observable access model. Removing these tightens the
-- policy list and makes future audits truthful.
-- -----------------------------------------------------------------------------

-- orders: `Customers can view own orders` uses `auth.uid() = customer_id`, but
-- customer_id is NULL on every existing row. The permissive "Customer can view
-- own orders" (based on user_id) already covers the correct case.
drop policy if exists "Customers can view own orders" on public.orders;

-- customer_addresses: the CUSTOMER_ADDRESSES_MIGRATION.sql migration replaced
-- these with identical named policies. The old snake_case copies are dead
-- weight and clutter pg_policies.
drop policy if exists customer_addresses_select_own on public.customer_addresses;
drop policy if exists customer_addresses_insert_own on public.customer_addresses;
drop policy if exists customer_addresses_update_own on public.customer_addresses;
drop policy if exists customer_addresses_delete_own on public.customer_addresses;

-- -----------------------------------------------------------------------------
-- Verify
-- -----------------------------------------------------------------------------
select policyname, cmd, permissive, qual
  from pg_policies
 where schemaname = 'public'
   and tablename in ('orders', 'customer_addresses')
 order by tablename, cmd, policyname;
