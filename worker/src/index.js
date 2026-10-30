// psalmwell-api: the only writer of public reflections and reports for Psalmwell.
// Every request is authenticated with a Firebase ID token; every reflection is moderated here.
import { moderate, cleanDetails } from "./moderation.js";

const RULES_VERSION = 1;
const MAX_POSTS_PER_DAY = 5;
const MIN_SECONDS_BETWEEN_POSTS = 30;
const MAX_REPORTS_PER_DAY = 30;
const AUTO_HIDE_REPORTS = 3;
const REASONS = new Set(["harassment", "hate", "threat", "sexual", "child_safety", "spam", "self_harm", "other"]);
const IMMEDIATE_HIDE = new Set(["child_safety", "threat", "sexual"]);

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    try {
      if (request.method === "OPTIONS") return cors(env, new Response(null, { status: 204 }));
      if (url.pathname === "/" || url.pathname === "/v1/health") return json(env, { ok: true, service: "psalmwell-api" });
      // Clean-up of expired records runs opportunistically (no cron needed).
      if (request.method !== "GET" || url.pathname === "/admin") { if (Math.random() < 0.05 || url.pathname === "/admin") ctx.waitUntil(cleanup(env).catch((e) => console.error("cleanup", e))); }
      if (url.pathname.startsWith("/admin")) return await admin(request, env, url);
      if (url.pathname === "/v1/reflections" && request.method === "POST") return await postReflection(request, env);
      if (url.pathname === "/v1/reports" && request.method === "POST") return await postReport(request, env);
      if (url.pathname === "/v1/account" && request.method === "DELETE") return await deleteAccount(request, env);
      return json(env, { error: "Not found" }, 404);
    } catch (e) {
      if (e instanceof HttpError) return json(env, { error: e.message }, e.status);
      console.error("unhandled", e && e.stack || e);
      return json(env, { error: "Something went wrong. Please try again." }, 500);
    }
  },

  // Optional daily clean-up if a cron trigger is configured.
  async scheduled(event, env, ctx) {
    ctx.waitUntil(cleanup(env));
  },
};

async function cleanup(env) {
  const before = (field, date, collectionId) => runQuery(env, "", {
    from: [{ collectionId }], where: { fieldFilter: { field: { fieldPath: field }, op: "LESS_THAN", value: { timestampValue: date.toISOString() } } }, limit: 300,
  });
  const yearAgo = new Date(Date.now() - 365 * 86400000);
  for (const d of await before("expiresAt", new Date(), "limits")) await deleteDoc(env, `limits/${d._id}`);
  for (const d of await before("removedAt", yearAgo, "removedReflections")) await deleteDoc(env, `removedReflections/${d._id}`);
  for (const d of await before("createdAt", yearAgo, "reports")) await deleteDoc(env, `reports/${d._id}`);
}

class HttpError extends Error { constructor(status, message) { super(message); this.status = status; } }

function cors(env, res) {
  const h = new Headers(res.headers);
  h.set("Access-Control-Allow-Origin", env.ALLOWED_ORIGIN || "https://psalmwell.web.app");
  h.set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Client");
  h.set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
  h.set("Vary", "Origin");
  return new Response(res.body, { status: res.status, headers: h });
}
function json(env, obj, status = 200) {
  return cors(env, new Response(JSON.stringify(obj), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } }));
}
async function readJson(request) {
  const text = await request.text();
  if (text.length > 8000) throw new HttpError(413, "Request too large.");
  try { return text ? JSON.parse(text) : {}; } catch { throw new HttpError(400, "Invalid request."); }
}
const utcDay = (d = new Date()) => d.toISOString().slice(0, 10);

