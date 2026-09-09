-- =============================================================================
--  BreakQ — Wishlist persistence (per-customer)
--
--  Replaces the prefs-only wishlist that was previously shared across every
--  account on the same device. RLS binds each row to auth.uid() so one
--  customer can never see another's wishlist.
--
--  Safe to re-run.
-- =============================================================================

create table if not exists public.wishlists (
  user_id     uuid        not null references auth.users(id) on delete cascade,
  product_id  text        not null,
  created_at  timestamptz not null default now(),
  primary key (user_id, product_id)
);

create index if not exists idx_wishlists_user on public.wishlists(user_id);

alter table public.wishlists enable row level security;

drop policy if exists "wishlists: owner rw" on public.wishlists;
create policy "wishlists: owner rw" on public.wishlists
  for all to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

-- Realtime replication so a customer's wishlist stays in sync if they use two
-- devices at once. Idempotent — no-op if the publication already includes it.
do $$
begin
  begin
    execute 'alter publication supabase_realtime add table public.wishlists';
  exception when duplicate_object then null;
  end;
end $$;

-- -----------------------------------------------------------------------------
-- Verify
-- -----------------------------------------------------------------------------
select policyname, cmd, qual, with_check
  from pg_policies
 where schemaname = 'public' and tablename = 'wishlists'
 order by policyname;

select count(*) as row_count from public.wishlists;
