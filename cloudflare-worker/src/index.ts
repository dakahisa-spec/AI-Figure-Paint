interface Env {
  DB: D1Database;
  AI_API_KEY: string;
  ADMIN_SECRET: string;
  LICENSE_HASH_SALT: string;
  OPENAI_RESPONSES_URL: string;
}

type LicenseRow = {
  id: string;
  max_devices: number;
  monthly_budget_krw: number;
  enabled: number;
};

type UsageRow = {
  used_krw: number;
  reserved_krw: number;
  input_tokens: number;
  output_tokens: number;
  image_requests: number;
};

const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), {
  status,
  headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
});

const error = (code: string, message: string, status: number) => json({ code, message }, status);

const nowIso = () => new Date().toISOString();

function koreaClock() {
  const shifted = new Date(Date.now() + 9 * 60 * 60 * 1000);
  const year = shifted.getUTCFullYear();
  const month = shifted.getUTCMonth() + 1;
  const nextMonth = month === 12 ? 1 : month + 1;
  const nextYear = month === 12 ? year + 1 : year;
  return {
    month: `${year}-${String(month).padStart(2, "0")}`,
    nextReset: `${nextYear}-${String(nextMonth).padStart(2, "0")}-01`,
  };
}

async function sha256(value: string): Promise<string> {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(bytes)].map((v) => v.toString(16).padStart(2, "0")).join("");
}

