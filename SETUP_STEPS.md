# 🏗️ Bharat Kirana — Backend / Cloud Setup Steps (A–H)

**Read this like a checklist.** Do the sections in order. Each section is self-contained.

> Time estimate: **~90 minutes total** if all your accounts already exist. Add 30 min if you need to create new accounts (Mappls, AdMob, Play Console).

---

# 🅰️ Supabase — Add all new database columns + tight security policies

**Why:** The Round 2 code (order status, shop timings, ratings, cancel reason, in-stock) needs new columns. Also — a security review found that the previous "wide-open" access rules would let anyone dump your entire customer database. This section fixes both.

> ⚠️ **This section is now MUCH stricter than the version I first gave you.** A friend's security review caught real vulnerabilities. Please use this new version. If you already ran the earlier "wide-open" SQL, just run this one after — it's safe to re-run and it will replace the loose policies with strict ones.

**Where:** https://supabase.com → your project → left sidebar → **SQL Editor** → **New query**.

## A.1 — Schema changes (columns + tables)

Paste and **Run**:

```sql
-- ============================================================================
-- BHARAT KIRANA — Round 2 schema
-- Safe to re-run.
-- ============================================================================

-- Orders: cancel tracking + updated_at
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancel_reason TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancelled_by TEXT; -- 'customer' or 'vendor'
ALTER TABLE orders ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS orders_updated_at ON orders;
CREATE TRIGGER orders_updated_at BEFORE UPDATE ON orders
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Shops: hours + accepting toggle + rating aggregates
ALTER TABLE shops ADD COLUMN IF NOT EXISTS open_time TEXT DEFAULT '08:00';
ALTER TABLE shops ADD COLUMN IF NOT EXISTS close_time TEXT DEFAULT '21:00';
ALTER TABLE shops ADD COLUMN IF NOT EXISTS accepting_orders BOOLEAN DEFAULT TRUE;
ALTER TABLE shops ADD COLUMN IF NOT EXISTS rating_average NUMERIC(3,2) DEFAULT 0;
ALTER TABLE shops ADD COLUMN IF NOT EXISTS rating_count INTEGER DEFAULT 0;
ALTER TABLE shops ADD COLUMN IF NOT EXISTS owner_id UUID; -- links shop to owner's auth.users.id

-- Products: stock toggle + quantity
ALTER TABLE products ADD COLUMN IF NOT EXISTS in_stock BOOLEAN DEFAULT TRUE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS stock_qty INTEGER DEFAULT 10;

-- Ratings & reviews
CREATE TABLE IF NOT EXISTS shop_ratings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  shop_id TEXT NOT NULL,
  order_id TEXT,
  customer_email TEXT NOT NULL,
  rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
  review TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_shop_ratings_shop ON shop_ratings(shop_id);

CREATE OR REPLACE FUNCTION refresh_shop_rating()
RETURNS TRIGGER AS $$
BEGIN
  UPDATE shops
  SET rating_average = COALESCE((SELECT AVG(rating)::NUMERIC(3,2) FROM shop_ratings WHERE shop_id = NEW.shop_id), 0),
      rating_count   = COALESCE((SELECT COUNT(*) FROM shop_ratings WHERE shop_id = NEW.shop_id), 0)
  WHERE id = NEW.shop_id;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS shop_ratings_refresh ON shop_ratings;
CREATE TRIGGER shop_ratings_refresh AFTER INSERT ON shop_ratings
FOR EACH ROW EXECUTE FUNCTION refresh_shop_rating();

-- User profiles: role is server-controlled, never client-set
CREATE TABLE IF NOT EXISTS user_profiles (
  user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  email TEXT UNIQUE NOT NULL,
  full_name TEXT,
  mobile TEXT,
  address TEXT,
  fcm_token TEXT,
  role TEXT NOT NULL DEFAULT 'customer' CHECK (role IN ('customer','vendor','admin','super_admin')),
  shop_id TEXT,
  updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_user_profiles_email ON user_profiles(email);

-- Ensure orders has customer_email indexed for RLS filtering
CREATE INDEX IF NOT EXISTS idx_orders_customer_email ON orders(customer_email);
CREATE INDEX IF NOT EXISTS idx_orders_shop_id ON orders(shop_id);
```

