-- Show every account and whether they're linked to a real shop.
-- Read-only.
select
  u.email,
  u.created_at::timestamp(0) as signed_up,
  p.role,
  p.shop_id,
  s.name as shop_name,
  s.status as shop_status,
  case
    when p.shop_id is null                         then 'plain customer'
    when p.shop_id is not null and s.id is null    then 'ORPHAN: shop_id set but shop does not exist'
    when s.status <> 'approved'                    then 'shop exists but status=' || s.status
    else                                                'vendor - linked to approved shop'
  end as state
from auth.users u
left join public.profiles p on p.id = u.id
left join public.shops s on s.id = p.shop_id
order by u.created_at desc;
