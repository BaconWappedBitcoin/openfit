import type { VercelRequest, VercelResponse } from "@vercel/node";
import { createHash, timingSafeEqual } from "node:crypto";

export function clientIp(req: VercelRequest): string {
  const fwd = req.headers["x-forwarded-for"];
  const first = (Array.isArray(fwd) ? fwd[0] : fwd)?.split(",")[0]?.trim();
  return first || "unknown";
}

/** A stable anonymous voter id: the install id the app sends, mixed with the IP, hashed. */
export function voterHash(req: VercelRequest): string {
  const install = String(req.headers["x-install-id"] ?? "").slice(0, 64);
  return createHash("sha256").update(`${install}|${clientIp(req)}`).digest("base64url").slice(0, 22);
}

export function method(req: VercelRequest, res: VercelResponse, allowed: string[]): boolean {
  if (allowed.includes(req.method ?? "")) return true;
  res.setHeader("Allow", allowed.join(", "));
  res.status(405).json({ error: "method not allowed" });
  return false;
}

/** The parsed JSON body, or null after answering 400 when the client sent something unparseable. */
export function readBody(req: VercelRequest, res: VercelResponse): Record<string, any> | null {
  try {
    const b = req.body;
    return typeof b === "object" && b !== null ? b : {};
  } catch {
    res.status(400).json({ error: "body must be valid JSON" });
    return null;
  }
}

export function fail(res: VercelResponse, status: number, error: string) {
  res.status(status).json({ error });
}

/** Never leak internals: log the real error, return a plain message. */
export function serverError(res: VercelResponse, e: unknown) {
  console.error(e);
  const msg = e instanceof Error && e.message === "storage is not configured" ? e.message : "something went wrong";
  res.status(500).json({ error: msg });
}

export function env(name: string): string {
  const v = process.env[name];
  if (!v) throw new Error(`${name} is not set`);
  return v;
}

/**
 * A user's own Gemini API key, sent with an analysis so that it runs on their account instead of
 * the server's ("bring your own key"). Returned only when it looks like a Google API key; it is
 * never stored or logged.
 */
/**
 * The project owner's batch jobs (pre-scanning a library of videos, user 2026-11-24): a request
 * with the secret ADMIN_TOKEN skips the rate limits and the daily cap. Unset, nobody is admin.
 */
export function isAdmin(req: VercelRequest): boolean {
  const expected = process.env.ADMIN_TOKEN;
  if (!expected || expected.length < 24) return false;
  const raw = req.headers["x-admin-token"];
  const given = (Array.isArray(raw) ? raw[0] : raw) ?? "";
  if (given.length !== expected.length) return false;
  return timingSafeEqual(Buffer.from(given), Buffer.from(expected));
}

export function userGeminiKey(req: VercelRequest): string | undefined {
  const raw = req.headers["x-gemini-key"];
  const key = (Array.isArray(raw) ? raw[0] : raw)?.trim();
  return key && USER_KEY.test(key) ? key : undefined;
}

/** Google API keys: "AIza" followed by 35 URL-safe characters. */
export const USER_KEY = /^AIza[0-9A-Za-z_-]{35}$/;
