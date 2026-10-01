-- =============================================================================
-- INSPECT_STEP5.sql  (read-only — changes nothing)
--
-- Answers the open Step 5 questions in one result table:
--   1. profiles / notifications / promo_codes / device_tokens policies
--      (can customers list every promo code? can they clear notifications?)
--   2. Tables with no DELETE rule (deleting from the app silently does nothing)
--   3. What happens to each table's rows if an auth user is deleted
--      (needed before deciding how real account deletion should work)
--   4. Whether a support / contact-message table exists
-- =============================================================================

with pol as (
  select tablename::text as tbl, policyname::text as name, cmd::text as cmd,
         permissive::text as mode, array_to_string(roles, ',') as roles,
         coalesce(qual, '') as using_rule
    from pg_policies
   where schemaname = 'public'
     and tablename in ('profiles', 'notifications', 'promo_codes', 'device_tokens')
),
fk as (
  select c.conrelid::regclass::text as tbl,
         c.confrelid::regclass::text as target,
         (select string_agg(a.attname::text, ',')
            from pg_attribute a
           where a.attrelid = c.conrelid and a.attnum = any (c.conkey)) as cols,
         case c.confdeltype
           when 'c' then 'CASCADE (rows are deleted)'
           when 'n' then 'SET NULL'
           when 'd' then 'SET DEFAULT'
           when 'r' then 'RESTRICT (blocks the delete)'
           else 'NO ACTION (blocks the delete)'
         end as on_delete
    from pg_constraint c
   where c.contype = 'f'
     and c.confrelid in ('auth.users'::regclass, 'public.profiles'::regclass)
)
select 1 as seq, 'policy' as kind, tbl as on_table,
       name || ' [' || cmd || ', ' || lower(mode) || ', ' || roles || ']' as detail,
       left(using_rule, 200) as rule
  from pol
union all
select 2, 'no DELETE rule', t, 'app deletes on this table change 0 rows', ''
  from unnest(array['profiles', 'notifications', 'device_tokens']) as t
 where not exists (select 1 from pol where pol.tbl = t and pol.cmd in ('DELETE', 'ALL'))
union all
select 3, 'linked to ' || target, tbl, cols || ' -> on delete ' || on_delete, ''
  from fk
union all
select 4, 'support table', c.relname::text, 'exists', ''
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public' and c.relkind = 'r'
   and (c.relname ilike '%contact%' or c.relname ilike '%support%' or c.relname ilike '%ticket%')
order by 1, 3, 4;
