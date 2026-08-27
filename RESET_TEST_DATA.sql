-- ============================================================================
--  BreakQ — Wipe all test data, keep schema
--
--  WHAT THIS DOES
--    - Deletes every row from every public table (orders, products, shops,
--      profiles, ratings, subscriptions, analytics, notifications, ...)
--    - Deletes every user from auth.users so the trigger can rebuild profiles
--      cleanly on next signup
--    - Resets wallet_balance / loyalty_points defaults from 150 / 350 to 0
--
--  WHAT IT DOES NOT DO
--    - No tables dropped. No columns dropped. No RLS policies touched.
--      No functions or triggers changed. Schema is untouched.
--    - Nothing in Supabase Storage is cleared (uploaded shop images survive).
--
--  IRREVERSIBLE. Everyone signed up on your two devices will be logged out
--  and their accounts gone. Re-sign-up required. Confirmed by user 2026-08-27.
--
--  RUN THE SECTIONS ONE AT A TIME. Highlight each and press Run.
-- ============================================================================


-- 0. PREVIEW — what will be affected. Read-only. --------------------------------
select table_name,
       (xpath('/row/c/text()',
              query_to_xml(format('select count(*) as c from public.%I', table_name),
                           false, true, '')))[1]::text::int as row_count
from information_schema.tables
where table_schema = 'public'
  and table_type   = 'BASE TABLE'
order by table_name;

select 'auth.users' as source, count(*) as row_count from auth.users;


-- 1. WIPE PUBLIC ROW DATA -------------------------------------------------------
-- CASCADE clears any table with a foreign key into these four, so ratings,
-- subscriptions, analytics and notifications all go too — even ones I did not
-- list explicitly. RESTART IDENTITY resets any auto-increment sequences.
truncate
  public.orders,
  public.products,
  public.shops,
  public.profiles
restart identity cascade;


-- 2. WIPE AUTH USERS ------------------------------------------------------------
-- Removes login accounts too. Without this, existing users could still sign in
-- and the handle_new_user trigger would silently recreate blank profile rows.

-- 2a. First identify every table that has a foreign key into auth.users.
--     Any row here blocks `delete from auth.users`, and we want to see the
--     full list rather than discover them one error at a time.
select conrelid::regclass::text as referring_table,
       conname                  as constraint_name,
       pg_get_constraintdef(oid) as definition
from pg_constraint
where contype = 'f'
  and confrelid = 'auth.users'::regclass
order by referring_table;

-- 2b. Truncate every public table that references auth.users. CASCADE picks up
--     anything they in turn reference. Extend this list if 2a shows more.
truncate public.notifications restart identity cascade;

-- 2c. Now the delete can succeed.
delete from auth.users;


-- 3. RESET THE DEMO DEFAULTS ----------------------------------------------------
-- Every new signup was starting with ₹150 and 350 loyalty points, because the
-- table defaults were left at demo values.
alter table public.profiles alter column wallet_balance set default 0;
alter table public.profiles alter column loyalty_points set default 0;


-- 4. VERIFY — expect zeros everywhere ------------------------------------------
select table_name,
       (xpath('/row/c/text()',
              query_to_xml(format('select count(*) as c from public.%I', table_name),
                           false, true, '')))[1]::text::int as row_count
from information_schema.tables
where table_schema = 'public'
  and table_type   = 'BASE TABLE'
order by table_name;

select 'auth.users' as source, count(*) as row_count from auth.users;

-- And confirm the defaults are gone.
select column_name, column_default
from information_schema.columns
where table_schema = 'public'
  and table_name   = 'profiles'
  and column_name in ('wallet_balance', 'loyalty_points');
-- ============================================================================
