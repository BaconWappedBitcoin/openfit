import type { VercelRequest, VercelResponse } from "@vercel/node";
import { allow, getCache, setCache } from "../lib/store.js";
import { clientIp, env, fail, method, serverError } from "../lib/http.js";
import { search } from "../lib/youtube.js";

// GET /api/search?q=...  -> embeddable YouTube videos, with their length.
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!method(req, res, ["GET"])) return;
  const q = String(req.query.q ?? "").trim().slice(0, 100);
  if (q.length < 2) return fail(res, 400, "q must be at least 2 characters");
  try {
    // YouTube's search costs 100 quota units a call, so this is limited more tightly than the rest.
    if (!(await allow("search", clientIp(req), 30, 600))) return fail(res, 429, "too many searches, try again later");
    const cacheKey = q.toLowerCase();
    const cached = await getCache<unknown>("search", cacheKey);
    if (cached) return res.status(200).json({ videos: cached });
    const videos = await search(`${q} running`, env("YOUTUBE_API_KEY"));
    await setCache("search", cacheKey, videos, 6 * 3600);
    res.setHeader("Cache-Control", "public, s-maxage=3600");
    res.status(200).json({ videos });
  } catch (e) {
    serverError(res, e);
  }
}
