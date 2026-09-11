-- =============================================================================
--  BreakQ — Storage buckets for shop hero photos + business proofs
--
--  Fixes the "shop image never appears" bug from the storage side:
--    - `shop-images` is PUBLIC. Customer devices can render a shop's hero
--      photo without needing an access token. Vendor-only uploads.
--    - `shop-documents` stays PRIVATE. Business proof PDFs / photos live
--      here and are only reachable via the vendor's own JWT or an admin.
--
--  This migration is fully idempotent — safe to re-run. It does NOT weaken
--  existing RLS on any table. It does NOT touch existing objects; it only
--  makes sure the buckets exist and the read/write policies are correct.
-- =============================================================================

-- ── 1. Ensure both buckets exist with the right visibility ─────────────────
insert into storage.buckets (id, name, public)
values ('shop-images', 'shop-images', true)
on conflict (id) do update set public = excluded.public;

insert into storage.buckets (id, name, public)
values ('shop-documents', 'shop-documents', false)
on conflict (id) do update set public = excluded.public;

-- ── 2. Policies for shop-images (public read, authenticated write) ─────────
drop policy if exists "shop-images-public-read"        on storage.objects;
drop policy if exists "shop-images-authenticated-insert" on storage.objects;
drop policy if exists "shop-images-authenticated-update" on storage.objects;
drop policy if exists "shop-images-authenticated-delete" on storage.objects;

create policy "shop-images-public-read"
  on storage.objects for select
  using (bucket_id = 'shop-images');

create policy "shop-images-authenticated-insert"
  on storage.objects for insert
  to authenticated
  with check (bucket_id = 'shop-images');

create policy "shop-images-authenticated-update"
  on storage.objects for update
  to authenticated
  using (bucket_id = 'shop-images')
  with check (bucket_id = 'shop-images');

create policy "shop-images-authenticated-delete"
  on storage.objects for delete
  to authenticated
  using (bucket_id = 'shop-images');

-- ── 3. Policies for shop-documents (private — owner + admin only) ──────────
drop policy if exists "shop-documents-owner-read"   on storage.objects;
drop policy if exists "shop-documents-owner-insert" on storage.objects;
drop policy if exists "shop-documents-owner-update" on storage.objects;
drop policy if exists "shop-documents-admin-read"   on storage.objects;

create policy "shop-documents-owner-read"
  on storage.objects for select
  to authenticated
  using (
    bucket_id = 'shop-documents'
    and owner = auth.uid()
  );

create policy "shop-documents-owner-insert"
  on storage.objects for insert
  to authenticated
  with check (
    bucket_id = 'shop-documents'
    and owner = auth.uid()
  );

create policy "shop-documents-owner-update"
  on storage.objects for update
  to authenticated
  using (bucket_id = 'shop-documents' and owner = auth.uid())
  with check (bucket_id = 'shop-documents' and owner = auth.uid());

create policy "shop-documents-admin-read"
  on storage.objects for select
  to authenticated
  using (
    bucket_id = 'shop-documents'
    and public.is_admin()
  );

-- ── Verify ─────────────────────────────────────────────────────────────────
select id, public from storage.buckets where id in ('shop-images', 'shop-documents');

select policyname, cmd
  from pg_policies
 where schemaname = 'storage'
   and tablename  = 'objects'
   and policyname like 'shop-%'
 order by policyname;
