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

serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const { shop_id, tier_id } = await req.json();
    if (!shop_id || !tier_id) {
      return new Response(JSON.stringify({ error: "shop_id and tier_id are required" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // Price comes from the DB, never from the client.
    const tierRes = await db(`subscription_tiers?id=eq.${tier_id}&select=price_rupees,display_name`);
    const tiers = await tierRes.json();
    if (!tiers.length) throw new Error(`Unknown tier ${tier_id}`);
    const priceRupees: number = tiers[0].price_rupees;
    if (priceRupees <= 0) throw new Error("This plan is free — no payment required.");

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
    if (!rzpRes.ok) throw new Error(`Razorpay: ${JSON.stringify(rzpBody)}`);

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

    return new Response(
      JSON.stringify({
        order_id: rzpBody.id,
        amount: amountPaise,
        currency: "INR",
        key_id: RAZORPAY_KEY_ID,
      }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  } catch (e) {
    console.error(">>> create-razorpay-order failed:", e);
    return new Response(JSON.stringify({ error: String(e) }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
