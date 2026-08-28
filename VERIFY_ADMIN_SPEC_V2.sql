-- ============================================================================
--  Verify claims in the admin panel spec v2 that I haven't independently seen.
--  All read-only.
-- ============================================================================

-- Claim 2: orders has commission_rupees and platform_fee_rupees columns.
select column_name, data_type
from information_schema.columns
where table_schema = 'public' and table_name = 'orders'
  and column_name in ('commission_rupees', 'platform_fee_rupees', 'promo_code')
order by column_name;

-- Claim 7: categories has icon_name and item_count columns.
select column_name, data_type, column_default
from information_schema.columns
where table_schema = 'public' and table_name = 'categories'
order by ordinal_position;

-- Claim 8: shop_ratings has three separate triggers all calling
-- refresh_shop_rating(). Should show exactly what fires.
select tgname, pg_get_triggerdef(oid)
from pg_trigger
where tgrelid = 'public.shop_ratings'::regclass
  and not tgisinternal;

-- Bonus: check that categories.item_count is NOT auto-maintained by any
-- trigger. If nothing shows here, the spec's warning is correct - the count
-- will drift.
select tgname
from pg_trigger
where tgrelid = 'public.categories'::regclass
  and not tgisinternal;
