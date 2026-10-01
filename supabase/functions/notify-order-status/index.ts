// Supabase Edge Function: notify-order-status
//
// Fired by the Database Webhook "order_status_notify" (public.orders INSERT + UPDATE).
//   INSERT → "New order received" to the shop owner
//   UPDATE → status message to the customer (only when the status really changed)
//            + a message to the shop owner when someone other than the shop cancels
//
// Security:
//   - Requires header x-webhook-secret = WEBHOOK_SECRET (refuses everything if
//     the secret isn't set). "Verify JWT" alone is not enough: the public anon
//     key passes it.
//   - The order is re-read from the database; the webhook body is only used to
//     know WHICH order and what the previous status was. A forged request can't
//     pick the recipient or the message.
//
// Secrets:
//   WEBHOOK_SECRET                  required (same value as the webhook header)
//   FIREBASE_SERVICE_ACCOUNT_JSON   required (or FIREBASE_SERVICE_ACCOUNT)
//   SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY  auto-injected
//
// NOTE: notifications.order_id is required by this function and by the app.
// See NOTIFICATIONS_ORDER_ID_FIX.sql — without that column every
// insertNotification call fails silently.

const env = (...names: string[]) => {
  for (const n of names) {
    const v = Deno.env.get(n);
    if (v) return v;
  }
  return "";
};

const FIREBASE_SERVICE_ACCOUNT = env("FIREBASE_SERVICE_ACCOUNT_JSON", "FIREBASE_SERVICE_ACCOUNT");
const SUPABASE_URL = env("SUPABASE_URL");
const SERVICE_ROLE_KEY = env("SUPABASE_SERVICE_ROLE_KEY", "SERVICE_ROLE_KEY");
const WEBHOOK_SECRET = env("WEBHOOK_SECRET");

const NEW_ORDER_MAX_AGE_MS = 15 * 60 * 1000; // don't announce old orders as "new"

interface WebhookPayload {
  type: "INSERT" | "UPDATE" | "DELETE";
  table: string;
  record: Record<string, any>;
  old_record?: Record<string, any>;
}

type Order = Record<string, any>;

const svc = () => ({
  apikey: SERVICE_ROLE_KEY,
  Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
});

function safeEqual(a: string, b: string): boolean {
  const x = new TextEncoder().encode(a);
  const y = new TextEncoder().encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

function displayNumber(o: Order): string {
  return o.order_number ? `#${o.order_number}` : `#${String(o.id).slice(-6)}`;
}

/* ------------------------------------------------------------ database --- */

async function loadOrder(id: string): Promise<Order | null> {
  const r = await fetch(
    `${SUPABASE_URL}/rest/v1/orders?id=eq.${encodeURIComponent(id)}&select=*`,
    { headers: svc() },
  );
  if (!r.ok) {
    console.error("loadOrder:", r.status, await r.text());
    return null;
  }
  return (await r.json())?.[0] ?? null;
}

async function shopOwnerId(shopId: string): Promise<string | null> {
  const r = await fetch(
    `${SUPABASE_URL}/rest/v1/shops?id=eq.${encodeURIComponent(shopId)}&select=owner_id`,
    { headers: svc() },
  );
  if (!r.ok) return null;
  return (await r.json())?.[0]?.owner_id ?? null;
}

// The customer by id (customer_id / user_id), falling back to the order's email.
async function customerId(o: Order): Promise<string | null> {
  const id = o.customer_id || o.user_id;
  if (id) return id;
  if (!o.customer_email) return null;
  const r = await fetch(
    `${SUPABASE_URL}/rest/v1/profiles?email=eq.${encodeURIComponent(String(o.customer_email).toLowerCase())}&select=id`,
    { headers: svc() },
  );
  if (!r.ok) return null;
  return (await r.json())?.[0]?.id ?? null;
}

// Every push token for a user: device_tokens rows + the legacy profiles.fcm_token.
async function pushTokens(userId: string): Promise<string[]> {
  const set = new Set<string>();
  try {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/device_tokens?user_id=eq.${encodeURIComponent(userId)}&select=token`,
      { headers: svc() },
    );
    if (r.ok) for (const t of await r.json()) if (t?.token) set.add(t.token);
  } catch {
    /* table may not exist */
  }
  try {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/profiles?id=eq.${encodeURIComponent(userId)}&select=fcm_token`,
      { headers: svc() },
    );
    if (r.ok) {
      const tok = (await r.json())?.[0]?.fcm_token;
      if (tok) set.add(tok);
    }
  } catch {
    /* column may not exist */
  }
  return [...set];
}

