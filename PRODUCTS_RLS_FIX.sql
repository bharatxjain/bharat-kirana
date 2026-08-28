-- Safety-net RLS policies for the `products` table so vendors can insert
-- their own products (fixes the HTTP 403 seen when listing a product).
-- Run this in the Supabase SQL editor. Idempotent — safe to run multiple times.

-- Make sure RLS is enabled but not gate-shut against authenticated vendors.
ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;

-- Everyone (including anon) can read products so the customer app works.
DROP POLICY IF EXISTS "products readable by all" ON public.products;
CREATE POLICY "products readable by all"
ON public.products
FOR SELECT
USING (true);

-- Vendors can INSERT a product for a shop they own.
DROP POLICY IF EXISTS "vendors insert own products" ON public.products;
CREATE POLICY "vendors insert own products"
ON public.products
FOR INSERT
TO authenticated
WITH CHECK (
  shop_id IN (SELECT id FROM public.shops WHERE owner_id = auth.uid())
);

-- Vendors can UPDATE their own products (price, stock, etc).
DROP POLICY IF EXISTS "vendors update own products" ON public.products;
CREATE POLICY "vendors update own products"
ON public.products
FOR UPDATE
TO authenticated
USING (
  shop_id IN (SELECT id FROM public.shops WHERE owner_id = auth.uid())
)
WITH CHECK (
  shop_id IN (SELECT id FROM public.shops WHERE owner_id = auth.uid())
);

-- Vendors can DELETE their own products.
DROP POLICY IF EXISTS "vendors delete own products" ON public.products;
CREATE POLICY "vendors delete own products"
ON public.products
FOR DELETE
TO authenticated
USING (
  shop_id IN (SELECT id FROM public.shops WHERE owner_id = auth.uid())
);

-- Sanity check: list current policies on products.
SELECT policyname, cmd, roles::text
FROM pg_policies
WHERE schemaname = 'public' AND tablename = 'products'
ORDER BY policyname;
