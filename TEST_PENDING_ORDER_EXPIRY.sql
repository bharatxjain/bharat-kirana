-- =============================================================================
--  BreakQ — Tests for PENDING_ORDER_EXPIRY_1H.sql
--
--  RUN THE WHOLE FILE AT ONCE, after PENDING_ORDER_EXPIRY_1H.sql.
--
--  It places 6 test orders, back-dates them, moves them to different statuses,
--  runs the real expire_pending_orders() and checks what was cancelled.
--  Everything is rolled back: no test order is saved, no real order is
--  cancelled, no stock changes and no push/email is sent.
--
--  PASS = works as intended.  FAIL = broken.  ERROR = the test itself failed.
--
--  Tidy up afterwards:  drop table public.breakq_expiry_results;
-- =============================================================================

drop table if exists public.breakq_expiry_results;
create table public.breakq_expiry_results (seq int, test text, result text, detail text);

do $$
declare
  v_shop       text;
  v_owner      uuid;
  v_product    text;
  v_weight     text;
  v_customer   uuid;
  v_items      jsonb;
  v_ids        text[] := array[]::text[];
  v_tokens     text[] := array[]::text[];
  v_id         text;
  v_tok        text;
  v_status     text[];
  v_cancel_at  timestamptz;
  v_n          int;
  v_mid        int;
  v_after      int;
  v_restored   int;
  v_msg        text;
  v_src        text;
  v_schedule   text;
  v_active     boolean;
  v_command    text;
  i            int;