function base64Bytes(value: string): Uint8Array {
  const binary = atob(value);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

function derEcdsaToRaw(der: Uint8Array, size = 32): Uint8Array {
  let cursor = 0;
  if (der[cursor++] !== 0x30) throw new Error("invalid signature");
  const seqLength = der[cursor++] & 0x7f;
  if (seqLength >= 0x80) cursor += seqLength & 0x7f;
  if (der[cursor++] !== 0x02) throw new Error("invalid signature");
  const rLength = der[cursor++];
  const r = der.slice(cursor, cursor + rLength);
  cursor += rLength;
  if (der[cursor++] !== 0x02) throw new Error("invalid signature");
  const sLength = der[cursor++];
  const s = der.slice(cursor, cursor + sLength);
  const raw = new Uint8Array(size * 2);
  raw.set(r.slice(Math.max(0, r.length - size)), size - Math.min(size, r.length));
  raw.set(s.slice(Math.max(0, s.length - size)), size * 2 - Math.min(size, s.length));
  return raw;
}

async function verifySignature(publicKeyBase64: string, payload: string, signatureBase64: string): Promise<boolean> {
  const key = await crypto.subtle.importKey(
    "spki",
    base64Bytes(publicKeyBase64),
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["verify"],
  );
  const der = base64Bytes(signatureBase64);
  return crypto.subtle.verify(
    { name: "ECDSA", hash: "SHA-256" },
    key,
    derEcdsaToRaw(der),
    new TextEncoder().encode(payload),
  );
}

async function bodyObject(request: Request): Promise<{ value: Record<string, unknown>; text: string }> {
  const text = await request.text();
  if (text.length > 24_000_000) throw new Error("요청 이미지 용량이 너무 큽니다.");
  return { value: JSON.parse(text || "{}") as Record<string, unknown>, text: text || "{}" };
}

async function licenseById(env: Env, id: string): Promise<LicenseRow | null> {
  return env.DB.prepare("SELECT id, max_devices, monthly_budget_krw, enabled FROM licenses WHERE id = ?")
    .bind(id).first<LicenseRow>();
}

async function usageResponse(env: Env, license: LicenseRow) {
  const clock = koreaClock();
  const usage = await env.DB.prepare(
    "SELECT used_krw, reserved_krw, input_tokens, output_tokens, image_requests FROM monthly_usage WHERE license_id = ? AND year_month = ?",
  ).bind(license.id, clock.month).first<UsageRow>();
  const deviceCount = await env.DB.prepare(
    "SELECT COUNT(*) AS count FROM devices WHERE license_id = ? AND revoked_at IS NULL",
  ).bind(license.id).first<{ count: number }>();
  const used = usage?.used_krw ?? 0;
  return {
    license_id: license.id,
    device_count: deviceCount?.count ?? 0,
    max_devices: license.max_devices,
    used_krw: used,
    reserved_krw: usage?.reserved_krw ?? 0,
    remaining_krw: Math.max(0, license.monthly_budget_krw - used - (usage?.reserved_krw ?? 0)),
    monthly_budget_krw: license.monthly_budget_krw,
    input_tokens: usage?.input_tokens ?? 0,
    output_tokens: usage?.output_tokens ?? 0,
    image_requests: usage?.image_requests ?? 0,
    month: clock.month,
    next_reset: clock.nextReset,
  };
}

async function activate(request: Request, env: Env): Promise<Response> {
  const { value } = await bodyObject(request);
  const code = String(value.activation_code ?? "").trim();
  const deviceHash = String(value.device_hash ?? "");
  const publicKey = String(value.public_key ?? "");
  const appVersion = String(value.app_version ?? "").slice(0, 40);
  if (!code || !/^[a-f0-9]{64}$/i.test(deviceHash) || !publicKey) {
    return error("INVALID_ACTIVATION", "활성화 정보를 확인해주세요.", 400);
  }
  const codeHash = await sha256(`${env.LICENSE_HASH_SALT}:${code}`);
  const license = await env.DB.prepare(
    "SELECT id, max_devices, monthly_budget_krw, enabled FROM licenses WHERE activation_code_hash = ?",
  ).bind(codeHash).first<LicenseRow>();
  if (!license || license.enabled !== 1) return error("INVALID_ACTIVATION", "활성화 코드를 확인해주세요.", 403);
  const existing = await env.DB.prepare(
    "SELECT revoked_at FROM devices WHERE license_id = ? AND device_hash = ?",
  ).bind(license.id, deviceHash).first<{ revoked_at: string | null }>();
  try {
    if (existing) {
      if (existing.revoked_at) return error("DEVICE_REVOKED", "이 기기의 등록이 해제되었습니다.", 403);
      await env.DB.prepare(
        "UPDATE devices SET public_key = ?, app_version = ?, last_used_at = ? WHERE license_id = ? AND device_hash = ?",
      ).bind(publicKey, appVersion, nowIso(), license.id, deviceHash).run();
    } else {
      await env.DB.prepare(
        "INSERT INTO devices(license_id, device_hash, public_key, app_version, first_activated_at, last_used_at) VALUES(?, ?, ?, ?, ?, ?)",
      ).bind(license.id, deviceHash, publicKey, appVersion, nowIso(), nowIso()).run();
    }
  } catch (cause) {
    if (String(cause).includes("DEVICE_LIMIT")) return error("DEVICE_LIMIT", "사용 가능한 기기 3대를 모두 등록했습니다.", 409);
    throw cause;
  }
  return json({ activated: true, ...(await usageResponse(env, license)) });
}

async function issueNonce(request: Request, env: Env): Promise<Response> {
  const { value } = await bodyObject(request);
  const licenseId = String(value.license_id ?? "");
  const deviceHash = String(value.device_hash ?? "");
  const device = await env.DB.prepare(
    "SELECT 1 AS ok FROM devices JOIN licenses ON licenses.id = devices.license_id WHERE devices.license_id = ? AND device_hash = ? AND revoked_at IS NULL AND licenses.enabled = 1",
  ).bind(licenseId, deviceHash).first<{ ok: number }>();
  if (!device) return error("DEVICE_REVOKED", "활성화된 기기를 확인할 수 없습니다.", 403);
  const nonce = crypto.randomUUID();
  const expires = Date.now() + 5 * 60 * 1000;
  await env.DB.batch([
    env.DB.prepare("DELETE FROM nonces WHERE expires_at < ?").bind(Date.now()),
    env.DB.prepare("INSERT INTO nonces(nonce, license_id, device_hash, expires_at, created_at) VALUES(?, ?, ?, ?, ?)")
      .bind(nonce, licenseId, deviceHash, expires, nowIso()),
  ]);
  return json({ nonce, expires_at: expires });
}

async function authenticate(request: Request, env: Env, bodyText: string) {
  const licenseId = request.headers.get("X-License-Id") ?? "";
  const deviceHash = request.headers.get("X-Device-Hash") ?? "";
  const requestId = request.headers.get("X-Request-Id") ?? "";
  const timestamp = request.headers.get("X-Timestamp") ?? "";
  const nonce = request.headers.get("X-Nonce") ?? "";
  const signature = request.headers.get("X-Signature") ?? "";
  if (!licenseId || !deviceHash || !requestId || !timestamp || !nonce || !signature) throw new Error("AUTH_REQUIRED");
  const timestampMs = Number(timestamp);
  if (!Number.isFinite(timestampMs) || Math.abs(Date.now() - timestampMs) > 5 * 60 * 1000) throw new Error("AUTH_EXPIRED");
  const nonceDelete = await env.DB.prepare(
    "DELETE FROM nonces WHERE nonce = ? AND license_id = ? AND device_hash = ? AND expires_at >= ?",
  ).bind(nonce, licenseId, deviceHash, Date.now()).run();
  if ((nonceDelete.meta.changes ?? 0) !== 1) throw new Error("AUTH_NONCE");
  const device = await env.DB.prepare(
    "SELECT public_key FROM devices JOIN licenses ON licenses.id = devices.license_id WHERE devices.license_id = ? AND device_hash = ? AND revoked_at IS NULL AND licenses.enabled = 1",
  ).bind(licenseId, deviceHash).first<{ public_key: string }>();
  if (!device) throw new Error("DEVICE_REVOKED");
  const payload = [licenseId, deviceHash, requestId, timestamp, nonce, await sha256(bodyText)].join("\n");
  if (!await verifySignature(device.public_key, payload, signature)) throw new Error("AUTH_SIGNATURE");
  await env.DB.prepare("UPDATE devices SET last_used_at = ? WHERE license_id = ? AND device_hash = ?")
    .bind(nowIso(), licenseId, deviceHash).run();
  return { licenseId, deviceHash, requestId };
}

const rates: Record<string, { input: number; output: number }> = {
  "gpt-5.6-luna": { input: 1500, output: 6000 },
  "gpt-5.6-terra": { input: 5000, output: 20000 },
  "gpt-5.6-sol": { input: 20000, output: 80000 },
};

function pricing(model: string) {
  return rates[model] ?? rates["gpt-5.6-sol"];
}

function estimateCost(body: Record<string, unknown>, bodyText: string) {
  const model = String(body.model ?? "gpt-5.6-sol");
  const imageCount = Math.min(5, (bodyText.match(/"input_image"/g) ?? []).length);
  const inputTokens = Math.ceil(bodyText.length / 3) + imageCount * 3000;
  const maxOutput = Math.min(8000, Math.max(800, Number(body.max_output_tokens ?? 4000)));
  const rate = pricing(model);
  const raw = inputTokens * rate.input / 1_000_000 + maxOutput * rate.output / 1_000_000;
  return { model, imageCount, reserved: Math.max(1, Math.ceil(raw * 1.25)) };
}

function actualCost(model: string, inputTokens: number, outputTokens: number) {
  const rate = pricing(model);
  return Math.max(1, Math.ceil((inputTokens * rate.input + outputTokens * rate.output) / 1_000_000));
}

async function reserve(env: Env, license: LicenseRow, requestId: string, deviceHash: string, model: string, amount: number) {
  const clock = koreaClock();
  const safeLimit = Math.max(1, license.monthly_budget_krw - 300);
  await env.DB.prepare(
    "INSERT OR IGNORE INTO monthly_usage(license_id, year_month, updated_at) VALUES(?, ?, ?)",
  ).bind(license.id, clock.month, nowIso()).run();
  const reservation = await env.DB.prepare(
    "UPDATE monthly_usage SET reserved_krw = reserved_krw + ?, updated_at = ? WHERE license_id = ? AND year_month = ? AND used_krw + reserved_krw + ? <= ?",
  ).bind(amount, nowIso(), license.id, clock.month, amount, safeLimit).run();
  if ((reservation.meta.changes ?? 0) !== 1) return false;
  try {
    await env.DB.prepare(
      "INSERT INTO api_requests(request_id, license_id, device_hash, model, reserved_cost_krw, status, created_at) VALUES(?, ?, ?, ?, ?, 'RESERVED', ?)",
    ).bind(requestId, license.id, deviceHash, model, amount, nowIso()).run();
    return true;
  } catch (cause) {
    await env.DB.prepare(
      "UPDATE monthly_usage SET reserved_krw = MAX(0, reserved_krw - ?) WHERE license_id = ? AND year_month = ?",
    ).bind(amount, license.id, clock.month).run();
    throw cause;
  }
}

async function release(env: Env, licenseId: string, requestId: string, amount: number, status: string) {
  const clock = koreaClock();
  await env.DB.batch([
    env.DB.prepare("UPDATE monthly_usage SET reserved_krw = MAX(0, reserved_krw - ?), updated_at = ? WHERE license_id = ? AND year_month = ?")
      .bind(amount, nowIso(), licenseId, clock.month),
    env.DB.prepare("UPDATE api_requests SET status = ?, completed_at = ? WHERE request_id = ?")
      .bind(status, nowIso(), requestId),
  ]);
}

async function proxyAi(request: Request, env: Env): Promise<Response> {
  const { value: body, text: bodyText } = await bodyObject(request);
  const imageCount = (bodyText.match(/"input_image"/g) ?? []).length;
  if (imageCount > 5) return error("TOO_MANY_IMAGES", "사진은 최대 5장까지 사용할 수 있습니다.", 400);
  let auth;
  try {
    auth = await authenticate(request, env, bodyText);
  } catch (cause) {
    return error(String(cause).includes("DEVICE_REVOKED") ? "DEVICE_REVOKED" : "AUTH_FAILED", "기기 인증을 확인할 수 없습니다.", 403);
  }
  const license = await licenseById(env, auth.licenseId);
  if (!license || license.enabled !== 1) return error("INVALID_ACTIVATION", "비활성화된 선물용 라이선스입니다.", 403);
  const estimate = estimateCost(body, bodyText);
  let reserved = false;
  try {
    reserved = await reserve(env, license, auth.requestId, auth.deviceHash, estimate.model, estimate.reserved);
  } catch (cause) {
    if (String(cause).includes("UNIQUE")) return error("DUPLICATE_REQUEST", "이미 처리한 요청입니다.", 409);
    throw cause;
  }
  if (!reserved) return error("BUDGET_LIMIT", "이번 달 AI 사용 한도에 도달했습니다.", 402);
  let upstream: Response;
  try {
    upstream = await fetch(env.OPENAI_RESPONSES_URL, {
      method: "POST",
      headers: { "authorization": `Bearer ${env.AI_API_KEY}`, "content-type": "application/json" },
      body: bodyText,
    });
  } catch (cause) {
    await release(env, license.id, auth.requestId, estimate.reserved, "NETWORK_FAILED");
    return error("UPSTREAM_FAILED", "AI 서비스에 연결할 수 없습니다.", 502);
  }
  const responseText = await upstream.text();
  if (!upstream.ok) {
    await release(env, license.id, auth.requestId, estimate.reserved, `UPSTREAM_${upstream.status}`);
    return new Response(responseText, { status: upstream.status, headers: { "content-type": "application/json", "cache-control": "no-store" } });
  }
  const responseJson = JSON.parse(responseText) as { usage?: { input_tokens?: number; output_tokens?: number } };
  const inputTokens = Math.max(0, responseJson.usage?.input_tokens ?? 0);
  const outputTokens = Math.max(0, responseJson.usage?.output_tokens ?? 0);
  const charged = actualCost(estimate.model, inputTokens, outputTokens);
  const clock = koreaClock();
  await env.DB.batch([
    env.DB.prepare(
      "UPDATE monthly_usage SET reserved_krw = MAX(0, reserved_krw - ?), used_krw = used_krw + ?, input_tokens = input_tokens + ?, output_tokens = output_tokens + ?, image_requests = image_requests + ?, updated_at = ? WHERE license_id = ? AND year_month = ?",
    ).bind(estimate.reserved, charged, inputTokens, outputTokens, estimate.imageCount, nowIso(), license.id, clock.month),
    env.DB.prepare("UPDATE api_requests SET actual_cost_krw = ?, status = 'COMPLETED', completed_at = ? WHERE request_id = ?")
      .bind(charged, nowIso(), auth.requestId),
  ]);
  return new Response(responseText, { status: 200, headers: { "content-type": "application/json", "cache-control": "no-store" } });
}

async function signedUsage(request: Request, env: Env): Promise<Response> {
  const { text } = await bodyObject(request);
  let auth;
  try {
    auth = await authenticate(request, env, text);
  } catch {
    return error("AUTH_FAILED", "기기 인증을 확인할 수 없습니다.", 403);
  }
  const license = await licenseById(env, auth.licenseId);
  if (!license || license.enabled !== 1) return error("INVALID_ACTIVATION", "비활성화된 선물용 라이선스입니다.", 403);
  return json(await usageResponse(env, license));
}

async function adminCreateLicense(request: Request, env: Env): Promise<Response> {
  if (request.headers.get("authorization") !== `Bearer ${env.ADMIN_SECRET}`) return error("ADMIN_REQUIRED", "관리자 인증이 필요합니다.", 403);
  const { value } = await bodyObject(request);
  const activationCode = String(value.activation_code ?? "").trim() || `GIFT-${crypto.randomUUID().replaceAll("-", "").slice(0, 20).toUpperCase()}`;
  const id = crypto.randomUUID();
  const codeHash = await sha256(`${env.LICENSE_HASH_SALT}:${activationCode}`);
  await env.DB.prepare(
    "INSERT INTO licenses(id, activation_code_hash, max_devices, monthly_budget_krw, enabled, created_at) VALUES(?, ?, 3, 3000, 1, ?)",
  ).bind(id, codeHash, nowIso()).run();
  return json({ license_id: id, activation_code: activationCode, max_devices: 3, monthly_budget_krw: 3000 }, 201);
}

async function adminRevokeDevice(request: Request, env: Env): Promise<Response> {
  if (request.headers.get("authorization") !== `Bearer ${env.ADMIN_SECRET}`) return error("ADMIN_REQUIRED", "관리자 인증이 필요합니다.", 403);
  const { value } = await bodyObject(request);
  const result = await env.DB.prepare(
    "UPDATE devices SET revoked_at = ? WHERE license_id = ? AND device_hash = ? AND revoked_at IS NULL",
  ).bind(nowIso(), String(value.license_id ?? ""), String(value.device_hash ?? "")).run();
  return json({ revoked: (result.meta.changes ?? 0) === 1 });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method !== "POST") return error("METHOD_NOT_ALLOWED", "POST 요청만 지원합니다.", 405);
    const path = new URL(request.url).pathname;
    try {
      if (path === "/v1/activate") return await activate(request, env);
      if (path === "/v1/nonce") return await issueNonce(request, env);
      if (path === "/v1/usage") return await signedUsage(request, env);
      if (path === "/v1/ai/responses") return await proxyAi(request, env);
      if (path === "/v1/admin/licenses") return await adminCreateLicense(request, env);
      if (path === "/v1/admin/revoke-device") return await adminRevokeDevice(request, env);
      return error("NOT_FOUND", "지원하지 않는 경로입니다.", 404);
    } catch (cause) {
      return error("INTERNAL_ERROR", cause instanceof Error ? cause.message.slice(0, 160) : "처리 중 오류가 발생했습니다.", 500);
    }
  },
};