// ---------- Firebase ID token verification ----------
let jwksCache = { keys: null, exp: 0 };
async function googleKeys() {
  if (jwksCache.keys && Date.now() < jwksCache.exp) return jwksCache.keys;
  const res = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
  if (!res.ok) throw new HttpError(503, "Sign-in check is unavailable. Please try again.");
  const body = await res.json();
  const maxAge = Number((res.headers.get("cache-control") || "").match(/max-age=(\d+)/)?.[1] || 3600);
  jwksCache = { keys: body.keys, exp: Date.now() + maxAge * 1000 };
  return body.keys;
}
const b64urlToBytes = (s) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4)), (c) => c.charCodeAt(0));
const b64urlJson = (s) => JSON.parse(new TextDecoder().decode(b64urlToBytes(s)));

async function verifyIdToken(request, env) {
  const auth = request.headers.get("Authorization") || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  if (!token) throw new HttpError(401, "Please sign in.");
  const [h, p, s] = token.split(".");
  if (!h || !p || !s) throw new HttpError(401, "Please sign in again.");
  const header = b64urlJson(h), payload = b64urlJson(p);
  const now = Math.floor(Date.now() / 1000);
  const project = env.FIREBASE_PROJECT_ID;
  if (header.alg !== "RS256" || payload.aud !== project || payload.iss !== `https://securetoken.google.com/${project}` ||
      !payload.sub || payload.exp < now - 30 || payload.iat > now + 300) throw new HttpError(401, "Please sign in again.");
  const jwk = (await googleKeys()).find((k) => k.kid === header.kid);
  if (!jwk) { jwksCache.exp = 0; throw new HttpError(401, "Please sign in again."); }
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, b64urlToBytes(s), new TextEncoder().encode(`${h}.${p}`));
  if (!ok) throw new HttpError(401, "Please sign in again.");
  return { uid: payload.sub, email: payload.email || null, emailVerified: !!payload.email_verified };
}

