# 🏗️ Bharat Kirana — What YOU need to do at home (v3)

> Fresh checklist. All completed tasks removed. Only the work that's **left to unblock push notifications** + a couple of optional items at the bottom.

---

## ✅ Already done (for reference)

| # | Task |
|---|---|
| A.1 | Supabase schema — new columns + triggers + indexes |
| A.2 | Strict RLS policies across 8 tables |
| A.3 | Promoted admin roles in `profiles` |
| A.5 | Untracked built AAB from git |
| F | Firebase Remote Config — all 10 keys published |
| Round 4 Task 1 | `barcode` column added to `products` |
| Round 4 Task 2 | Realtime enabled on `orders` + `notifications` (2 tables) |

---

## 🎯 TONIGHT'S CHECKLIST (~30 min total)

### ☐ Task 1 — Promo codes table (5 min) 🏠

Supabase → **SQL Editor** → **New query** → paste → **Run**:

```sql
CREATE TABLE IF NOT EXISTS promo_codes (
  code TEXT PRIMARY KEY,
  description TEXT DEFAULT '',
  discount_percent INTEGER NOT NULL DEFAULT 0 CHECK (discount_percent BETWEEN 0 AND 100),
  discount_flat_rupees INTEGER NOT NULL DEFAULT 0 CHECK (discount_flat_rupees >= 0),
  min_order_amount INTEGER NOT NULL DEFAULT 0,
  max_discount_rupees INTEGER,
  valid_from TIMESTAMPTZ DEFAULT NOW(),
  valid_until TIMESTAMPTZ,
  usage_limit INTEGER,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE orders ADD COLUMN IF NOT EXISTS promo_code TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS promo_discount INTEGER DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_orders_promo_code ON orders(promo_code) WHERE promo_code IS NOT NULL;

ALTER TABLE promo_codes ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Anyone can view active promo codes" ON promo_codes;
CREATE POLICY "Anyone can view active promo codes" ON promo_codes
FOR SELECT USING (
  active = TRUE
  AND (valid_until IS NULL OR valid_until > NOW())
  AND (valid_from  IS NULL OR valid_from  <= NOW())
);

DROP POLICY IF EXISTS "Admin can manage promo codes" ON promo_codes;
CREATE POLICY "Admin can manage promo codes" ON promo_codes
FOR ALL USING (
  (SELECT role FROM profiles WHERE id = auth.uid()) IN ('admin','super_admin')
) WITH CHECK (
  (SELECT role FROM profiles WHERE id = auth.uid()) IN ('admin','super_admin')
);

-- Two seed codes so you can test the redemption flow in the app tonight:
INSERT INTO promo_codes (code, description, discount_percent, min_order_amount, max_discount_rupees, valid_until)
VALUES ('WELCOME50', 'Flat 50% off (up to ₹100)', 50, 100, 100, NOW() + INTERVAL '30 days')
ON CONFLICT (code) DO NOTHING;

INSERT INTO promo_codes (code, description, discount_flat_rupees, min_order_amount, valid_until)
VALUES ('DIWALI30', '₹30 off orders above ₹200', 30, 200, NOW() + INTERVAL '60 days')
ON CONFLICT (code) DO NOTHING;
```

**Verify:** `SELECT * FROM promo_codes;` → shows 2 rows.

**Test in app:** cart → enter `WELCOME50` → tap Apply → discount appears. ✅

---

### ☐ Task 2 — Firebase FCM v1 setup (15 min) 🖥️

**Where:** https://console.firebase.google.com → your Bharat Kirana project.

#### E.1 — Enable FCM v1 API

1. Click ⚙️ **Project settings** → **Cloud Messaging** tab.
2. Find the **Firebase Cloud Messaging API (V1)** section.
3. If it says **Disabled**: click the 3-dot menu (⋮) → **Manage API in Google Cloud Console** → click **Enable** → wait 1-2 min.
4. **Do NOT enable** the "Cloud Messaging API (Legacy)" section (deprecated, skip it).

#### E.2 — Download service account JSON

1. Project settings → **Service accounts** tab.
2. Click **Generate new private key** → confirm → a JSON file downloads.
3. Open the file in Notepad. Copy the **entire content** (starts with `{` ends with `}`, ~2 KB).
4. Keep this Notepad open — you'll paste it into Supabase in Task 3.

