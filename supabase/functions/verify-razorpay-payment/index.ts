// Supabase Edge Function: verify-razorpay-payment
// Deploy at: Dashboard → Edge Functions → New function → name it exactly
//            "verify-razorpay-payment" → paste this → Deploy.
//
// Secrets required: RAZORPAY_KEY_SECRET, SERVICE_ROLE_KEY
//
// The app can lie. This function recomputes the HMAC-SHA256 signature over
// "<order_id>|<payment_id>" using the key secret and only upgrades the vendor's
// tier if it matches what Razorpay sent back.
//
// A bad signature changes nothing and never reports success, even for an order
// that is already paid. Activation happens at most once per Razorpay order: the
// payment row is claimed with a single conditional UPDATE, so retries and
// concurrent calls cannot extend the subscription again.

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

function safeEqual(a: string, b: string): boolean {
  const x = new TextEncoder().encode(a);
  const y = new TextEncoder().encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

const ORDER_ID_RE = /^order_[A-Za-z0-9]{6,40}$/;
const PAYMENT_ID_RE = /^pay_[A-Za-z0-9]{6,40}$/;
const SIGNATURE_RE = /^[a-f0-9]{64}$/;

serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "POST only" }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    const orderId = body?.razorpay_order_id;
    const paymentId = body?.razorpay_payment_id;
    const signature = body?.razorpay_signature;
    if (
      typeof orderId !== "string" || !ORDER_ID_RE.test(orderId) ||
      typeof paymentId !== "string" || !PAYMENT_ID_RE.test(paymentId) ||
      typeof signature !== "string" || !SIGNATURE_RE.test(signature)
    ) {
      return json({ error: "Missing payment fields" }, 400);
    }

    // 1. Signature check — the whole security boundary lives here.
    const expected = await hmacSha256Hex(RAZORPAY_KEY_SECRET, `${orderId}|${paymentId}`);
    if (!safeEqual(expected, signature)) {
      console.warn("verify-razorpay-payment: signature mismatch for", orderId);
      return json({ error: "Signature verification failed" }, 401);
    }

    const orderFilter = `razorpay_order_id=eq.${encodeURIComponent(orderId)}`;

    // 2. Claim the payment. Only one request can move it to 'paid'; any later
    //    or concurrent request updates zero rows and stops here.
    const claimed = await (await db(`subscription_payments?${orderFilter}&status=neq.paid`, {
      method: "PATCH",
      headers: { Prefer: "return=representation" },
      body: JSON.stringify({
        razorpay_payment_id: paymentId,
        razorpay_signature: signature,
        status: "paid",
        verified_at: new Date().toISOString(),
      }),
    })).json();

    if (!claimed.length) {
      const existing = await (await db(`subscription_payments?${orderFilter}&select=status`)).json();
      if (!existing.length) return json({ error: "No matching payment found" }, 404);
      return json({ ok: true, note: "already processed" });
    }

    const { shop_id, tier_id, amount_rupees } = claimed[0];
    const expires = new Date();
    expires.setMonth(expires.getMonth() + 1);

    // 3. Retire the old active subscription, then activate the new tier for 1 month.
    try {
      await db(`vendor_subscriptions?shop_id=eq.${encodeURIComponent(shop_id)}&status=eq.active`, {
        method: "PATCH",
        headers: { Prefer: "return=minimal" },
        body: JSON.stringify({ status: "cancelled" }),
      });

      await db("vendor_subscriptions", {
        method: "POST",
        headers: { Prefer: "return=minimal" },
        body: JSON.stringify({
          shop_id,
          tier_id,
          status: "active",
          started_at: new Date().toISOString(),
          expires_at: expires.toISOString(),
          payment_ref: paymentId,
          amount_paid_rupees: amount_rupees,
        }),
      });
    } catch (e) {
      // Hand the claim back so a retry can finish the activation.
      await db(`subscription_payments?${orderFilter}&status=eq.paid`, {
        method: "PATCH",
        headers: { Prefer: "return=minimal" },
        body: JSON.stringify({ status: "created", verified_at: null }),
      }).catch(() => {});
      throw e;
    }

    console.log(`verify-razorpay-payment: ${shop_id} upgraded to ${tier_id} until ${expires.toISOString()}`);
    return json({ ok: true, tier_id, expires_at: expires.toISOString() });
  } catch (e) {
    console.error("verify-razorpay-payment failed:", e instanceof Error ? e.message : e);
    return json({ error: "Payment received but activation failed. Please contact support." }, 500);
  }
});
