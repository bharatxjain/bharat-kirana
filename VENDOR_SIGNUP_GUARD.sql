-- =============================================================================
--  BreakQ — One account is either a shop or a shopper, never both
--
--  Policy (decided 2026-09-24): the role is chosen at signup. There is no
--  in-app switch from customer to vendor.
--
--  The "Register Your Shop" button was removed from the customer profile, but
--  the database never got the memo: link_shop_to_profile still promotes ANYONE
--  who inserts a row into shops. A modified APK, or plain curl with a valid
--  login, can still convert a customer account into a vendor account — and the
--  moment that happens the app routes them to the vendor dashboard and their
--  entire order history becomes unreachable. That is exactly what happened to
--  itzalexparker2001@gmail.com.
--
--  WHAT THIS DOES NOT DO
--  It does not require role='vendor' before the insert. It cannot: a Google
--  signup that picks "Vendor" is still role='customer' at that point, because
--  RoleSelectionScreen only navigates and never writes the role. Requiring it
--  would break Google vendor signup completely.
--
--  Nor is self-promotion a privilege escalation on its own — a new shop lands
--  as status='pending' and create_order_with_items refuses any shop that is not
--  approved. An admin still has to say yes.
--
--  WHAT THIS DOES
--  Blocks the one outcome that actually loses data: an account that has already
--  shopped cannot become a shop. Fresh accounts are unaffected, so every
--  legitimate signup path keeps working.
--
--  Safe to re-run.
-- =============================================================================

create or replace function public.guard_shop_owner_has_no_orders()
returns trigger
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_orders int;
begin
  if new.owner_id is null then
    return new;
  end if;

  select count(*) into v_orders
    from public.orders o
   where o.user_id = new.owner_id;

  if v_orders > 0 then
    raise exception
      'This account has already placed % order(s) as a customer, so it cannot be turned into a shop. Please register your shop with a different email.',
      v_orders
      using errcode = 'P0001';
  end if;

  return new;
end;
$$;

drop trigger if exists trg_guard_shop_owner_has_no_orders on public.shops;
create trigger trg_guard_shop_owner_has_no_orders
  before insert on public.shops
  for each row
  execute function public.guard_shop_owner_has_no_orders();


-- -----------------------------------------------------------------------------
-- Verify. Rolls back — no shop is created. The result prints as the LAST
-- table at the bottom of this file (the SQL editor hides RAISE NOTICE output).
-- -----------------------------------------------------------------------------
drop table if exists public.breakq_guard_results;
create table public.breakq_guard_results (test text, result text, detail text);

do $$
declare
  v_customer uuid;
  v_msg      text;
  v_ok       boolean := false;
begin
  -- A customer who has actually ordered. Exactly the account we want blocked.
  select o.user_id into v_customer
    from public.orders o
   where o.user_id is not null
   limit 1;

  if v_customer is null then
    insert into public.breakq_guard_results values
      ('Customer with orders cannot open a shop', 'SKIP', 'No customer with orders found.');
    return;
  end if;

  begin
    insert into public.shops (id, name, address, owner_id)
    values ('GUARDTEST-' || gen_random_uuid()::text, 'Guard Test', 'nowhere', v_customer);
    v_ok := true;
    raise exception using errcode = 'ZZ001', message = 'rollback';
  exception
    when sqlstate 'ZZ001' then null;
    when others then v_msg := sqlerrm;
  end;

  insert into public.breakq_guard_results values (
    'Customer with orders cannot open a shop',
    case when v_ok then 'FAIL' else 'PASS' end,
    coalesce(v_msg, 'a customer with order history was still able to create a shop'));
end $$;


-- -----------------------------------------------------------------------------
-- Who would this block today? Accounts that own a shop AND have customer
-- orders are already in the mixed state this prevents going forward.
-- -----------------------------------------------------------------------------
select
  u.email,
  p.role,
  s.id                                                          as owns_shop,
  s.name                                                        as shop_name,
  (select count(*) from public.orders o where o.user_id = u.id) as customer_orders
from public.shops s
join auth.users u      on u.id = s.owner_id
left join public.profiles p on p.id = u.id
where (select count(*) from public.orders o where o.user_id = u.id) > 0
order by customer_orders desc;


-- -----------------------------------------------------------------------------
-- Guard self-test result. Must say PASS.
-- Tidy up when done:  drop table public.breakq_guard_results;
-- -----------------------------------------------------------------------------
select * from public.breakq_guard_results;
