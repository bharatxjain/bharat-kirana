-- =============================================================================
--  BreakQ — Where did the products go?
--
--  Run this WHOLE file (Ctrl+A) and send back every result block.
--  It only reads. Nothing here changes any data.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Do the rows still exist at all? Per shop, with the flags that control
--    whether the app will display them.
-- -----------------------------------------------------------------------------
select
  s.id                                                        as shop_id,
  s.name                                                      as shop_name,
  s.status::text                                              as shop_status,
  s.is_deleted                                                as shop_deleted,
  s.accepting_orders,
  count(p.id)                                                 as products_total,
  count(*) filter (where p.is_active is not false)            as active,
  count(*) filter (where p.is_active is false)                as inactive,
  count(*) filter (where p.in_stock is true)                  as in_stock,
  count(*) filter (where p.in_stock is not true)              as out_of_stock,
  count(*) filter (where p.is_restricted)                     as restricted,
  count(*) filter (where p.stock_qty = 0)                     as qty_zero,
  count(*) filter (where p.stock_qty is null)                 as qty_untracked
from public.shops s
left join public.products p on p.shop_id = s.id
group by s.id, s.name, s.status, s.is_deleted, s.accepting_orders
order by products_total desc;


-- -----------------------------------------------------------------------------
-- 2. Every product for the shop in question, with all the display flags.
--    If rows come back here, NOTHING was deleted — they are only hidden.
-- -----------------------------------------------------------------------------
select
  p.id, p.name, p.brand,
  p.current_price, p.stock_qty, p.in_stock, p.is_active, p.is_restricted,
  p.created_at
from public.products p
where p.shop_id = 's_1787839141966'      -- Yash Lala
order by p.created_at desc;


-- -----------------------------------------------------------------------------
-- 3. Has anything actually consumed stock? Every real order against that shop.
--    If this is empty, no order ever decremented anything.
-- -----------------------------------------------------------------------------
select
  o.id, o.order_number, o.status, o.total_amount, o.created_at,
  (select count(*) from public.order_items oi where oi.order_id = o.id) as items
from public.orders o
where o.shop_id = 's_1787839141966'
order by o.created_at desc
limit 20;


-- -----------------------------------------------------------------------------
-- 4. Any leftover test rows? These should ALL return zero.
--    'TEST-' comes from my verification script, 'RACE-' from the manual
--    two-tab race test. If either exists, something did not roll back.
-- -----------------------------------------------------------------------------
select
  count(*) filter (where id like 'TEST-%') as leftover_test_orders,
  count(*) filter (where id like 'RACE-%') as leftover_race_orders
from public.orders;


-- -----------------------------------------------------------------------------
-- 5. Who owns what. Confirms the alexparker change did not touch other shops.
-- -----------------------------------------------------------------------------
select
  u.email,
  p.role,
  p.shop_id      as profile_points_at,
  s.id           as owns_shop,
  s.name         as shop_name,
  s.is_deleted,
  s.accepting_orders
from public.profiles p
join auth.users u on u.id = p.id
left join public.shops s on s.owner_id = p.id
where p.role in ('vendor', 'admin', 'super_admin')
   or s.id is not null
order by u.email;