// ---------- Service account access token ----------
let accessCache = { token: null, exp: 0 };
const bytesToB64url = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const strToB64url = (s) => bytesToB64url(new TextEncoder().encode(s));
async function accessToken(env) {
  if (accessCache.token && Date.now() < accessCache.exp) return accessCache.token;
  const pem = String(env.FIREBASE_PRIVATE_KEY || "").replace(/\\n/g, "\n");
  const der = Uint8Array.from(atob(pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "")), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const now = Math.floor(Date.now() / 1000);
  const unsigned = strToB64url(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." + strToB64url(JSON.stringify({
    iss: env.FIREBASE_CLIENT_EMAIL, scope: "https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/identitytoolkit",
    aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
  }));
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${unsigned}.${bytesToB64url(sig)}` }),
  });
  if (!res.ok) { console.error("token", res.status, await res.text()); throw new HttpError(503, "Service temporarily unavailable."); }
  const body = await res.json();
  accessCache = { token: body.access_token, exp: Date.now() + (body.expires_in - 120) * 1000 };
  return accessCache.token;
}

// ---------- Firestore REST helpers ----------
function toValue(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (v instanceof Date) return { timestampValue: v.toISOString() };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(toValue) } };
  if (typeof v === "object") return { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, toValue(x)])) } };
  return { stringValue: String(v) };
}
function fromValue(v) {
  if (!v) return null;
  if ("stringValue" in v) return v.stringValue;
  if ("integerValue" in v) return Number(v.integerValue);
  if ("doubleValue" in v) return v.doubleValue;
  if ("booleanValue" in v) return v.booleanValue;
  if ("timestampValue" in v) return v.timestampValue;
  if ("nullValue" in v) return null;
  if ("mapValue" in v) return Object.fromEntries(Object.entries(v.mapValue.fields || {}).map(([k, x]) => [k, fromValue(x)]));
  if ("arrayValue" in v) return (v.arrayValue.values || []).map(fromValue);
  return null;
}
const docData = (doc) => doc ? { _name: doc.name, _id: doc.name.split("/").pop(), ...Object.fromEntries(Object.entries(doc.fields || {}).map(([k, v]) => [k, fromValue(v)])) } : null;
const base = (env) => `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents`;

async function fs(env, method, path, body) {
  const res = await fetch(path.startsWith("https://") ? path : `${base(env)}/${path}`, {
    method, headers: { Authorization: `Bearer ${await accessToken(env)}`, "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 404 && method === "GET") return null;
  if (!res.ok) { const t = await res.text(); console.error("firestore", method, path, res.status, t.slice(0, 500)); throw new HttpError(res.status === 409 ? 409 : 502, "Service temporarily unavailable."); }
  return res.status === 204 ? {} : res.json();
}
const getDoc = async (env, path) => docData(await fs(env, "GET", path));
const setDoc = (env, path, data) => fs(env, "PATCH", path, { fields: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, toValue(v)])) });
const deleteDoc = (env, path) => fs(env, "DELETE", path);
async function createDoc(env, collectionPath, data, id) {
  const q = id ? `?documentId=${encodeURIComponent(id)}` : "";
  return docData(await fs(env, "POST", `${collectionPath}${q}`, { fields: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, toValue(v)])) }));
}
async function listDocs(env, collectionPath, pageSize = 300) {
  const out = []; let token = "";
  for (let i = 0; i < 20; i++) {
    const r = await fs(env, "GET", `${collectionPath}?pageSize=${pageSize}${token ? `&pageToken=${encodeURIComponent(token)}` : ""}`);
    (r?.documents || []).forEach((d) => out.push(docData(d)));
    if (!r?.nextPageToken) break; token = r.nextPageToken;
  }
  return out;
}
async function runQuery(env, parent, structuredQuery) {
  const r = await fs(env, "POST", `${base(env)}${parent ? "/" + parent : ""}:runQuery`, { structuredQuery });
  return (r || []).filter((x) => x.document).map((x) => docData(x.document));
}

// ---------- Endpoints ----------
async function requireActiveUser(env, uid) {
  const mod = await getDoc(env, `moderation/${uid}`);
  if (mod?.banned) throw new HttpError(403, "This account can no longer post. To appeal, email support from Settings > Contact & appeals.");
  return mod;
}

async function postReflection(request, env) {
  const user = await verifyIdToken(request, env);
  const body = await readJson(request);
  await requireActiveUser(env, user.uid);
  const profile = await getDoc(env, `users/${user.uid}`);
  if (!profile || Number(profile.rulesAcceptedVersion || 0) < RULES_VERSION) throw new HttpError(403, "Please agree to the community rules before posting.");
  const today = utcDay();
  const dayKey = String(body.dayKey || "");
  if (dayKey !== today && dayKey !== utcDay(new Date(Date.now() - 6 * 3600 * 1000))) throw new HttpError(400, "Reflections are for today's verse. Please refresh and try again.");
  const result = moderate(body.body);
  if (!result.allowed) throw new HttpError(422, result.message);

  const limitPath = `limits/${user.uid}_${today}`;
  const limit = await getDoc(env, limitPath);
  const count = Number(limit?.posts || 0);
  if (count >= MAX_POSTS_PER_DAY) throw new HttpError(429, `You can share up to ${MAX_POSTS_PER_DAY} reflections a day. Come back tomorrow.`);
  if (limit?.lastPostAt && Date.now() - Date.parse(limit.lastPostAt) < MIN_SECONDS_BETWEEN_POSTS * 1000) throw new HttpError(429, "Please wait a moment before posting again.");

  const pub = await getDoc(env, `publicProfiles/${user.uid}`);
  const authorName = String(pub?.displayName || profile.displayName || "Friend").slice(0, 40);
  const now = new Date();
  const doc = await createDoc(env, `days/${dayKey}/reflections`, {
    authorUid: user.uid, authorName, body: String(body.body).trim(), reference: String(body.reference || "").slice(0, 40),
    status: "visible", createdAt: now, reportCount: 0,
  });
  await Promise.all([
    createDoc(env, `userReflections/${user.uid}/items`, { dayKey, createdAt: now }, doc._id),
    setDoc(env, limitPath, { posts: count + 1, reports: Number(limit?.reports || 0), lastPostAt: now, expiresAt: new Date(Date.now() + 3 * 86400000) }),
  ]);
  return json(env, { ok: true, id: doc._id });
}

async function postReport(request, env) {
  const user = await verifyIdToken(request, env);
  const body = await readJson(request);
  const type = body.type === "user" ? "user" : body.type === "reflection" ? "reflection" : null;
  const reason = String(body.reason || "");
  const targetUid = String(body.targetUid || "");
  if (!type || !REASONS.has(reason) || !/^[A-Za-z0-9]{10,128}$/.test(targetUid)) throw new HttpError(400, "Invalid report.");
  if (targetUid === user.uid) throw new HttpError(400, "You can't report yourself.");
  const today = utcDay();
  const limitPath = `limits/${user.uid}_${today}`;
  const limit = await getDoc(env, limitPath);
  if (Number(limit?.reports || 0) >= MAX_REPORTS_PER_DAY) throw new HttpError(429, "You've sent a lot of reports today. Our team will review them.");

  let snapshot = null, reflectionPath = null;
  const dayKey = body.dayKey ? String(body.dayKey) : null, reflectionId = body.reflectionId ? String(body.reflectionId) : null;
  if (dayKey && reflectionId && /^\d{4}-\d{2}-\d{2}$/.test(dayKey) && /^[A-Za-z0-9]{1,64}$/.test(reflectionId)) {
    reflectionPath = `days/${dayKey}/reflections/${reflectionId}`;
    const r = await getDoc(env, reflectionPath);
    if (r) snapshot = { body: r.body, authorUid: r.authorUid, authorName: r.authorName, dayKey };
  }
  if (type === "reflection" && !snapshot) throw new HttpError(404, "That reflection is no longer available.");

  const now = new Date();
  await createDoc(env, "reports", {
    type, reason, details: cleanDetails(body.details), reporterUid: user.uid, targetUid,
    dayKey, reflectionId, snapshot, status: "open", createdAt: now,
  });
  await setDoc(env, limitPath, { posts: Number(limit?.posts || 0), reports: Number(limit?.reports || 0) + 1, lastPostAt: limit?.lastPostAt ? new Date(limit.lastPostAt) : null, expiresAt: new Date(Date.now() + 3 * 86400000) });

  if (type === "reflection" && snapshot) {
    const r = await getDoc(env, reflectionPath);
    const reporters = new Set([...(r?.reporters || []), user.uid]);
    if (IMMEDIATE_HIDE.has(reason) || reporters.size >= AUTO_HIDE_REPORTS) {
      await hideReflection(env, dayKey, reflectionId, `auto:${reason}`);
    } else if (r) {
      await setDoc(env, reflectionPath, { ...stripMeta(r), reporters: [...reporters], reportCount: reporters.size, createdAt: new Date(r.createdAt) });
    }
  }
  return json(env, { ok: true });
}
const stripMeta = (d) => Object.fromEntries(Object.entries(d).filter(([k]) => !k.startsWith("_")));

async function hideReflection(env, dayKey, reflectionId, why) {
  const path = `days/${dayKey}/reflections/${reflectionId}`;
  const r = await getDoc(env, path);
  if (!r) return false;
  await setDoc(env, `removedReflections/${dayKey}_${reflectionId}`, { ...stripMeta(r), createdAt: new Date(r.createdAt), status: "removed", removedReason: why, removedAt: new Date(), dayKey, reflectionId });
  await deleteDoc(env, path);
  return true;
}
async function restoreReflection(env, removedId) {
  const r = await getDoc(env, `removedReflections/${removedId}`);
  if (!r) return false;
  const { dayKey, reflectionId } = r;
  const data = stripMeta(r);
  delete data.removedReason; delete data.removedAt; delete data.dayKey; delete data.reflectionId; delete data.reporters;
  await setDoc(env, `days/${dayKey}/reflections/${reflectionId}`, { ...data, status: "visible", reportCount: 0, createdAt: new Date(r.createdAt) });
  await deleteDoc(env, `removedReflections/${removedId}`);
  return true;
}

async function deleteAccount(request, env) {
  const user = await verifyIdToken(request, env);
  await deleteUserData(env, user.uid);
  const res = await fetch(`https://identitytoolkit.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/accounts:delete`, {
    method: "POST", headers: { Authorization: `Bearer ${await accessToken(env)}`, "Content-Type": "application/json" },
    body: JSON.stringify({ localId: user.uid }),
  });
  if (!res.ok) { console.error("auth delete", res.status, await res.text()); throw new HttpError(502, "Your content was deleted, but the account could not be removed. Please try again."); }
  return json(env, { ok: true });
}

