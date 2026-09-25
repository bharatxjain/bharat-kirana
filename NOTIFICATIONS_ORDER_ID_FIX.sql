-- =============================================================================
--  BreakQ — notifications.order_id
--
--  notify-order-status inserts `order_id` on every notification row, and the
--  app reads `order_id` back to deep-link the tap to the right order
--  (SupabaseGroceryRepo.fetchNotifications, GroceryViewModel realtime handler).
--
--  The column does not exist, so PostgREST rejects the insert. The Edge
--  Function only console.warn's the failure, so it has been failing silently:
--  order-status notifications never reach the in-app list, and taps on the
--  ones that do exist cannot open an order.
--
--  Safe to re-run.
-- =============================================================================

alter table public.notifications
  add column if not exists order_id text;

-- Deep-link taps and the unread badge both filter by user; ordering is by
-- recency. This index covers the app's only query against the table.
create index if not exists notifications_user_created_idx
  on public.notifications (user_id, created_at desc);


-- -----------------------------------------------------------------------------
-- Verify
-- -----------------------------------------------------------------------------
select column_name, data_type
  from information_schema.columns
 where table_schema = 'public' and table_name = 'notifications'
 order by ordinal_position;

-- What has actually been written so far. If no order-status titles appear
-- ("Order confirmed", "Ready for pickup!", ...) then the insert was indeed
-- failing and only push-campaign rows got through.
select title, count(*) as rows, max(created_at) as newest
  from public.notifications
 group by title
 order by newest desc
 limit 20;
