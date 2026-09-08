-- =============================================================================
--  BreakQ — Fix customer cancel RLS policy
--
--  The old "Customer can cancel own order" policy had:
--    USING      : (auth.uid() = user_id) AND status IN ('Order Placed','Preparing')
--    WITH CHECK : null   -- Postgres treats this as WITH CHECK (USING)
--
--  So a customer PATCHing status = 'Cancelled' failed the implicit WITH CHECK
--  (target status is not one of the allowed ones). PostgREST reported HTTP 204
--  with 0 rows updated, which the client used to treat as success — hence the
--  "Cancel button reappears" symptom on customer, and the "order still active"
--  symptom on vendor.
--
--  This rewrite:
--    - USING      restricted to the two pre-terminal states the UI exposes
--                 Cancel for: 'Order Placed' and 'Order Confirmed'.
--    - WITH CHECK requires the new status to be exactly 'Cancelled'. Customers
--                 can no longer smuggle other status writes through this policy.
--
--  The M1 trigger continues to block any exit from Cancelled/Completed so the
--  customer can't retroactively re-cancel a terminal order into something else.
--
--  Safe to re-run.
-- =============================================================================

drop policy if exists "Customer can cancel own order" on public.orders;

create policy "Customer can cancel own order"
  on public.orders
  for update
  using (
    auth.uid() = user_id
    and status in ('Order Placed', 'Order Confirmed')
  )
  with check (
    auth.uid() = user_id
    and status = 'Cancelled'
  );

-- -----------------------------------------------------------------------------
-- Verify
-- -----------------------------------------------------------------------------
select policyname, cmd, qual, with_check
  from pg_policies
 where schemaname = 'public'
   and tablename = 'orders'
   and cmd = 'UPDATE'
 order by policyname;
