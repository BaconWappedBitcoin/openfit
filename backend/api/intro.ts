import type { VercelRequest, VercelResponse } from "@vercel/node";
import { cachedAnalysis, cacheAnalysis, db, listPrograms } from "../lib/store.js";
import { env, fail, isAdmin, method, serverError } from "../lib/http.js";
import { analyseWindow } from "../lib/gemini.js";
import { cleanBounds, totalSeconds } from "../lib/program.js";

// POST /api/intro?v=<YouTube video id>  (owner only, x-admin-token)
// Reads just the first minute of a library video (two 30 s windows) for an intro, so workouts made
// before the scan knew about intros get a suggested start without paying for the whole video again
// (user request 2026-11-24). The cached readings and every library workout for the video are updated.
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!method(req, res, ["POST"])) return;
  if (!isAdmin(req)) return fail(res, 403, "owner only");
  const videoId = String(req.query.v ?? "").trim();
  if (!/^[A-Za-z0-9_-]{11}$/.test(videoId)) return fail(res, 400, "v must be a YouTube video id");
  try {
    const key = env("GEMINI_API_KEY");
    const model = process.env.GEMINI_MODEL ?? "gemini-3.5-flash";
    const url = `https://www.youtube.com/watch?v=${videoId}`;
    const first = await Promise.all([analyseWindow(key, model, url, 0, 0, 30), analyseWindow(key, model, url, 1, 30, 60)]);
    const parts = first.map((r) => r.part ?? "running");
    // The intro runs on from the start for as long as the windows say intro.
    const introEnd = parts[0] !== "intro" ? 0 : parts[1] === "intro" ? 60 : 30;

    // The cached readings learn which part each of the first windows is.
    const readings = await cachedAnalysis(videoId);
    if (readings) {
      const next = readings.map((r) => (r.index < 2 ? { ...r, part: parts[r.index] as "intro" | "running" | "outro" } : r));
      await cacheAnalysis(videoId, next);
    }

    // Every library workout for this video gets the suggested start (or loses a stale one).
    const updated: string[] = [];
    const all = await listPrograms("new", 0, 500);
    for (const p of all.filter((x) => x.videoId === videoId)) {
      const total = totalSeconds(p.program);
      const bounds = cleanBounds({ introEndSeconds: introEnd || undefined, outroStartSeconds: p.program.outroStartSeconds }, total);
      const program = { ...p.program };
      delete program.introEndSeconds;
      if (bounds.introEndSeconds !== undefined) program.introEndSeconds = bounds.introEndSeconds;
      await db().set(`program:${p.id}`, { ...p, program });
      updated.push(p.id);
    }
    res.status(200).json({ videoId, parts, introEndSeconds: introEnd || null, updated, notes: first.map((r) => r.note) });
  } catch (e) {
    serverError(res, e);
  }
}