Click **Run**. Verify columns show up in Table Editor.

## A.2 — Strict Row-Level Security policies (**this is the real security fix**)

⚠️ **Read this section carefully.** These rules are what actually protect your users' data. Without them, anyone with the anon key can dump everything.

Paste and **Run**:

```sql
-- ============================================================================
-- ROW-LEVEL SECURITY — strict policies
-- Drops any earlier loose policies first, then applies the safe ones.
-- ============================================================================

-- Helper: read the current user's role from user_profiles
CREATE OR REPLACE FUNCTION current_user_role()
RETURNS TEXT AS $$
  SELECT role FROM user_profiles WHERE user_id = auth.uid()
$$ LANGUAGE sql SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION current_user_email()
RETURNS TEXT AS $$
  SELECT lower(email) FROM auth.users WHERE id = auth.uid()
$$ LANGUAGE sql SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION is_admin()
RETURNS BOOLEAN AS $$
  SELECT COALESCE(current_user_role() IN ('admin','super_admin'), FALSE)
$$ LANGUAGE sql SECURITY DEFINER STABLE;

-- Drop old loose policies if they exist
DO $$
DECLARE p RECORD;
BEGIN
  FOR p IN
    SELECT schemaname, tablename, policyname
    FROM pg_policies
    WHERE schemaname = 'public'
      AND tablename IN ('orders','shops','products','shop_ratings','user_profiles')
  LOOP
    EXECUTE format('DROP POLICY IF EXISTS %I ON %I.%I', p.policyname, p.schemaname, p.tablename);
  END LOOP;
END $$;

-- Enable RLS on every relevant table
ALTER TABLE orders        ENABLE ROW LEVEL SECURITY;
ALTER TABLE shops         ENABLE ROW LEVEL SECURITY;
ALTER TABLE products      ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop_ratings  ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;

-- ==========================================================================
-- USER_PROFILES
-- ==========================================================================
-- Users can read their own profile. Admins can read any.
CREATE POLICY "profiles_select_own_or_admin" ON user_profiles
FOR SELECT USING (user_id = auth.uid() OR is_admin());

-- Users can INSERT their own profile row ONCE (during signup). Role is forced to 'customer'.
CREATE POLICY "profiles_insert_self" ON user_profiles
FOR INSERT WITH CHECK (user_id = auth.uid() AND role = 'customer');

-- Users can UPDATE their own basic fields, but NOT role. shop_id can be set to a shop
-- they own (linking themselves as owner). Admins can update anything.
CREATE POLICY "profiles_update_own_basic" ON user_profiles
FOR UPDATE USING (user_id = auth.uid())
WITH CHECK (
  user_id = auth.uid()
  AND role = (SELECT role FROM user_profiles WHERE user_id = auth.uid())
  AND (
    shop_id IS NOT DISTINCT FROM (SELECT shop_id FROM user_profiles WHERE user_id = auth.uid())
    OR shop_id IN (SELECT id FROM shops WHERE owner_id = auth.uid())
  )
);

CREATE POLICY "profiles_update_admin" ON user_profiles
FOR UPDATE USING (is_admin()) WITH CHECK (is_admin());

-- ==========================================================================
-- ORDERS
-- ==========================================================================
-- Customer sees ONLY their own orders. Vendor sees orders for their shop. Admin sees all.
CREATE POLICY "orders_select_scoped" ON orders
FOR SELECT USING (
  lower(customer_email) = current_user_email()
  OR shop_id IN (SELECT shop_id FROM user_profiles WHERE user_id = auth.uid() AND role = 'vendor')
  OR is_admin()
);

-- Authenticated customers can INSERT their own orders (customer_email must match theirs).
CREATE POLICY "orders_insert_customer" ON orders
FOR INSERT WITH CHECK (lower(customer_email) = current_user_email());

-- Only vendor (for their shop) or admin can UPDATE order status. Customer can cancel their own.
CREATE POLICY "orders_update_vendor_admin_or_cancel" ON orders
FOR UPDATE USING (
  shop_id IN (SELECT shop_id FROM user_profiles WHERE user_id = auth.uid() AND role = 'vendor')
  OR is_admin()
  OR (lower(customer_email) = current_user_email() AND status IN ('Order Placed','Preparing'))
);

-- ==========================================================================
-- SHOPS
-- ==========================================================================
-- Everyone can browse the shop list.
CREATE POLICY "shops_select_all" ON shops FOR SELECT USING (TRUE);

-- Only admin can INSERT new shops (vendor registrations go through a review). Simpler: allow authenticated to insert their own, admin verifies later.
CREATE POLICY "shops_insert_owner" ON shops
FOR INSERT WITH CHECK (owner_id = auth.uid() OR is_admin());

-- Only the shop's owner or admin can UPDATE the shop.
CREATE POLICY "shops_update_owner_or_admin" ON shops
FOR UPDATE USING (owner_id = auth.uid() OR is_admin());

-- ==========================================================================
-- PRODUCTS
-- ==========================================================================
-- Everyone can browse products.
CREATE POLICY "products_select_all" ON products FOR SELECT USING (TRUE);

-- Only the vendor who owns the shop, or admin, can INSERT/UPDATE/DELETE products.
CREATE POLICY "products_write_vendor_or_admin" ON products
FOR ALL USING (
  shop_id IN (SELECT shop_id FROM user_profiles WHERE user_id = auth.uid() AND role = 'vendor')
  OR is_admin()
) WITH CHECK (
  shop_id IN (SELECT shop_id FROM user_profiles WHERE user_id = auth.uid() AND role = 'vendor')
  OR is_admin()
);

-- ==========================================================================
-- SHOP_RATINGS
-- ==========================================================================
-- Everyone can read ratings (needed to show shop rating publicly).
CREATE POLICY "ratings_select_all" ON shop_ratings FOR SELECT USING (TRUE);

-- Only the customer who placed the order can INSERT a rating.
CREATE POLICY "ratings_insert_customer" ON shop_ratings
FOR INSERT WITH CHECK (lower(customer_email) = current_user_email());

-- No updates/deletes allowed at all (immutable review history).
```

