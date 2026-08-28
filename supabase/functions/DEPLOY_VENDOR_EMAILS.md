# Vendor email flow — deployment steps

Two Edge Functions and two Database Webhooks. All configured in the Supabase
Dashboard, no CLI required.

## 1. Confirm secrets are set

Supabase Dashboard → **Project Settings → Edge Functions → Secrets**. These
should already be there from the earlier step:

- `RESEND_API_KEY`  (starts with `re_`)
- `ADMIN_EMAIL`  (verify: `bjain5329@email.com` — is that a typo for `.gmail.com`?)
- `FROM_EMAIL`  (`onboarding@resend.dev` for now, or your verified domain)
- `APP_NAME`  (`BreakQ`)
- `ADMIN_PANEL_URL`  (`https://breakq.app/admin`)

`SUPABASE_URL` and `SERVICE_ROLE_KEY` are always available automatically;
you don't need to set them.

## 2. Deploy the two functions

Dashboard → **Edge Functions → Create a new function**.

### Function A: `vendor-registered`
- Name it exactly `vendor-registered`
- Paste the contents of `supabase/functions/vendor-registered/index.ts`
- Click **Deploy**

### Function B: `vendor-status-changed`
- Name it exactly `vendor-status-changed`
- Paste the contents of `supabase/functions/vendor-status-changed/index.ts`
- Click **Deploy**

Both should show status "Active" after a few seconds.

## 3. Wire the Database Webhooks

Dashboard → **Database → Webhooks → Create a new hook**.

### Webhook A — fires when a shop is registered

| Field | Value |
|---|---|
| Name | `on-shop-insert-vendor-registered` |
| Table | `public.shops` |
| Events | `INSERT` only |
| Type | Supabase Edge Functions |
| Edge Function | `vendor-registered` |
| Method | POST |
| HTTP Headers | (leave default) |

Click **Create webhook**.

### Webhook B — fires when a shop's status changes

| Field | Value |
|---|---|
| Name | `on-shop-update-status-changed` |
| Table | `public.shops` |
| Events | `UPDATE` only |
| Type | Supabase Edge Functions |
| Edge Function | `vendor-status-changed` |
| Method | POST |
| HTTP Headers | (leave default) |

Click **Create webhook**.

The function itself checks whether the status changed and only sends email
when it did; it's fine to receive UPDATEs for other columns.

## 4. Test end to end

1. Sign up a new vendor in the app, complete OTP, submit shop registration
2. Within a few seconds:
   - Your admin email should get "New vendor: {name}"
   - The vendor email should get "Registration received"
3. Open Supabase Dashboard → **Table Editor → shops** → find the row → change
   `status` from `pending` to `approved`
4. The vendor email should get "Your shop is approved"

## 5. If something doesn't arrive

Supabase Dashboard → **Edge Functions → vendor-registered → Logs** shows every
invocation with success/failure. Look for `resend: 4xx` or a stack trace.

Common issues:
- Resend rejects the email because `FROM_EMAIL` isn't a verified domain and
  you're not using `onboarding@resend.dev` → use the test address until you
  verify your domain in Resend
- `ADMIN_EMAIL` has a typo → check the secret
- Function times out → the vendor email lookup requires SERVICE_ROLE_KEY,
  make sure it's set (it should be automatic)