// A token FCM rejects as UNREGISTERED/INVALID will never work again. Drop it so
// it stops counting toward "sent 0 of 4" on every future push.
async function dropDeadToken(token: string) {
  await fetch(
    `${SUPABASE_URL}/rest/v1/device_tokens?token=eq.${encodeURIComponent(token)}`,
    { method: "DELETE", headers: { ...svc(), Prefer: "return=minimal" } },
  ).catch(() => {});
  await fetch(
    `${SUPABASE_URL}/rest/v1/profiles?fcm_token=eq.${encodeURIComponent(token)}`,
    {
      method: "PATCH",
      headers: { ...svc(), "Content-Type": "application/json", Prefer: "return=minimal" },
      body: JSON.stringify({ fcm_token: null }),
    },
  ).catch(() => {});
}

async function insertNotification(userId: string, title: string, body: string, orderId: string) {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/notifications`, {
    method: "POST",
    headers: { ...svc(), "Content-Type": "application/json", Prefer: "return=minimal" },
    body: JSON.stringify({ user_id: userId, title, message: body, is_read: false, order_id: orderId }),
  });
  // Loud, not quiet: a schema drift here silently empties the in-app list.
  if (!r.ok) console.error("notifications insert FAILED:", r.status, await r.text());
}

/* ----------------------------------------------------------------- FCM --- */

let fcmCache: { token: string; exp: number; projectId: string } | null = null;

function b64url(input: ArrayBuffer | string): string {
  let bin = "";
  if (typeof input === "string") bin = input;
  else for (const b of new Uint8Array(input)) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN [^-]+-----/, "")
    .replace(/-----END [^-]+-----/, "")
    .replace(/\s+/g, "");
  const bin = atob(b64);
  const buf = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
  return buf.buffer;
}

async function fcm(): Promise<{ token: string; projectId: string }> {
  const now = Math.floor(Date.now() / 1000);
  if (fcmCache && fcmCache.exp - 60 > now) return fcmCache;

  const sa = JSON.parse(FIREBASE_SERVICE_ACCOUNT);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }),
  );
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(String(sa.private_key).replace(/\\n/g, "\n")),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claim}`));

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${header}.${claim}.${b64url(sig)}`,
    }),
  });
  const j = await res.json();
  if (!j.access_token) throw new Error("FCM token request failed");
  fcmCache = { token: j.access_token, exp: now + (j.expires_in ?? 3600), projectId: sa.project_id };
  return fcmCache;
}

async function push(userId: string, title: string, body: string, orderId: string) {
  const tokens = await pushTokens(userId);
  if (tokens.length === 0) {
    console.warn(`push: user ${userId} has NO registered tokens`);
    return 0;
  }
  const { token, projectId } = await fcm();
  let sent = 0;
  await Promise.allSettled(
    tokens.map(async (fcmToken) => {
      const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          message: {
            token: fcmToken,
            notification: { title, body },
            data: { order_id: String(orderId), type: "order_status" },
            android: {
              priority: "HIGH",
              notification: { sound: "default", channel_id: "bharat_kirana_orders" },
            },
          },
        }),
      });
      if (res.ok) {
        sent++;
        return;
      }
      const text = await res.text();
      console.warn(`FCM ${res.status} for ...${fcmToken.slice(-12)}: ${text}`);
      if (res.status === 404 || text.includes("UNREGISTERED") || text.includes("INVALID_ARGUMENT")) {
        await dropDeadToken(fcmToken);
      }
    }),
  );
  console.log(`push: user ${userId} — ${sent}/${tokens.length} delivered`);
  return sent;
}

async function notify(userId: string, title: string, body: string, orderId: string) {
  await insertNotification(userId, title, body, orderId);
  return push(userId, title, body, orderId);
}

/* ------------------------------------------------------------ messages --- */

function itemsSummary(o: Order): string {
  const raw = Array.isArray(o.items_json) ? o.items_json : Array.isArray(o.items) ? o.items : [];
  const count = raw.reduce((n: number, it: any) => n + Number(it?.quantity ?? it?.qty ?? 1), 0);
  const first: string = raw[0]?.product_name ?? raw[0]?.name ?? "";
  if (count > 1 && first) return `${first} +${count - 1} more`;
  return first || `${count} item${count === 1 ? "" : "s"}`;
}

function cancelReason(o: Order): string {
  return typeof o.cancel_reason === "string" ? o.cancel_reason.trim() : "";
}

function customerCancelBody(o: Order, label: string, total: string): string {
  const reason = cancelReason(o);
  switch (o.cancelled_by) {
    case "admin":
      return reason ? `${label} was cancelled by BreakQ support: ${reason}` : `${label} was cancelled by BreakQ support.`;
    case "vendor":
      return reason ? `${label} was cancelled by the shop: ${reason}` : `${label} was cancelled by the shop.`;
    case "system":
      return `${label} was cancelled because the shop didn't accept it in time.`;
    default:
      return `${label} was cancelled${total}.`;
  }
}