⚠️ **This JSON is EXTREMELY sensitive.** Anyone with it can send push notifications to your users. After Task 3, delete the local file. Never commit it to git.

#### E.3 — Enable App Check (optional but recommended, 3 min)

1. Firebase Console → **App Check** (left sidebar) → your Android app → **Register**.
2. Choose provider: **Play Integrity** → follow prompts.
3. Done. Blocks fake APKs from calling Firebase.

**Note:** Play Integrity needs your SHA-256 fingerprint. If it asks for one, skip E.3 for now — we'll set it up during Play Store release.

---

### ☐ Task 3 — Supabase Edge Function for push (10 min) 🏠

**Where:** https://supabase.com → your project.

#### B.1 — Add secrets

Project Settings → **Edge Functions** → **Secrets** → add 3 secrets:

| Secret name | Value |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Paste the entire JSON from Task 2 (one line, exactly as copied) |
| `SUPABASE_URL` | Project Settings → API → "Project URL" |
| `SUPABASE_SERVICE_ROLE_KEY` | Project Settings → API → **service_role** key (the SECRET one) |

Click **Save** after each.

#### B.2 — Deploy the Edge Function

Left sidebar → **Edge Functions** → **Deploy a new function**:

- **Name:** `notify-order-status`
- Paste the code below into the editor
- Click **Deploy**

