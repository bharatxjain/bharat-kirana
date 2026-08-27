-- ============================================================================
--  BreakQ — Diagnose vendor registration HTTP 400 "lat column not found"
--
--  The Kotlin app sends { name, owner_id, address, phone, lat, lng, ... } to
--  POST /rest/v1/shops. PostgREST replied with:
--     PGRST204 - Could not find the 'lat' column of 'shops' in the schema cache
--
--  Root causes ranked from most to least likely:
--    1. Schema cache is stale (column exists but PostgREST has not reloaded)
--    2. Column is named 'latitude' / 'longitude' instead of 'lat' / 'lng'
--    3. Column is truly missing
-- ============================================================================


-- A. LIST EVERY COLUMN THE SHOPS TABLE ACTUALLY HAS. Read-only.
-- Send the output to the assistant. This tells us which of the three causes we
-- are looking at.
select ordinal_position, column_name, data_type, is_nullable, column_default
from information_schema.columns
where table_schema = 'public'
  and table_name   = 'shops'
order by ordinal_position;


-- B. FORCE POSTGREST TO RELOAD ITS SCHEMA CACHE.
-- Safe to run any time; takes effect within a second. If the column truly
-- exists, this alone fixes cause (1) above.
notify pgrst, 'reload schema';


-- C. IF THE COLUMN DOES NOT EXIST, add it. Only run if step A shows lat/lng
-- are missing. Skip otherwise.
--
-- alter table public.shops add column if not exists lat  double precision;
-- alter table public.shops add column if not exists lng  double precision;
-- notify pgrst, 'reload schema';
