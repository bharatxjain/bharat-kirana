-- Check the state of any account after signup. Change the email at the bottom.
-- Read-only. Shows what the DB thinks vs what the app would route to.
select
  u.email,
  u.raw_user_meta_data ->> 'full_name' as meta_name,
  u.raw_user_meta_data ->> 'mobile'    as meta_mobile,
  p.role,
  p.shop_id,
  p.profile_completed,
  s.name as shop_name,
  case
    when p.role = 'admin'          then 'admin - would route to admin panel'
    when p.shop_id is not null and s.id is null
                                    then 'BROKEN: shop_id points at missing shop'
    when p.shop_id is not null     then 'vendor - would route to VendorDashboard'
    else                                 'customer OR vendor-in-progress - routing depends on which button they picked at signup'
  end as diagnosis
from auth.users u
left join public.profiles p on p.id = u.id
left join public.shops s on s.id = p.shop_id
where u.email = 'YOUR_TEST_EMAIL@gmail.com';   -- put the vendor test email here

