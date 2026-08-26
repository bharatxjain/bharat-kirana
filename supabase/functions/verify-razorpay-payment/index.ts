// Supabase Edge Function: verify-razorpay-payment
// Deploy at: Dashboard → Edge Functions → New function → name it exactly
//            "verify-razorpay-payment" → paste this → Deploy.
//
// Secrets required: RAZORPAY_KEY_SECRET, SERVICE_ROLE_KEY
//
// The app can lie. This function recomputes the HMAC-SHA256 signature over
// "<order_id>|<payment_id>" using the key secret and only upgrades the vendor's
// tier if it matches what Razorpay sent back.

import { serve } from "https://deno.land/std@0.208.0/http/server.ts";

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

async function hmacSha256Hex(secret: string, message: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const { razorpay_order_id, razorpay_payment_id, razorpay_signature } = await req.json();
    if (!razorpay_order_id || !razorpay_payment_id || !razorpay_signature) {
      return new Response(JSON.stringify({ error: "Missing payment fields" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 1. Signature check — the whole security boundary lives here.
    const expected = await hmacSha256Hex(
      RAZORPAY_KEY_SECRET,
      `${razorpay_order_id}|${razorpay_payment_id}`,
    );
    if (expected !== razorpay_signature) {
      console.error(">>> signature mismatch for order", razorpay_order_id);
      await db(`subscription_payments?razorpay_order_id=eq.${razorpay_order_id}`, {
        method: "PATCH",
        headers: { Prefer: "return=minimal" },
        body: JSON.stringify({ status: "failed" }),
      });
      return new Response(JSON.stringify({ error: "Signature verification failed" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 2. Find the intent we recorded when the order was created.
    const payRes = await db(
      `subscription_payments?razorpay_order_id=eq.${razorpay_order_id}&select=shop_id,tier_id,amount_rupees,status`,
    );
    const rows = await payRes.json();
    if (!rows.length) throw new Error("No matching payment intent found");
    const { shop_id, tier_id, amount_rupees, status } = rows[0];

    // Idempotency: a retried callback shouldn't extend the subscription twice.
    if (status === "paid") {
      return new Response(JSON.stringify({ ok: true, note: "already processed" }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 3. Mark the payment paid.
    await db(`subscription_payments?razorpay_order_id=eq.${razorpay_order_id}`, {
      method: "PATCH",
      headers: { Prefer: "return=minimal" },
      body: JSON.stringify({
        razorpay_payment_id,
        razorpay_signature,
        status: "paid",
        verified_at: new Date().toISOString(),
      }),
    });

    // 4. Retire the old active subscription, then activate the new tier for 1 month.
    await db(`vendor_subscriptions?shop_id=eq.${shop_id}&status=eq.active`, {
      method: "PATCH",
      headers: { Prefer: "return=minimal" },
      body: JSON.stringify({ status: "cancelled" }),
    });

    const expires = new Date();
    expires.setMonth(expires.getMonth() + 1);

    await db("vendor_subscriptions", {
      method: "POST",
      headers: { Prefer: "return=minimal" },
      body: JSON.stringify({
        shop_id,
        tier_id,
        status: "active",
        started_at: new Date().toISOString(),
        expires_at: expires.toISOString(),
        payment_ref: razorpay_payment_id,
        amount_paid_rupees: amount_rupees,
      }),
    });

    console.log(`>>> ${shop_id} upgraded to ${tier_id} until ${expires.toISOString()}`);
    return new Response(JSON.stringify({ ok: true, tier_id, expires_at: expires.toISOString() }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (e) {
    console.error(">>> verify-razorpay-payment failed:", e);
    return new Response(JSON.stringify({ error: String(e) }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
