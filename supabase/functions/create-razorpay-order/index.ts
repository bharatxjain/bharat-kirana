// Supabase Edge Function: create-razorpay-order
// Deploy at: Dashboard → Edge Functions → New function → name it exactly
//            "create-razorpay-order" → paste this → Deploy.
//
// Secrets required (Edge Functions → Settings → Secrets):
//   RAZORPAY_KEY_ID       e.g. rzp_test_XXXXXXXX
//   RAZORPAY_KEY_SECRET   (never ships in the APK)
//   SERVICE_ROLE_KEY      Supabase service_role key (Settings → API)
//
// Flow: app POSTs { shop_id, tier_id } with the user's JWT → we look up the
// tier price server-side (so a tampered client can't buy Pro for ₹1), create a
// Razorpay order, record it as 'created', and return the order id.
//
// Security: the caller's JWT is resolved to a user server-side and that user
// must own shop_id. "Verify JWT" alone is not enough — the public anon key is
// also a valid JWT.

import { serve } from "https://deno.land/std@0.208.0/http/server.ts";

const RAZORPAY_KEY_ID = Deno.env.get("RAZORPAY_KEY_ID")!;
const RAZORPAY_KEY_SECRET = Deno.env.get("RAZORPAY_KEY_SECRET")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SERVICE_ROLE_KEY")!;

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

async function db(path: string, init?: RequestInit) {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/${path}`, {
    ...init,
    headers: {
      apikey: SERVICE_ROLE_KEY,
      Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });
  if (!res.ok) throw new Error(`DB ${path} -> ${res.status} ${await res.text()}`);
  return res;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function isId(v: unknown): v is string {
  return typeof v === "string" && /^[A-Za-z0-9_-]{1,80}$/.test(v);
}

// The user behind the caller's JWT, or null for the anon key / an expired token.
async function callerId(req: Request): Promise<string | null> {
  const authHeader = req.headers.get("Authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) return null;
  const r = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: SERVICE_ROLE_KEY, Authorization: authHeader },
  });
  if (!r.ok) return null;
  const u = await r.json().catch(() => null);
  return typeof u?.id === "string" ? u.id : null;
}

serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "POST only" }, 405);

  try {
    const uid = await callerId(req);
    if (!uid) return json({ error: "Please sign in again." }, 401);

    const { shop_id, tier_id } = await req.json().catch(() => ({}));
    if (!isId(shop_id) || !isId(tier_id)) {
      return json({ error: "shop_id and tier_id are required" }, 400);
    }

    const shops = await (await db(
      `shops?id=eq.${encodeURIComponent(shop_id)}&select=owner_id`,
    )).json();
    if (!shops.length || shops[0].owner_id !== uid) {
      return json({ error: "You can only buy a plan for your own shop." }, 403);
    }

    // Price comes from the DB, never from the client.
    const tiers = await (await db(
      `subscription_tiers?id=eq.${encodeURIComponent(tier_id)}&is_active=eq.true&select=price_rupees`,
    )).json();
    if (!tiers.length) return json({ error: "That plan is not available." }, 400);
    const priceRupees = Number(tiers[0].price_rupees);
    if (!Number.isInteger(priceRupees) || priceRupees <= 0) {
      return json({ error: "This plan is free — no payment required." }, 400);
    }

    const amountPaise = priceRupees * 100;

    // Create the Razorpay order.
    const auth = btoa(`${RAZORPAY_KEY_ID}:${RAZORPAY_KEY_SECRET}`);
    const rzpRes = await fetch("https://api.razorpay.com/v1/orders", {
      method: "POST",
      headers: { Authorization: `Basic ${auth}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        amount: amountPaise,
        currency: "INR",
        receipt: `${shop_id}_${tier_id}_${Date.now()}`.slice(0, 40),
        notes: { shop_id, tier_id, product: "BreakQ" },
      }),
    });
    const rzpBody = await rzpRes.json();
    if (!rzpRes.ok) {
      console.error("create-razorpay-order: Razorpay refused", rzpRes.status, rzpBody?.error?.description);
      return json({ error: "Could not start payment. Please try again." }, 502);
    }

    // Record the intent so verify-razorpay-payment can match it later.
    await db("subscription_payments", {
      method: "POST",
      headers: { Prefer: "return=minimal" },
      body: JSON.stringify({
        shop_id,
        tier_id,
        razorpay_order_id: rzpBody.id,
        amount_rupees: priceRupees,
        status: "created",
      }),
    });

    return json({
      order_id: rzpBody.id,
      amount: amountPaise,
      currency: "INR",
      key_id: RAZORPAY_KEY_ID,
    });
  } catch (e) {
    console.error("create-razorpay-order failed:", e instanceof Error ? e.message : e);
    return json({ error: "Could not start payment. Please try again." }, 500);
  }
});