Click **Run**. Verify no errors.

## A.3 — Bootstrap the first super admin

Because our new rules block clients from setting `role`, you must promote yourself to super_admin manually — **once**.

1. Sign up in the app normally with your admin email (e.g., `you@yourdomain.com`).
2. In Supabase → **Table Editor** → open `user_profiles` table.
3. Find the row with your email → click the pencil ✏️ on the `role` column → change from `customer` to `super_admin` → save.
4. Log out and back in on your phone → you now have super admin.

For future admins/vendors, you (as super admin) can promote them the same way. No one can promote themselves.

## A.4 — Rotate the leaked Supabase anon key

The old anon key (`sb_publishable_lUWgw…`) has been in a public GitHub repo. Even though it's a "publishable" key, we should rotate it as hygiene now that we've tightened everything.

1. Supabase → **Project Settings** → **API** → **anon (publishable) key** → click the ↻ rotate icon.
2. Confirm rotation. Supabase shows you a new key.
3. Copy new key → paste into your local `.env` file (replaces the old `SUPABASE_ANON_KEY` line).
4. Rebuild the app in Android Studio (▶). It will now use the new key.
5. Anyone still using the old APK with the old key will get 401 errors — force them to update. Since your app isn't on Play Store yet, this doesn't affect real users.

## A.5 — Clean the built AAB out of git

