-- =============================================================================
--  BreakQ — Customer delivery addresses (run once in Supabase SQL Editor)
--
--  Replaces the single free-text profiles.address with a structured, per-user
--  address book. profiles.address is intentionally LEFT IN PLACE and untouched
--  so existing profile sync / vendor flows keep working unchanged.
--
--  Safe to re-run.
-- =============================================================================

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- 1. Table
-- ---------------------------------------------------------------------------
create table if not exists public.customer_addresses (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references auth.users(id) on delete cascade,

  label            text not null default 'Home',

  -- Structured components. Only house_no / area_street / city are enforced by
  -- the client; everything else is optional, so defaults keep inserts simple.
  house_no         text not null default '',
  building         text not null default '',
  floor            text not null default '',
  area_street      text not null default '',
  landmark         text not null default '',
  city             text not null default '',
  state            text not null default '',
  pincode          text not null default '',

  lat              double precision,
  lng              double precision,

  -- Contact: "Myself" copies from profiles at display time; "Someone else"
  -- stores an explicit recipient.
  is_for_self      boolean not null default true,
  recipient_name   text not null default '',
  recipient_phone  text not null default '',

  is_default       boolean not null default false,

  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),

  constraint customer_addresses_label_chk check (label in ('Home', 'Work', 'Other'))
);

create index if not exists idx_customer_addresses_user
  on public.customer_addresses (user_id, created_at desc);

-- ---------------------------------------------------------------------------
-- 1b. Reconcile an ALREADY-EXISTING table.
--     `create table if not exists` above is a no-op when the table was created
--     by an earlier version of the app, so every column is (re)asserted here.
--     All of these are no-ops on a freshly created table.
-- ---------------------------------------------------------------------------
alter table public.customer_addresses add column if not exists user_id         uuid;
alter table public.customer_addresses add column if not exists label           text    not null default 'Home';
alter table public.customer_addresses add column if not exists house_no        text    not null default '';
alter table public.customer_addresses add column if not exists building        text    not null default '';
alter table public.customer_addresses add column if not exists floor           text    not null default '';
alter table public.customer_addresses add column if not exists area_street     text    not null default '';
alter table public.customer_addresses add column if not exists landmark        text    not null default '';
alter table public.customer_addresses add column if not exists city            text    not null default '';
alter table public.customer_addresses add column if not exists state           text    not null default '';
alter table public.customer_addresses add column if not exists pincode         text    not null default '';
alter table public.customer_addresses add column if not exists lat             double precision;
alter table public.customer_addresses add column if not exists lng             double precision;
alter table public.customer_addresses add column if not exists is_for_self     boolean not null default true;
alter table public.customer_addresses add column if not exists recipient_name  text    not null default '';
alter table public.customer_addresses add column if not exists recipient_phone text    not null default '';
alter table public.customer_addresses add column if not exists is_default      boolean not null default false;
alter table public.customer_addresses add column if not exists created_at      timestamptz not null default now();
alter table public.customer_addresses add column if not exists updated_at      timestamptz not null default now();

-- Any leftover column from the previous implementation that is NOT NULL with no
-- default would reject our inserts. Relax those rather than guessing values.
do $$
declare
  col record;
begin
  for col in
    select column_name
      from information_schema.columns
     where table_schema = 'public'
       and table_name   = 'customer_addresses'
       and is_nullable  = 'NO'
       and column_default is null
       and column_name not in ('id', 'user_id')
  loop
    execute format(
      'alter table public.customer_addresses alter column %I drop not null',
      col.column_name
    );
    raise notice 'Relaxed NOT NULL on legacy column %', col.column_name;
  end loop;
end $$;

-- Label CHECK, added only if absent so pre-existing rows aren't rejected.
do $$
begin
  if not exists (
    select 1 from pg_constraint
     where conname = 'customer_addresses_label_chk'
       and conrelid = 'public.customer_addresses'::regclass
  ) then
    alter table public.customer_addresses
      add constraint customer_addresses_label_chk
      check (label in ('Home', 'Work', 'Other')) not valid;
  end if;
end $$;


-- One default address per user.
create unique index if not exists uq_customer_addresses_one_default
  on public.customer_addresses (user_id)
  where is_default;

-- ---------------------------------------------------------------------------
-- 2. Keep updated_at honest
-- ---------------------------------------------------------------------------
create or replace function public.touch_customer_address()
returns trigger
language plpgsql
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists trg_touch_customer_address on public.customer_addresses;
create trigger trg_touch_customer_address
  before update on public.customer_addresses
  for each row execute function public.touch_customer_address();

-- ---------------------------------------------------------------------------
-- 3. Promote a single address to default, atomically.
--    SECURITY DEFINER so the demote+promote pair cannot half-apply, but the
--    owner check still runs against auth.uid() — a caller can never touch
--    another user's rows.
-- ---------------------------------------------------------------------------
create or replace function public.set_default_customer_address(p_address_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then
    raise exception 'NOT_AUTHENTICATED' using errcode = 'P0001';
  end if;

  if not exists (
    select 1 from public.customer_addresses
     where id = p_address_id and user_id = v_user
  ) then
    raise exception 'NOT_YOUR_ADDRESS' using errcode = 'P0001';
  end if;

  update public.customer_addresses
     set is_default = false
   where user_id = v_user and is_default;

  update public.customer_addresses
     set is_default = true
   where id = p_address_id and user_id = v_user;
end;
$$;

revoke all on function public.set_default_customer_address(uuid) from public;
grant execute on function public.set_default_customer_address(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 4. RLS — owner-only, all four operations
-- ---------------------------------------------------------------------------
alter table public.customer_addresses enable row level security;

drop policy if exists "customer reads own addresses"   on public.customer_addresses;
drop policy if exists "customer inserts own addresses" on public.customer_addresses;
drop policy if exists "customer updates own addresses" on public.customer_addresses;
drop policy if exists "customer deletes own addresses" on public.customer_addresses;

create policy "customer reads own addresses"
  on public.customer_addresses for select
  to authenticated
  using (user_id = auth.uid());

create policy "customer inserts own addresses"
  on public.customer_addresses for insert
  to authenticated
  with check (user_id = auth.uid());

create policy "customer updates own addresses"
  on public.customer_addresses for update
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "customer deletes own addresses"
  on public.customer_addresses for delete
  to authenticated
  using (user_id = auth.uid());

-- ---------------------------------------------------------------------------
-- 5. Verify
-- ---------------------------------------------------------------------------
select policyname, cmd, qual, with_check
  from pg_policies
 where schemaname = 'public' and tablename = 'customer_addresses'
 order by policyname;
