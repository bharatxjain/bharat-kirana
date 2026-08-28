// Supabase Edge Function: vendor-status-changed
// Deploy at: Dashboard → Edge Functions → New function → name it exactly
//            "vendor-status-changed" → paste this → Deploy.
//
// Triggered by: a Database Webhook on `public.shops` UPDATE.
//
// What it does:
//   1. Only fires when shops.status actually changed
//   2. Sends the vendor an email tailored to the new status
//   3. Inserts a matching row into public.notifications
//
// Secrets required:
//   RESEND_API_KEY, FROM_EMAIL, APP_NAME, ADMIN_PANEL_URL,
//   SUPABASE_URL (auto), SERVICE_ROLE_KEY

import { serve } from "https://deno.land/std@0.208.0/http/server.ts";

const RESEND_API_KEY = Deno.env.get("RESEND_API_KEY")!;
const FROM_EMAIL = Deno.env.get("FROM_EMAIL") ?? "onboarding@resend.dev";
const APP_NAME = Deno.env.get("APP_NAME") ?? "BreakQ";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SERVICE_ROLE_KEY")!;

const PURPLE = "#6C00FF";
const GREEN = "#059669";
const RED = "#DC2626";

interface ShopRow {
  id: string;
  name: string;
  owner_name?: string;
  owner_id?: string;
  status?: string;
  rejection_reason?: string;
}

interface WebhookPayload {
  type: string;
  table: string;
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

function shell(body: string, accent: string): string {
  return `
    <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
                max-width:560px;margin:0 auto;padding:24px;color:#111827">
      <div style="background:${accent};color:#fff;padding:16px 20px;border-radius:12px 12px 0 0;
                  font-size:20px;font-weight:700">${APP_NAME}</div>
      <div style="background:#fff;border:1px solid #E5E7EB;border-top:0;border-radius:0 0 12px 12px;
                  padding:24px">${body}</div>
    </div>`;
}

function approvedBody(shop: ShopRow): string {
  return shell(`
    <h2 style="margin:0 0 12px;color:${GREEN}">Your shop is approved 🎉</h2>
    <p>Hi ${shop.owner_name ?? "there"},</p>
    <p><b>${shop.name}</b> is now live on ${APP_NAME}. Customers in your area
       can find your shop, browse your products, and place pickup orders.</p>
    <p>Open the ${APP_NAME} app to add products, set stock levels, and start
       receiving orders.</p>`, GREEN);
}

function rejectedBody(shop: ShopRow): string {
  const reason = shop.rejection_reason?.trim();
  return shell(`
    <h2 style="margin:0 0 12px;color:${RED}">Registration not approved</h2>
    <p>Hi ${shop.owner_name ?? "there"},</p>
    <p>We couldn't approve <b>${shop.name}</b> at this time.</p>
    ${reason ? `<p style="background:#FEF2F2;padding:12px 16px;border-radius:8px;
                          border-left:3px solid ${RED}">
                 <b>Reason:</b> ${reason}</p>` : ""}
    <p>You can update your registration details in the ${APP_NAME} app and
       submit again, or reply to this email if you have questions.</p>`, RED);
}

function suspendedBody(shop: ShopRow): string {
  return shell(`
    <h2 style="margin:0 0 12px;color:${RED}">Your shop is suspended</h2>
    <p>Hi ${shop.owner_name ?? "there"},</p>
    <p><b>${shop.name}</b> has been temporarily suspended and is not visible
       to customers.</p>
    <p>Please reply to this email so we can help resolve the issue.</p>`, RED);
}

serve(async (req) => {
  try {
    const p: WebhookPayload = await req.json();
    if (p.table !== "shops" || p.type !== "UPDATE" || !p.record || !p.old_record) {
      return new Response("Ignored", { status: 200 });
    }

    const newStatus = p.record.status;
    const oldStatus = p.old_record.status;
    if (!newStatus || newStatus === oldStatus) {
      return new Response("No status change", { status: 200 });
    }

    const shop = p.record;
    let subject: string;
    let html: string;
    let notificationTitle: string;
    let notificationMessage: string;

    switch (newStatus) {
      case "approved":
        subject = `Your ${APP_NAME} shop is approved`;
        html = approvedBody(shop);
        notificationTitle = "Your shop is approved 🎉";
        notificationMessage = `${shop.name} is now live on ${APP_NAME}. Start adding products.`;
        break;
      case "rejected":
        subject = `Your ${APP_NAME} registration was not approved`;
        html = rejectedBody(shop);
        notificationTitle = "Registration not approved";
        notificationMessage = shop.rejection_reason ?? "Please review the email we sent for details.";
        break;
      case "suspended":
        subject = `Your ${APP_NAME} shop has been suspended`;
        html = suspendedBody(shop);
        notificationTitle = "Shop suspended";
        notificationMessage = `${shop.name} is temporarily hidden from customers.`;
        break;
      default:
        return new Response(`Status ${newStatus} not messaged`, { status: 200 });
    }

    const vendorEmail = shop.owner_id ? await lookupOwnerEmail(shop.owner_id) : null;

    await Promise.allSettled([
      vendorEmail ? sendMail(vendorEmail, subject, html) : Promise.resolve(),
      shop.owner_id ? logNotification(shop.owner_id, notificationTitle, notificationMessage) : Promise.resolve(),
    ]);

    return new Response("OK", { status: 200 });
  } catch (e) {
    console.error(e);
    return new Response(`Error: ${e}`, { status: 500 });
  }
});