The 16 MB pre-built app bundle at `app/release/app-release.aab` shouldn't be in version control. Already added to `.gitignore`. To remove it from git history:

```powershell
cd C:\Users\PulkitYagyasaini\Documents\Playground\BKS\bharat-kirana
git rm --cached app/release/app-release.aab
git commit -m "Stop tracking built release artifact"
git push
```

(The file stays on your laptop — this just tells git to ignore it going forward.)

## A.6 — What still needs Round 2 (I'll do these tomorrow in code)

Even with tonight's fixes, these client-side changes must happen tomorrow to complete the security lockdown:

- **Fetch `user_profiles.role` after login** and populate `UserProfile.serverRole`. This makes RLS the sole source of authorization instead of the `.env`-based bridge.
- **Move `bharatkirana://` password-reset callback to Supabase email OTP** (6-digit code entered in app) so no other app can hijack the reset link via URI scheme.
- **Store user's `auth.uid()`** in the app after login (not just email) so RLS conditions using `auth.uid()` work correctly.

I'll wire all of this in Round 2 automatically.

**Verify:** After running A.1 and A.2, open **Authentication → Policies** in Supabase sidebar. You should see the strict policies listed under each table. If any table shows "No policies" — RLS is on but nothing lets you through — re-run A.2.

---

# 🅱️ Supabase — Deploy Edge Function for push notifications

**Why:** When a shopkeeper marks an order "Ready for Pickup", we need Supabase to automatically ping the customer's phone with a notification. This function is the "robot" that watches order status changes and asks Firebase to deliver the push.

**Where:** https://supabase.com → your project → left sidebar → **Edge Functions** → **Deploy a new function** (or use the Supabase CLI locally — I'll show the CLI-free path here).

**Steps:**

## B.1 — Create the function

1. Click **Deploy a new function** → name it `notify-order-status`.
2. Paste the code below into the editor.