async function deleteUserData(env, uid) {
  const items = await listDocs(env, `userReflections/${uid}/items`);
  for (const it of items) {
    if (it.dayKey) await deleteDoc(env, `days/${it.dayKey}/reflections/${it._id}`);
    await deleteDoc(env, `userReflections/${uid}/items/${it._id}`);
  }
  for (const b of await listDocs(env, `users/${uid}/blocked`)) await deleteDoc(env, `users/${uid}/blocked/${b._id}`);
  await deleteDoc(env, `users/${uid}`);
  await deleteDoc(env, `publicProfiles/${uid}`);
  // Reports this person filed are kept for safety review, without their account id.
  const filed = await runQuery(env, "", { from: [{ collectionId: "reports" }], where: { fieldFilter: { field: { fieldPath: "reporterUid" }, op: "EQUAL", value: { stringValue: uid } } }, limit: 500 });
  for (const r of filed) await fs(env, "PATCH", `${r._name.split("/documents/")[1]}?updateMask.fieldPaths=reporterUid`, { fields: { reporterUid: { stringValue: "deleted-account" } } });
}

// ---------- Moderator console ----------
async function admin(request, env, url) {
  const cookie = (request.headers.get("Cookie") || "").match(/pw_admin=([^;]+)/)?.[1];
  let token = cookie ? decodeURIComponent(cookie) : "";
  if (request.method === "POST" && url.pathname === "/admin/login") {
    const form = await request.formData();
    token = String(form.get("token") || "");
    if (!env.ADMIN_TOKEN || token !== env.ADMIN_TOKEN) return html(loginPage("That token is not right."), 401);
    return new Response(null, { status: 303, headers: { Location: "/admin", "Set-Cookie": `pw_admin=${encodeURIComponent(token)}; Path=/admin; HttpOnly; Secure; SameSite=Strict; Max-Age=86400` } });
  }
  if (!env.ADMIN_TOKEN || token !== env.ADMIN_TOKEN) return html(loginPage(""), 401);

  if (request.method === "POST" && url.pathname === "/admin/action") {
    const form = await request.formData();
    const action = String(form.get("action")), reportId = String(form.get("reportId") || "");
    const report = reportId ? await getDoc(env, `reports/${reportId}`) : null;
    if (action === "remove" && report?.dayKey && report?.reflectionId) await hideReflection(env, report.dayKey, report.reflectionId, `moderator:${report.reason}`);
    if (action === "restore") await restoreReflection(env, String(form.get("removedId") || ""));
    if (action === "ban" && report?.targetUid) {
      await setDoc(env, `moderation/${report.targetUid}`, { banned: true, bannedAt: new Date(), reason: report.reason });
      const items = await listDocs(env, `userReflections/${report.targetUid}/items`);
      for (const it of items) if (it.dayKey) await hideReflection(env, it.dayKey, it._id, "moderator:ban");
      await fetch(`https://identitytoolkit.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/accounts:update`, {
        method: "POST", headers: { Authorization: `Bearer ${await accessToken(env)}`, "Content-Type": "application/json" },
        body: JSON.stringify({ localId: report.targetUid, disableUser: true }),
      });
    }
    if (action === "unban") {
      const uid = String(form.get("uid") || "");
      if (uid) {
        await setDoc(env, `moderation/${uid}`, { banned: false, unbannedAt: new Date() });
        await fetch(`https://identitytoolkit.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/accounts:update`, {
          method: "POST", headers: { Authorization: `Bearer ${await accessToken(env)}`, "Content-Type": "application/json" },
          body: JSON.stringify({ localId: uid, disableUser: false }),
        });
      }
    }
    if (report && ["remove", "ban", "dismiss"].includes(action)) {
      await fs(env, "PATCH", `reports/${reportId}?updateMask.fieldPaths=status&updateMask.fieldPaths=resolvedAt&updateMask.fieldPaths=resolution`, {
        fields: { status: toValue("closed"), resolvedAt: toValue(new Date()), resolution: toValue(action) },
      });
    }
    return new Response(null, { status: 303, headers: { Location: "/admin" } });
  }

  const reports = await runQuery(env, "", { from: [{ collectionId: "reports" }], orderBy: [{ field: { fieldPath: "createdAt" }, direction: "DESCENDING" }], limit: 200 });
  const removed = await runQuery(env, "", { from: [{ collectionId: "removedReflections" }], orderBy: [{ field: { fieldPath: "removedAt" }, direction: "DESCENDING" }], limit: 50 });
  return html(adminPage(reports, removed));
}