begin
  ---------------------------------------------------------------------------
  -- The live function and job.
  ---------------------------------------------------------------------------
  select p.prosrc into v_src
    from pg_proc p where p.oid = 'public.expire_pending_orders()'::regprocedure;
  insert into public.breakq_expiry_results values (1, 'Expiry limit is 1 hour',
    case when v_src like '%interval ''1 hour''%' and v_src not like '%3 hours%' then 'PASS' else 'FAIL' end,
    case when v_src like '%interval ''1 hour''%' then 'limit = 1 hour' else 'limit is not 1 hour — run PENDING_ORDER_EXPIRY_1H.sql' end);

  select j.schedule, j.active, j.command into v_schedule, v_active, v_command
    from cron.job j where j.jobname = 'expire-pending-orders';
  insert into public.breakq_expiry_results values (2, 'Scheduled job still runs the expiry',
    case when v_active and v_command ilike '%expire_pending_orders()%' then 'PASS' else 'FAIL' end,
    format('schedule=%s active=%s', coalesce(v_schedule, 'missing'), coalesce(v_active::text, '-')));

  ---------------------------------------------------------------------------
  -- Fixtures: an approved, open shop with a known owner and a stock-tracked
  -- product (qty >= 6); a customer.
  ---------------------------------------------------------------------------
  select p.shop_id, s.owner_id, p.id, coalesce(p.weight_options->0->>'label', p.unit)
    into v_shop, v_owner, v_product, v_weight
    from public.products p
    join public.shops s
      on s.id = p.shop_id
     and s.status::text = 'approved'
     and coalesce(s.is_deleted, false) = false
     and coalesce(s.accepting_orders, true) = true
     and s.owner_id is not null
   where coalesce(p.in_stock, true)
     and coalesce(p.is_active, true)
     and not coalesce(p.is_restricted, false)
     and p.stock_qty is not null and p.stock_qty >= 6
   order by p.stock_qty desc
   limit 1;

  select pr.id into v_customer
    from public.profiles pr join auth.users u on u.id = pr.id
   where pr.role = 'customer' and coalesce(pr.is_blocked, false) = false
     and pr.id is distinct from v_owner
   limit 1;

  if v_product is null or v_customer is null then
    insert into public.breakq_expiry_results values (0, 'Fixtures', 'BLOCKED',
      'Need an approved open shop with a stock-tracked product (qty >= 6) and a customer.');
    return;
  end if;

  insert into public.breakq_expiry_results values (0, 'Fixtures', 'INFO',
    format('shop=%s product=%s customer=%s', v_shop, v_product, v_customer));

  v_items := jsonb_build_array(jsonb_build_object(
    'product_id', v_product, 'weight_label', v_weight, 'quantity', 1));

  ---------------------------------------------------------------------------
  -- Orders:  1 = pending, placed 61 min ago     2 = pending, placed 59 min ago
  --          3 = Confirmed, 2 h ago             4 = Preparing, 2 h ago
  --          5 = Ready for Pickup, 2 h ago      6 = Completed, 2 h ago
  -- now() is fixed for the whole transaction, so the 1-hour edge is exact.
  ---------------------------------------------------------------------------
  begin
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_customer, 'role', 'authenticated')::text, true);
    for i in 1..6 loop
      v_id := 'EXPTEST-' || gen_random_uuid()::text;
      select r.pickup_token into v_tok
        from public.create_order_with_items(jsonb_build_object('id', v_id, 'shop_id', v_shop), v_items) r;
      v_ids := v_ids || v_id;
      v_tokens := v_tokens || v_tok;
    end loop;

    update public.orders set created_at = now() - interval '61 minutes' where id = v_ids[1];
    update public.orders set created_at = now() - interval '59 minutes' where id = v_ids[2];
    update public.orders set created_at = now() - interval '2 hours'    where id = any(v_ids[3:6]);
    update public.orders set status = 'Order Confirmed'  where id = any(v_ids[3:6]);
    update public.orders set status = 'Preparing'        where id = any(v_ids[4:6]);
    update public.orders set status = 'Ready for Pickup' where id = any(v_ids[5:6]);
    perform set_config('request.jwt.claims',
      json_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    perform public.complete_order_by_pickup_token(v_tokens[6]);

    select stock_qty into v_mid from public.products where id = v_product;
    v_n := public.expire_pending_orders();
    select stock_qty into v_after from public.products where id = v_product;

    -- Units released by every order this run cancelled (cancelled_at = this transaction's now()).
    select coalesce(sum(oi.quantity), 0)::int into v_restored
      from public.order_items oi
      join public.orders o on o.id = oi.order_id
     where oi.product_id = v_product
       and o.status = 'Cancelled'
       and o.cancelled_at = now();

    select array_agg(o.status order by array_position(v_ids, o.id)) into v_status
      from public.orders o where o.id = any(v_ids);
    select o.cancelled_at into v_cancel_at from public.orders o where o.id = v_ids[1];
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;

  if v_msg is not null then
    insert into public.breakq_expiry_results values (3, 'Test setup / expiry run', 'ERROR', v_msg);
    return;
  end if;

  insert into public.breakq_expiry_results values
    (3, 'Pending order older than 1 hour (61 min) is cancelled',
     case when v_status[1] = 'Cancelled' and v_cancel_at is not null then 'PASS' else 'FAIL' end,
     format('status=%s cancelled_at set=%s', v_status[1], v_cancel_at is not null)),
    (4, 'Pending order newer than 1 hour (59 min) is NOT cancelled',
     case when v_status[2] = 'Order Placed' then 'PASS' else 'FAIL' end,
     format('status=%s', v_status[2])),
    (5, 'Confirmed order (2 h old) is NOT cancelled',
     case when v_status[3] = 'Order Confirmed' then 'PASS' else 'FAIL' end,
     format('status=%s', v_status[3])),
    (6, 'Preparing order (2 h old) is NOT cancelled',
     case when v_status[4] = 'Preparing' then 'PASS' else 'FAIL' end,
     format('status=%s', v_status[4])),
    (7, 'Ready for Pickup order (2 h old) is NOT cancelled',
     case when v_status[5] = 'Ready for Pickup' then 'PASS' else 'FAIL' end,
     format('status=%s', v_status[5])),
    (8, 'Completed order (2 h old) is NOT cancelled',
     case when v_status[6] = 'Completed' then 'PASS' else 'FAIL' end,
     format('status=%s', v_status[6])),
    (9, 'Stock comes back for expired orders',
     case when v_restored >= 1 and v_after = v_mid + v_restored then 'PASS' else 'FAIL' end,
     format('stock before expiry=%s, after=%s, units released=%s', v_mid, v_after, v_restored)),
    (10, 'Orders the job would cancel right now', 'INFO',
     format('%s (1 test order + %s real). Rolled back — nothing was cancelled.', v_n, v_n - 1));
end $$;


-- -----------------------------------------------------------------------------
-- Results.
-- -----------------------------------------------------------------------------
select seq, test, result, detail from public.breakq_expiry_results order by seq;