```typescript
// supabase/functions/notify-order-status/index.ts
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { create, getNumericDate } from "https://deno.land/x/djwt@v3.0.2/mod.ts";

const FIREBASE_SERVICE_ACCOUNT = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

interface WebhookPayload {
  type: "INSERT" | "UPDATE" | "DELETE";
  table: string;
  record: Record<string, any>;
  old_record?: Record<string, any>;
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem.replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "").replace(/\s+/g, "");
  const bin = atob(b64);
  const buf = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
  return buf.buffer;
}

async function getFcmAccessToken(): Promise<string> {
  const sa = JSON.parse(FIREBASE_SERVICE_ACCOUNT);
  const claim = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: getNumericDate(3600),
    iat: getNumericDate(0),
  };
  const pem = sa.private_key.replace(/\\n/g, "\n");
  const key = await crypto.subtle.importKey("pkcs8", pemToArrayBuffer(pem),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const jwt = await create({ alg: "RS256", typ: "JWT" }, claim, key);
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });
  const j = await res.json();
  if (!j.access_token) throw new Error("Token: " + JSON.stringify(j));
  return j.access_token;
}

async function sendPush(token: string, projectId: string, fcmToken: string,
  title: string, body: string, orderId: string) {
  const res = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
      method: "POST",
      headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token: fcmToken,
          notification: { title, body },
          // Key must be order_id (snake_case) — MyFirebaseMessagingService reads
          // data["order_id"]; the previous camelCase "orderId" never matched, so
          // tapping a push never deep-linked to the order.
          data: { order_id: orderId, type: "order_status" },
          // channel_id must be a channel that actually exists on the device when
          // this push is auto-displayed in the background. "customer_notifications"
          // was never created there — only "bharat_kirana_orders" is (MainActivity
          // creates it unconditionally on every app start) — so the OS was silently
          // dropping every background push.
          android: { priority: "HIGH", notification: { sound: "default", channel_id: "bharat_kirana_orders" } },
        },
      }),
    });
  console.log("FCM v1:", res.status, await res.text());
}

async function fetchFcmToken(email: string): Promise<string | null> {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/profiles?email=eq.${encodeURIComponent(email)}&select=fcm_token`,
    { headers: { "apikey": SUPABASE_SERVICE_ROLE_KEY, "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}` } });
  const d = await r.json();
  return d?.[0]?.fcm_token ?? null;
}

async function insertNotification(email: string, title: string, body: string, orderId: string) {
  const p = await fetch(`${SUPABASE_URL}/rest/v1/profiles?email=eq.${encodeURIComponent(email)}&select=id`,
    { headers: { "apikey": SUPABASE_SERVICE_ROLE_KEY, "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}` } });
  const uid = (await p.json())?.[0]?.id;
  if (!uid) return;
  await fetch(`${SUPABASE_URL}/rest/v1/notifications`, {
    method: "POST",
    headers: { "apikey": SUPABASE_SERVICE_ROLE_KEY, "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
      "Content-Type": "application/json", "Prefer": "return=minimal" },
    // order_id was previously omitted, so every in-app notification row had a null
    // order_id — the Notifications screen only navigates to OrderDetails when
    // notification.orderId != null, so tapping any of them did nothing.
    body: JSON.stringify({ user_id: uid, title, message: body, is_read: false, order_id: orderId }),
  });
}

serve(async (req) => {
  try {
    const p: WebhookPayload = await req.json();
    if (p.table !== "orders" || p.type !== "UPDATE") return new Response("Ignored", { status: 200 });
    const newStatus = p.record?.status, oldStatus = p.old_record?.status;
    if (!newStatus || newStatus === oldStatus) return new Response("No change", { status: 200 });

    const email = p.record.customer_email, orderId = p.record.id;
    const msgs: Record<string, { title: string; body: string }> = {
      "Preparing": { title: "Aapka order taiyar ho raha hai 👨‍🍳", body: `Order #${orderId} — shop has started preparing your items.` },
      "Ready for Pickup": { title: "Order ready for pickup! 🛍️", body: `Order #${orderId} is ready. Please visit the shop to collect.` },
      "Completed": { title: "Thank you! ✅", body: `Order #${orderId} is complete. Please rate your experience.` },
      "Cancelled": { title: "Order cancelled", body: `Order #${orderId} has been cancelled.` },
    };
    const m = msgs[newStatus];
    if (!m) return new Response("No message", { status: 200 });

    await insertNotification(email, m.title, m.body, orderId);
    const fcm = await fetchFcmToken(email);
    if (!fcm) return new Response("No token (in-app only)", { status: 200 });
    const sa = JSON.parse(FIREBASE_SERVICE_ACCOUNT);
    await sendPush(await getFcmAccessToken(), sa.project_id, fcm, m.title, m.body, orderId);
    return new Response("OK", { status: 200 });
  } catch (e) {
    console.error(e);
    return new Response(`Error: ${e}`, { status: 500 });
  }
});
```

#### B.3 — Create the Database Webhook

Supabase → **Database** → **Webhooks** → **Create a new hook**:

- **Name:** `order_status_notify`
- **Table:** `orders`
- **Events:** ☑ Update
- **Type:** **Supabase Edge Functions**
- **Function:** select `notify-order-status`
- Click **Create webhook**.

#### B.4 — Test end-to-end

1. Table Editor → open `orders` → pick any row → change `status` from anything to `Preparing` → save.
2. Left sidebar → **Edge Functions** → `notify-order-status` → **Logs** tab.
3. You should see `"FCM v1: 200 ..."` within a few seconds.
4. If yes → **push is working.** ✅ (You'll actually see the notification pop up on your phone once we release the next app version with FCM token registration wired.)

If you see an error in logs, screenshot it and send to me.

---

## 📦 Optional (not blocking anything today)

### C — Mappls maps API keys
Only needed if you want real maps on Nearby Shops. Currently the app works fine without them (uses local sample map view). Do this when you're ready to ship maps: https://about.mappls.com/api → sign up → 4 keys → paste into `.env`.

### G — Privacy policy on GitHub Pages
Needed **only** when you upload to Play Store production track. Not urgent tonight. 15 min at any time: create tiny repo → paste `privacy.html` → enable Pages → get public URL.

### Play Store release + anon key rotation
Round 4 endgame. Don't do until barcode + realtime are tested and Task 3 above is complete.

---

## 🚀 After tonight

Once you complete Tasks 1-3, tell me **"tasks 1-3 done, push logs show 200"** and I'll wire the last piece of push notifications:

- Save FCM token to `profiles.fcm_token` after login
- Handle notification taps → open specific order details
- Test end-to-end with your phone

Then we're at Round 4 completion, ready for Play Store release.

---

## 📞 If anything fails tonight

Screenshot the error + which section you're on → send to me → I'll debug.

**Sleep well after Task 3.** 🌙
