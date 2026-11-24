import type { VercelRequest, VercelResponse } from "@vercel/node";
import { allow, getCache, setCache } from "../lib/store.js";
import { clientIp, env, fail, isAdmin, method, serverError, userGeminiKey } from "../lib/http.js";
import { placeFacts } from "../lib/gemini.js";
import { videoDetails } from "../lib/youtube.js";

// GET /api/facts?v=<YouTube video id> -> { place, facts[] } about the place the video was filmed,
// for the app's virtual coach. Cached per video, so each video costs one model call at most.
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!method(req, res, ["GET"])) return;
  const videoId = String(req.query.v ?? "").trim();
  if (!/^[A-Za-z0-9_-]{11}$/.test(videoId)) return fail(res, 400, "v must be a YouTube video id");
  try {
    const cached = await getCache<unknown>("facts20", videoId);
    if (cached) return res.status(200).json(cached);
    // Not made yet: only on the runner's own key (or the owner's batch job); otherwise none, and
    // the coach simply has no facts for this video.
    const ownKey = userGeminiKey(req);
    const admin = isAdmin(req);
    if (!ownKey && !admin) return res.status(200).json({ place: null, facts: [] });
    if (!admin && !(await allow("facts", clientIp(req), 20, 3600))) return fail(res, 429, "too many requests, try again later");
    const [info] = await videoDetails([videoId], env("YOUTUBE_API_KEY"));
    if (!info) return fail(res, 404, "video not found");
    const answer = await placeFacts(
      ownKey ?? env("GEMINI_API_KEY"),
      process.env.GEMINI_MODEL ?? "gemini-3.5-flash",
      info.title,
      info.channel ?? "",
    );
    await setCache("facts20", videoId, answer, 30 * 24 * 3600);
    res.setHeader("Cache-Control", "public, s-maxage=86400");
    res.status(200).json(answer);
  } catch (e) {
    serverError(res, e);
  }
}