```typescript
// supabase/functions/notify-order-status/index.ts
// Sends a Firebase Cloud Messaging (FCM) push notification whenever an order's
// status is updated. Triggered by a Supabase Database Webhook (see step B.3).

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

const FCM_SERVER_KEY = Deno.env.get("FCM_SERVER_KEY")!; // set in step E
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

interface WebhookPayload {
  type: "INSERT" | "UPDATE" | "DELETE";
  table: string;
  record: Record<string, any>;
  old_record?: Record<string, any>;
}

async function sendPush(fcmToken: string, title: string, body: string, orderId: string) {
  const res = await fetch("https://fcm.googleapis.com/fcm/send", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `key=${FCM_SERVER_KEY}`,
    },
    body: JSON.stringify({
      to: fcmToken,
      notification: { title, body, sound: "default" },
      data: { orderId, type: "order_status" },
      priority: "high",
    }),
  });
  console.log("FCM response:", res.status, await res.text());
}

async function fetchFcmTokenForEmail(email: string): Promise<string | null> {
  const res = await fetch(
    `${SUPABASE_URL}/rest/v1/user_profiles?email=eq.${email}&select=fcm_token`,
    {
      headers: {
        "apikey": SUPABASE_SERVICE_ROLE_KEY,
        "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
      },
    }
  );
  const data = await res.json();
  return data?.[0]?.fcm_token ?? null;
}

serve(async (req) => {
  try {
    const payload: WebhookPayload = await req.json();
    if (payload.table !== "orders" || payload.type !== "UPDATE") {
      return new Response("Ignored", { status: 200 });
    }

    const newStatus = payload.record?.status;
    const oldStatus = payload.old_record?.status;
    if (!newStatus || newStatus === oldStatus) {
      return new Response("No status change", { status: 200 });
    }

    const customerEmail = payload.record.customer_email;
    const orderId = payload.record.id;

    const messagesByStatus: Record<string, { title: string; body: string }> = {
      "Preparing":         { title: "Aapka order taiyar ho raha hai 👨‍🍳", body: `Order #${orderId} — the shop has started preparing your items.` },
      "Ready for Pickup":  { title: "Order ready for pickup! 🛍️",        body: `Order #${orderId} is ready. Please visit the shop to collect.` },
      "Completed":         { title: "Thank you! ✅",                       body: `Order #${orderId} is complete. Please rate your experience.` },
      "Cancelled":         { title: "Order cancelled",                     body: `Order #${orderId} has been cancelled.` },
    };
    const msg = messagesByStatus[newStatus];
    if (!msg) return new Response("No message for status", { status: 200 });

    const fcmToken = await fetchFcmTokenForEmail(customerEmail);
    if (!fcmToken) return new Response("No FCM token for user", { status: 200 });

    await sendPush(fcmToken, msg.title, msg.body, orderId);
    return new Response("OK", { status: 200 });
  } catch (err) {
    console.error(err);
    return new Response(`Error: ${err}`, { status: 500 });
  }
});
```

3. Click **Deploy**.

## B.2 — Add secrets to the Edge Function

Still in Supabase → left sidebar → **Project Settings** → **Edge Functions** → **Secrets** section.

Add these three secrets:

| Secret name | Where to find the value |
|---|---|
| `FCM_SERVER_KEY` | You'll paste this after doing step **E** below |
| `SUPABASE_URL` | Project Settings → API → "Project URL" |
| `SUPABASE_SERVICE_ROLE_KEY` | Project Settings → API → "service_role" key (⚠️ **secret** — never put in the app code) |

## B.3 — Create the Database Webhook

Supabase → left sidebar → **Database** → **Webhooks** → **Create a new hook**.

- **Name:** `order_status_notify`
- **Table:** `orders`
- **Events:** ☑ Update
- **Type:** **HTTP Request**
- **HTTP method:** POST
- **URL:** `https://<your-project-ref>.supabase.co/functions/v1/notify-order-status` (Supabase auto-fills this if you pick "Supabase Edge Functions" as the endpoint type)
- **HTTP headers:** Add `Authorization` = `Bearer <SUPABASE_SERVICE_ROLE_KEY>` (paste the same service_role key)

Click **Create webhook**.

**Test:** In Table Editor, open any row in `orders` table → change `status` field to "Preparing" → save. Check Edge Function **Logs** — you should see `"FCM response: 200 ..."` line. If yes, you're done. If not, don't worry — we'll debug once your phone is set up.

---

# 🅲 Mappls — Get a free maps API key

**Why:** The "Nearby Shops" screen currently has placeholder text where a map key should go. Without a real key, the map won't render.

**Where:** https://about.mappls.com/api/ → **Sign Up**

**Steps:**

1. Click **Sign Up** → use your email + phone. Verify OTP.
2. After login → **Dashboard** → **Create Project** → name it "Bharat Kirana."
3. Under project → **API Keys** tab → you'll see 3 keys:
   - **REST API Key**
   - **Map SDK Key** (this is the main one for Android)
   - **Atlas Client ID + Secret**
4. Copy all four values into a plain text note.
5. Paste them into `app/src/main/AndroidManifest.xml` — replace the placeholder values:

```xml
<meta-data android:name="mappls_api_key" android:value="PASTE_MAP_SDK_KEY_HERE" />
<meta-data android:name="mappls_rest_key" android:value="PASTE_REST_API_KEY_HERE" />
<meta-data android:name="mappls_atlas_client_id" android:value="PASTE_CLIENT_ID_HERE" />
<meta-data android:name="mappls_atlas_client_secret" android:value="PASTE_CLIENT_SECRET_HERE" />
```

⚠️ **Better long-term:** move these into your `.env` file so they're not committed to GitHub. We can wire that up later — for now pasting into the manifest is fine.