// Sent to the shop for cancellations it didn't make itself.
function vendorCancelMessage(o: Order): { title: string; body: string } {
  const label = displayNumber(o);
  const reason = cancelReason(o);
  switch (o.cancelled_by) {
    case "admin":
      return {
        title: "Order cancelled by BreakQ",
        body: `Order ${label} was cancelled by support${reason ? `: ${reason}` : ""}.`,
      };
    case "customer":
      return { title: "Order cancelled by customer", body: `Order ${label} was cancelled by the customer. No need to prepare it.` };
    case "system":
      return { title: "Order expired", body: `Order ${label} was cancelled automatically because it wasn't accepted in time.` };
    default:
      return { title: "Order cancelled", body: `Order ${label} was cancelled.` };
  }
}

function customerMessage(status: string, o: Order): { title: string; body: string } | null {
  const label = displayNumber(o);
  const items = itemsSummary(o);
  const total = Number(o.total_amount ?? 0) > 0 ? ` · ₹${o.total_amount}` : "";
  const msgs: Record<string, { title: string; body: string }> = {
    "Order Confirmed": {
      title: "Order confirmed ✅",
      body: `${label} — ${items}${total}. We'll let you know when it's ready.`,
    },
    "Preparing": {
      title: "Preparing your order 👨‍🍳",
      body: `${label} — shop started preparing. ${items}${total}.`,
    },
    "Ready for Pickup": {
      title: "Ready for pickup! 🛍️",
      body: `${label} — ${items}${total}. Show your QR at the shop.`,
    },
    "Completed": {
      title: "Order picked up ✅",
      body: `${label} — thank you! Rate your experience?`,
    },
    "Cancelled": {
      title: "Order cancelled",
      body: customerCancelBody(o, label, total),
    },
  };
  return msgs[status] ?? null;
}

/* ------------------------------------------------------------ handlers --- */

async function handleInsert(o: Order): Promise<Response> {
  const age = Date.now() - new Date(o.created_at || 0).getTime();
  if (!(age >= 0 && age <= NEW_ORDER_MAX_AGE_MS)) return new Response("Old order — skipped", { status: 200 });
  if (!o.shop_id) return new Response("Skipped: order has no shop_id", { status: 200 });

  const owner = await shopOwnerId(o.shop_id);
  if (!owner) return new Response("Skipped: no shop owner", { status: 200 });

  const sent = await notify(
    owner,
    "New order received",
    `Order ${displayNumber(o)} — ₹${o.total_amount ?? 0}. Tap to open.`,
    o.id,
  );
  return new Response(`OK (vendor notified, ${sent} push)`, { status: 200 });
}

async function handleUpdate(o: Order, oldStatus: unknown): Promise<Response> {
  const status = o.status;
  if (!status || status === oldStatus) return new Response("No status change", { status: 200 });

  const m = customerMessage(status, o);
  if (!m) return new Response("No message for status " + status, { status: 200 });

  const results: string[] = [];
  const customer = await customerId(o);
  if (customer) results.push(`customer ${await notify(customer, m.title, m.body, o.id)} push`);
  else results.push("no customer profile");

  // The shop already knows about its own cancellations.
  if (status === "Cancelled" && o.cancelled_by !== "vendor" && o.shop_id) {
    const owner = await shopOwnerId(o.shop_id);
    if (owner) {
      const v = vendorCancelMessage(o);
      await notify(owner, v.title, v.body, o.id);
      results.push("vendor notified");
    }
  }
  return new Response(`OK (${results.join(", ")})`, { status: 200 });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("POST only", { status: 405 });
  if (!WEBHOOK_SECRET || !SUPABASE_URL || !SERVICE_ROLE_KEY || !FIREBASE_SERVICE_ACCOUNT) {
    console.error("notify-order-status: secrets not configured — refusing");
    return new Response("Not configured", { status: 500 });
  }
  if (!safeEqual(req.headers.get("x-webhook-secret") || "", WEBHOOK_SECRET)) {
    return new Response("Unauthorized", { status: 401 });
  }

  try {
    const p: WebhookPayload = await req.json();
    if (p.table !== "orders" || !p.record?.id) return new Response("Ignored", { status: 200 });

    // Trust the database, not the request body.
    const order = await loadOrder(String(p.record.id));
    if (!order) return new Response("Order not found", { status: 200 });

    if (p.type === "INSERT") return await handleInsert(order);
    if (p.type === "UPDATE") {
      // Stale or forged event: the order's real status must be the one announced.
      if (order.status !== p.record.status) return new Response("Stale event", { status: 200 });
      return await handleUpdate(order, p.old_record?.status);
    }
    return new Response("Ignored", { status: 200 });
  } catch (e) {
    console.error(e);
    return new Response("Error", { status: 500 });
  }
});
