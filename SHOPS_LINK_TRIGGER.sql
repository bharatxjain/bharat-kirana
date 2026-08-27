-- ============================================================================
--  BreakQ — Wire vendor registration end-to-end
--
--  Two fixes required after PROFILES_RLS_FIX.sql section 4:
--
--  A. Server-side linkage. The client used to PATCH profiles.shop_id itself,
--     but section 4 revoked UPDATE on that column (correctly — a customer must
--     not be able to promote themselves by writing shop_id). This trigger
--     bridges the gap: it runs SECURITY DEFINER after every shops INSERT and
--     writes the linkage server-side, so the client never touches shop_id.
--
--  B. Diagnostic to help pick the right filter value for approved shops.
--     The DB default might be lowercase 'pending' or uppercase 'PENDING'; the
--     app filter needs to match.
--
--  Safe to re-run.
-- ============================================================================


-- A. LINK-SHOP TRIGGER ---------------------------------------------------------
create or replace function public.link_shop_to_profile()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  update public.profiles
  set shop_id = new.id
  where id = new.owner_id
    -- Only fill an empty slot. If the vendor somehow already owns a shop, this
    -- leaves them pointed at the original so we do not silently swap them.
    and (shop_id is null or shop_id = new.id);
  return new;
end;
$$;

drop trigger if exists on_shop_insert_link_profile on public.shops;
create trigger on_shop_insert_link_profile
  after insert on public.shops
  for each row
  when (new.owner_id is not null)
  execute function public.link_shop_to_profile();


-- B. DIAGNOSTIC — what is the shops.status default and what values exist? -----
-- Read-only. The app parser expects lowercase (defaults to 'pending' when the
-- column is missing). If this returns something else, we need to match it.
select column_name, data_type, column_default, udt_name
from information_schema.columns
where table_schema = 'public'
  and table_name   = 'shops'
  and column_name  = 'status';

-- Any existing rows and their case. Empty table right now, so this may be blank.
select distinct status from public.shops;


-- C. VERIFY TRIGGER INSTALLED --------------------------------------------------
select tgname, tgenabled, tgtype
from pg_trigger
where tgrelid = 'public.shops'::regclass
  and not tgisinternal
order by tgname;
-- ============================================================================
