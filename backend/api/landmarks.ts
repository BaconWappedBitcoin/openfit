import type { VercelRequest, VercelResponse } from "@vercel/node";
import { allow, getCache, setCache } from "../lib/store.js";
import { clientIp, env, fail, isAdmin, method, serverError, userGeminiKey } from "../lib/http.js";
import { videoLandmarks } from "../lib/gemini.js";
import { videoDetails } from "../lib/youtube.js";

// GET /api/landmarks?v=<YouTube video id> -> { landmarks: [{ seconds, name, fact }] } for the app's
// virtual coach to mention as the runner passes them. One low-detail read of the video, cached.
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!method(req, res, ["GET"])) return;
  const videoId = String(req.query.v ?? "").trim();
  if (!/^[A-Za-z0-9_-]{11}$/.test(videoId)) return fail(res, 400, "v must be a YouTube video id");
  try {
    const cached = await getCache<unknown>("landmarks", videoId);
    if (cached) return res.status(200).json(cached);
    // Not made yet: only on the runner's own key (or the owner's batch job); otherwise none.
    const ownKey = userGeminiKey(req);
    const admin = isAdmin(req);
    if (!ownKey && !admin) return res.status(200).json({ landmarks: [] });
    if (!admin && !(await allow("landmarks", clientIp(req), 6, 3600))) return fail(res, 429, "too many requests, try again later");
    const [info] = await videoDetails([videoId], env("YOUTUBE_API_KEY"));
    if (!info) return fail(res, 404, "video not found");
    if (info.durationSeconds > 2 * 3600) return fail(res, 422, "video is too long");
    const landmarks = await videoLandmarks(
      ownKey ?? env("GEMINI_API_KEY"),
      process.env.GEMINI_MODEL ?? "gemini-3.5-flash",
      `https://www.youtube.com/watch?v=${videoId}`,
      info.title,
    );
    const answer = { landmarks };
    await setCache("landmarks", videoId, answer, 90 * 24 * 3600);
    res.setHeader("Cache-Control", "public, s-maxage=86400");
    res.status(200).json(answer);
  } catch (e) {
    serverError(res, e);
  }
}