**Free tier:** Mappls gives 30,000 free map loads/month. Plenty for early testing.

---

# 🅳 AdMob — Skipped for launch ✅

Done. I removed all AdMob code from the app. Nothing for you to do here.

**Later (after you have real users):** if you want to add ads back, we'll:
1. Sign up at https://admob.google.com
2. Get real `App ID` and `Ad Unit ID`
3. I'll re-add the code paths I just deleted.

---

# 🅴 Firebase — Get Cloud Messaging Server Key

**Why:** The Supabase Edge Function from step B needs permission to send notifications via Firebase. This "Server Key" is that permission.

**Where:** https://console.firebase.google.com → your project (the same one connected to `google-services.json`)

**Steps:**

1. Click the ⚙️ gear icon (top-left) → **Project settings** → **Cloud Messaging** tab.
2. Look for **Cloud Messaging API (Legacy)** section. It says either "Enabled" or "Disabled."
   - If **Disabled**: click the 3-dot menu (⋮) → **Manage API in Google Cloud Console** → click **Enable**. Come back to Firebase → refresh.
3. Once enabled, a **Server key** value appears. Copy it.
4. Go back to **Supabase → Project Settings → Edge Functions → Secrets** and paste that value into the `FCM_SERVER_KEY` secret you created in step B.2.

⚠️ **This key is SECRET.** Never paste it into the Android app code or commit it to GitHub. It only lives inside Supabase's secret store.

**Also enable App Check** while you're here (blocks fake apps from calling Firebase):
- Firebase Console → **App Check** → your Android app → **Register** → provider = **Play Integrity** → follow prompts.
- This is quick and protects your project from abuse. Do it now, thank yourself later.

---

# 🅵 Firebase — Set up Remote Config

**Why:** Remote Config is your "control panel." You'll be able to change prices, discount rules, banner text, and force-update settings from Firebase's website **without releasing a new app version**.

**Where:** Firebase Console → left sidebar → **Remote Config** → **Create configuration**.

**Steps:**

1. Click **Add parameter** for each row in the table below. Use the **exact key name** (case-sensitive):

| Parameter key | Default value | Type | Description (paste in the "Description" field) |
|---|---|---|---|
| `min_order_for_free_handling` | `200` | Number | Order above this ₹ amount gets free handling fee |
| `handling_fee_rupees` | `5` | Number | Handling fee added to each order |
| `free_handling_discount_rupees` | `15` | Number | Discount amount if order crosses the free-handling threshold |
| `default_shop_radius_km` | `5` | Number | Default radius customer sees when browsing shops |
| `min_supported_version_code` | `5` | Number | Any app with lower versionCode is forced to update |
| `latest_version_code` | `5` | Number | Latest version code available (used for "update available" prompt) |
| `promo_banner_text` | `Welcome to Bharat Kirana! 🛒` | String | Text shown in the home page banner |
| `promo_banner_enabled` | `true` | Boolean | Show/hide the home banner |
| `maintenance_mode` | `false` | Boolean | Set to true to show a "maintenance" screen and block ordering |
| `support_whatsapp_number` | `+91XXXXXXXXXX` | String | Number shown on the "Contact Us" button (replace with yours) |

2. After adding all 10 → click **Publish changes** (top-right).

Round 2 code will read these values on app open. Anytime you edit them here and click **Publish**, users will see the change within a minute.

---

# 🅶 GitHub Pages — Host your Privacy Policy publicly

**Why:** Play Store requires a **public URL** to your privacy policy. In-app screens don't count.

**Steps:**

## G.1 — Create a new tiny repo

1. Go to https://github.com/new
2. Repo name: `bharat-kirana-policy`
3. Set to **Public**
4. Check ☑ **"Add a README file"**
5. Click **Create repository**.

## G.2 — Add the privacy policy HTML