const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
function html(body, status = 200) {
  return new Response(`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex"><title>Psalmwell moderation</title>
<style>body{font-family:system-ui,sans-serif;max-width:960px;margin:24px auto;padding:0 16px;color:#1d2a2e}h1{color:#0f4c5c}.card{border:1px solid #ddd;border-radius:12px;padding:12px 16px;margin:10px 0}.open{border-left:5px solid #e0a458}.muted{color:#667}button{margin-right:6px;padding:6px 10px;border-radius:8px;border:1px solid #0f4c5c;background:#fff;cursor:pointer}button.danger{border-color:#b3261e;color:#b3261e}</style></head><body>${body}</body></html>`,
    { status, headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store", "X-Frame-Options": "DENY" } });
}
function loginPage(msg) {
  return `<h1>Psalmwell moderation</h1><form method="post" action="/admin/login"><p><input type="password" name="token" placeholder="Moderator token" required style="padding:8px;width:280px"></p><button>Sign in</button></form><p style="color:#b3261e">${esc(msg)}</p>`;
}
function adminPage(reports, removed) {
  const open = reports.filter((r) => r.status === "open"), closed = reports.filter((r) => r.status !== "open").slice(0, 30);
  const card = (r) => `<div class="card ${r.status === "open" ? "open" : ""}"><b>${esc(r.reason)}</b> · ${esc(r.type)} · <span class="muted">${esc(r.createdAt)}</span><br>
    Reported user: <code>${esc(r.targetUid)}</code>${r.snapshot ? ` (${esc(r.snapshot.authorName)})<blockquote>${esc(r.snapshot.body)}</blockquote>` : ""}
    ${r.details ? `<p class="muted">Details: ${esc(r.details)}</p>` : ""}
    ${r.status === "open" ? `<form method="post" action="/admin/action"><input type="hidden" name="reportId" value="${esc(r._id)}">
      ${r.reflectionId ? `<button name="action" value="remove">Remove reflection</button>` : ""}<button name="action" value="ban" class="danger">Ban user</button><button name="action" value="dismiss">Dismiss</button></form>` : `<span class="muted">${esc(r.resolution || r.status)}</span>`}</div>`;
  const rem = (r) => `<div class="card"><span class="muted">${esc(r.removedReason)} · ${esc(r.removedAt)}</span><blockquote>${esc(r.body)}</blockquote>${esc(r.authorName)} <code>${esc(r.authorUid)}</code>
    <form method="post" action="/admin/action" style="display:inline"><input type="hidden" name="removedId" value="${esc(r._id)}"><button name="action" value="restore">Restore</button></form>
    <form method="post" action="/admin/action" style="display:inline"><input type="hidden" name="uid" value="${esc(r.authorUid)}"><button name="action" value="unban">Unban author</button></form></div>`;
  return `<h1>Psalmwell moderation</h1><h2>Open reports (${open.length})</h2>${open.map(card).join("") || "<p>No open reports.</p>"}
    <h2>Removed reflections</h2>${removed.map(rem).join("") || "<p>None.</p>"}<h2>Recently closed</h2>${closed.map(card).join("")}`;
}
