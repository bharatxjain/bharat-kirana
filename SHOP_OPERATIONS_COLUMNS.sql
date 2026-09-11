-- =============================================================================
--  BreakQ — Shop operations columns (packing_time + auto_confirm)
--
--  Makes the vendor's Shop Operations toggles (packing time, auto-confirm)
--  actually persist to the DB so:
--    - A vendor's chosen packing time drives every customer's pickup ETA.
--    - The value survives a re-login and shows the same on any device.
--    - accepting_orders already exists on the row and is preserved as-is.
--
--  Idempotent. Safe to re-run.
-- =============================================================================

alter table public.shops
  add column if not exists packing_time  integer not null default 15,
  add column if not exists auto_confirm  boolean not null default true;

-- Clamp existing rows into a sane range so a bad migration or manual edit can't
-- poison the customer ETA with 0 or 999.
update public.shops
   set packing_time = 15
 where packing_time is null
    or packing_time not between 5 and 180;

-- ── Verify ─────────────────────────────────────────────────────────────────
select column_name, data_type, column_default
  from information_schema.columns
 where table_schema = 'public'
   and table_name   = 'shops'
   and column_name in ('packing_time', 'auto_confirm', 'accepting_orders')
 order by column_name;
