-- Inspect the 2 rows still in promo_codes — decide whether to keep them.
-- Read-only.
select code, description, discount_pct, discount_flat, min_order,
       valid_from, valid_to, usage_limit, times_used
from public.promo_codes
order by code;
