// Supabase Edge Function: vendor-registered
// Deploy at: Dashboard → Edge Functions → New function → name it exactly
//            "vendor-registered" → paste this → Deploy.
//
// Triggered by: a Database Webhook on `public.shops` INSERT.
//
// What it does:
//   1. Looks up the vendor's email via shops.owner_id -> auth.users
//   2. Emails ADMIN_EMAIL: "New vendor registered, review at ADMIN_PANEL_URL"
//   3. Emails the vendor: "Registration received, awaiting approval"
//   4. Inserts a matching row into public.notifications so the vendor also
//      sees it in-app.
//
// Secrets required:
//   RESEND_API_KEY, ADMIN_EMAIL, FROM_EMAIL, APP_NAME, ADMIN_PANEL_URL,
//   SUPABASE_URL (auto), SERVICE_ROLE_KEY

import { serve } from "https://deno.land/std@0.208.0/http/server.ts";

const RESEND_API_KEY = Deno.env.get("RESEND_API_KEY")!;
const ADMIN_EMAIL = Deno.env.get("ADMIN_EMAIL")!;
const FROM_EMAIL = Deno.env.get("FROM_EMAIL") ?? "onboarding@resend.dev";
const APP_NAME = Deno.env.get("APP_NAME") ?? "BreakQ";
const ADMIN_PANEL_URL = Deno.env.get("ADMIN_PANEL_URL")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SERVICE_ROLE_KEY")!;

const PURPLE = "#6C00FF";

interface ShopRow {
  id: string;
  name: string;
  owner_name?: string;
  owner_id?: string;
  phone?: string;
  address?: string;
  primary_category?: string;
  status?: string;
}

interface WebhookPayload {
  type: string;      // "INSERT" | "UPDATE" | "DELETE"
  table: string;     // "shops"
  record?: ShopRow;
  old_record?: ShopRow;
}

async function lookupOwnerEmail(ownerId: string): Promise<string | null> {
  const r = await fetch(
    `${SUPABASE_URL}/auth/v1/admin/users/${ownerId}`,
    { headers: { apikey: SERVICE_ROLE_KEY, Authorization: `Bearer ${SERVICE_ROLE_KEY}` } },
  );
  if (!r.ok) return null;
  const u = await r.json();
  return u?.email ?? null;
}

async function sendMail(to: string, subject: string, html: string) {
  const r = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${RESEND_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ from: `${APP_NAME} <${FROM_EMAIL}>`, to, subject, html }),
  });
  if (!r.ok) console.error("resend:", r.status, await r.text());
}

async function logNotification(userId: string, title: string, message: string) {
  await fetch(`${SUPABASE_URL}/rest/v1/notifications`, {
    method: "POST",
    headers: {
      apikey: SERVICE_ROLE_KEY,
      Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
      "Content-Type": "application/json",
      Prefer: "return=minimal",
    },
    body: JSON.stringify({ user_id: userId, title, message, is_read: false }),
  });
}

function shell(body: string): string {
  return `
    <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
                max-width:560px;margin:0 auto;padding:24px;color:#111827">
      <div style="background:${PURPLE};color:#fff;padding:16px 20px;border-radius:12px 12px 0 0;
                  font-size:20px;font-weight:700">${APP_NAME}</div>
      <div style="background:#fff;border:1px solid #E5E7EB;border-top:0;border-radius:0 0 12px 12px;
                  padding:24px">
        ${body}
      </div>
    </div>`;
}

function vendorEmailBody(shop: ShopRow): string {
  return shell(`
    <h2 style="margin:0 0 12px;color:${PURPLE}">Registration received</h2>
    <p>Hi ${shop.owner_name ?? "there"},</p>
    <p>We've received your registration for <b>${shop.name}</b>. Our team will
       review your details and get back to you within 24 to 48 hours.</p>
    <p>You can keep the ${APP_NAME} app open — you'll see the update the moment
       your shop is approved.</p>
    <p style="color:#6B7280;font-size:12px;margin-top:24px">
       Shop: ${shop.name}<br>Phone: ${shop.phone ?? "—"}<br>
       Address: ${shop.address ?? "—"}</p>`);
}

function adminEmailBody(shop: ShopRow, vendorEmail: string | null): string {
  return shell(`
    <h2 style="margin:0 0 12px;color:${PURPLE}">New vendor registration</h2>
    <p>A new shop is waiting for your review.</p>
    <table style="border-collapse:collapse;margin:16px 0;font-size:14px">
      <tr><td style="padding:6px 12px 6px 0;color:#6B7280">Shop</td><td><b>${shop.name}</b></td></tr>
      <tr><td style="padding:6px 12px 6px 0;color:#6B7280">Owner</td><td>${shop.owner_name ?? "—"}</td></tr>
      <tr><td style="padding:6px 12px 6px 0;color:#6B7280">Email</td><td>${vendorEmail ?? "—"}</td></tr>
      <tr><td style="padding:6px 12px 6px 0;color:#6B7280">Phone</td><td>${shop.phone ?? "—"}</td></tr>
      <tr><td style="padding:6px 12px 6px 0;color:#6B7280">Address</td><td>${shop.address ?? "—"}</td></tr>
      <tr><td style="padding:6px 12px 6px 0;color:#6B7280">Category</td><td>${shop.primary_category ?? "—"}</td></tr>
    </table>
    <p><a href="${ADMIN_PANEL_URL}" style="display:inline-block;background:${PURPLE};color:#fff;
       padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:600">
       Review in Admin Panel</a></p>`);
}

serve(async (req) => {
  try {
    const p: WebhookPayload = await req.json();
    if (p.table !== "shops" || p.type !== "INSERT" || !p.record) {
      return new Response("Ignored", { status: 200 });
    }
    const shop = p.record;
    const vendorEmail = shop.owner_id ? await lookupOwnerEmail(shop.owner_id) : null;

    // Fire and forget three writes. If any one fails we still want the others
    // attempted — a missing vendor email should not block the admin notification.
    await Promise.allSettled([
      sendMail(ADMIN_EMAIL, `[${APP_NAME}] New vendor: ${shop.name}`, adminEmailBody(shop, vendorEmail)),
      vendorEmail
        ? sendMail(vendorEmail, `Your ${APP_NAME} registration is being reviewed`, vendorEmailBody(shop))
        : Promise.resolve(),
      shop.owner_id
        ? logNotification(shop.owner_id, "Registration received", "Your shop is being reviewed. You'll be notified within 24-48 hours.")
        : Promise.resolve(),
    ]);

    return new Response("OK", { status: 200 });
  } catch (e) {
    console.error(e);
    return new Response(`Error: ${e}`, { status: 500 });
  }
});