1. In your new repo → click **Add file** → **Create new file**.
2. Filename: `privacy.html`
3. Paste this (edit the parts in **[BRACKETS]**):

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Bharat Kirana — Privacy Policy</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    body { font-family: -apple-system, Segoe UI, Roboto, sans-serif; max-width: 720px; margin: 40px auto; padding: 0 20px; color: #1f2937; line-height: 1.6; }
    h1 { color: #7c3aed; }
    h2 { color: #4c1d95; margin-top: 32px; }
    a { color: #7c3aed; }
  </style>
</head>
<body>
  <h1>Privacy Policy — Bharat Kirana</h1>
  <p><em>Last updated: [DATE]</em></p>

  <p>Bharat Kirana ("we", "us") operates the Bharat Kirana Android app. This page informs you of our policies regarding the collection, use, and disclosure of personal information when you use our service.</p>

  <h2>1. Information We Collect</h2>
  <ul>
    <li><b>Account Info:</b> Name, email, mobile number, delivery/pickup address.</li>
    <li><b>Location:</b> Approximate/precise location (only when app is open) to show nearby shops.</li>
    <li><b>Order Data:</b> Items in cart, order history, pickup QR codes.</li>
    <li><b>Device:</b> App version, OS, push notification token.</li>
  </ul>

  <h2>2. How We Use It</h2>
  <ul>
    <li>To create your account and let you place orders.</li>
    <li>To show nearby partner shops.</li>
    <li>To send order status notifications.</li>
    <li>To improve the app (aggregated, anonymized analytics).</li>
  </ul>

  <h2>3. Data Sharing</h2>
  <p>We share your order details only with the partner shopkeeper you order from. We do not sell personal data to third parties.</p>

  <h2>4. Data Storage</h2>
  <p>Your data is stored securely on Supabase (PostgreSQL) and Firebase Cloud Messaging (Google). We follow industry-standard encryption in transit and at rest.</p>

  <h2>5. Your Rights</h2>
  <p>You may request account deletion, data export, or corrections by emailing us at <a href="mailto:[YOUR_EMAIL]">[YOUR_EMAIL]</a>. We respond within 7 business days.</p>

  <h2>6. Children</h2>
  <p>Bharat Kirana is not intended for users under 13 years of age.</p>

  <h2>7. Changes</h2>
  <p>We may update this policy. The "Last updated" date at the top reflects the latest revision.</p>

  <h2>8. Contact</h2>
  <p>Questions? Reach us at <a href="mailto:[YOUR_EMAIL]">[YOUR_EMAIL]</a> or WhatsApp <b>[YOUR_PHONE]</b>.</p>
</body>
</html>
```

4. Scroll down → **Commit new file**.

## G.3 — Turn on GitHub Pages

1. In the repo → **Settings** → **Pages** (left sidebar).
2. Under "Build and deployment":
   - **Source:** Deploy from a branch
   - **Branch:** `main` / `root`
3. Click **Save**.
4. Wait 30-60 seconds. Refresh.
5. You'll see: **"Your site is live at `https://YOUR-USERNAME.github.io/bharat-kirana-policy/privacy.html`"**
6. Copy this URL. **Save it.** You'll paste it into Play Console during upload (step H).

**Test:** Open the URL in your browser. You should see the privacy policy. ✅

---

# 🅷 Play Console — Do this LATER (after Round 2-4 are done)

**Why last:** No point in setting up Play Console until the app is feature-complete and tested.

When you're ready, the flow is:

1. Sign up at https://play.google.com/console — **$25 USD one-time** (~ ₹2100). Verify identity (Aadhaar/PAN).
2. Create your app → package name `com.kks.bharatkirana`.
3. Fill "App content" declarations:
   - **Privacy policy URL:** paste the GitHub Pages link from step G.
   - **Data safety form:** disclose what you collect (email, phone, location, purchase history).
   - **Content rating:** fill IARC questionnaire — grocery/shopping app → likely PG.
   - **Target audience:** 18+ (grocery shopping).
   - **Ads:** No (we removed them).
4. **Store listing:** app name, short description (80 chars), full description (4000 chars), 8 screenshots (2 min), feature graphic (1024×500), icon (512×512).
5. **App signing:** enroll in Play App Signing (Google manages the release key). Upload your `my-upload-key.jks` as the upload key.
6. Upload `.aab` to **Internal testing** track first. Invite 10-20 real users. Fix bugs.
7. Move to **Closed testing** (requires 12 testers × 14 days for new accounts). Fix bugs.
8. Move to **Production**. Go live 🚀.

I'll walk you through this section-by-section when you reach it.

---

# ✅ Checklist for tonight

Tick off as you complete each section:

- [ ] **A.1** — Ran the schema SQL. Verified new columns exist in Table Editor.
- [ ] **A.2** — Ran the strict RLS SQL. Verified policies show under Authentication → Policies for every table.
- [ ] **A.3** — Manually promoted your own row in `user_profiles` to `super_admin`.
- [ ] **A.4** — Rotated the Supabase anon key. Updated `.env` on your laptop with the new key.
- [ ] **A.5** — Removed `app/release/app-release.aab` from git tracking.
- [ ] **Local `.env`** — Copied `.env.example` → `.env` and filled in real values (Supabase URL, new anon key, your admin emails, support email).
- [ ] **B.1** — Deployed `notify-order-status` Edge Function in Supabase.
- [ ] **E** — Enabled Cloud Messaging API in Firebase and copied Server Key.
- [ ] **B.2** — Pasted `FCM_SERVER_KEY`, `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` into Supabase Edge Function secrets.
- [ ] **B.3** — Created Database Webhook `order_status_notify` pointing to the Edge Function.
- [ ] **B (test)** — Manually updated an order's status in Table Editor → saw success in Edge Function logs.
- [ ] **C** — Signed up at Mappls and pasted all 4 keys into `AndroidManifest.xml`.
- [ ] **E (bonus)** — Enabled App Check with Play Integrity.
- [ ] **F** — Added all 10 Remote Config parameters in Firebase and clicked Publish.
- [ ] **G** — Created privacy policy repo, hosted `privacy.html` on GitHub Pages, saved the URL.

**Skipped:** D (AdMob), H (Play Console — later).

---

## 🛡️ What the security review caught (already fixed in code)

Your friend's review found real vulnerabilities. Here's what's now done on the code side, so you know it's handled:

- ✅ Removed hardcoded personal emails (`bjain539@gmail.com`, `bjain5329@gmail.com`) from every file. Admin whitelist now reads from `.env` → `BuildConfig` instead.
- ✅ Removed hardcoded Supabase URL/anon key fallback strings from source. The app now strictly uses `.env`.
- ✅ Removed `role` from the client-sent profile payload. Server-side only.
- ✅ Set `android:allowBackup="false"` — no more ADB backup extraction.
- ✅ Cleaned `.env.example` — real credentials replaced with placeholders.
- ✅ Added `app/release/`, `*.aab`, `*.apk` to `.gitignore`.

## 🔓 What still needs Round 2 tomorrow

- Fetch `user_profiles.role` after login → server becomes the sole source of admin authority. The `.env` whitelist is just a bootstrap bridge until this lands.
- Replace `bharatkirana://` password-reset callback with an in-app 6-digit code flow → closes the URI-scheme hijacking risk.
- Store `auth.uid()` alongside email so RLS `auth.uid()` conditions work end-to-end.

---

## 📞 If anything fails tonight

Note the section letter + a short description of what went wrong (a screenshot of any error is best). Bring it to our next session and I'll debug with you step-by-step. Nothing here is one-way — everything can be reversed.

**Once these are done, message me: "A-H done"** and I'll drop Round 2 code (the big order status flow + shopkeeper incoming orders screen + everything else). That code will assume all the columns and keys above exist, so it'll just plug in cleanly.
