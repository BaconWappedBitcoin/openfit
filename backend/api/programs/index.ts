import type { VercelRequest, VercelResponse } from "@vercel/node";
import { allow, listPrograms, newId, ratingScore, savePublished, type StoredProgram } from "../../lib/store.js";
import { clientIp, env, fail, isAdmin, method, readBody, serverError, userGeminiKey } from "../../lib/http.js";
import { aiProblem, textProblem, videoProblem, videoSafety } from "../../lib/moderation.js";
import { effort, ProgramError, sanitize, totalSeconds } from "../../lib/program.js";
import { VIDEO_ID } from "../../lib/youtube.js";

export function summary(p: StoredProgram) {
  return {
    id: p.id,
    name: p.program.name,
    videoId: p.videoId,
    steps: p.program.steps.length,
    durationSeconds: totalSeconds(p.program),
    ...effort(p.program),
    // The real average of the stars given (user report 2026-11-24: one 1-star vote showed 3.1);
    // "score", smoothed towards 3.5 so one vote can't top the list, is for ranking only.
    rating: p.ratingCount > 0 ? Math.round((p.ratingSum / p.ratingCount) * 10) / 10 : 0,
    score: Math.round(ratingScore(p.ratingSum, p.ratingCount) * 100) / 100,
    ratings: p.ratingCount,
    origin: p.origin,
    createdAt: p.createdAt,
  };
}

// GET  /api/programs?sort=new|top&offset=0&limit=20   -> { programs: [...] }
// POST /api/programs  { program, videoId?, note?, origin? }  -> { id }
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!method(req, res, ["GET", "POST"])) return;
  try {
    if (req.method === "GET") {
      const sort = req.query.sort === "top" ? "top" : "new";
      const offset = Math.max(0, Number(req.query.offset) || 0);
      const limit = Math.min(50, Math.max(1, Number(req.query.limit) || 20));
      const programs = await listPrograms(sort, offset, limit);
      // Never cached: a workout someone has just shared must show up on their next refresh.
      res.setHeader("Cache-Control", "no-store");
      return res.status(200).json({ programs: programs.map(summary) });
    }

    if (!isAdmin(req) && !(await allow("publish", clientIp(req), 10, 3600))) return fail(res, 429, "too many uploads, try again later");
    const body = readBody(req, res);
    if (!body) return;
    const program = sanitize(body.program);
    const videoId = body.videoId == null ? null : String(body.videoId);
    if (videoId !== null && !VIDEO_ID.test(videoId)) return fail(res, 400, "videoId must be a YouTube id");
    const note = String(body.note ?? "").replace(/[\u0000-\u001f\u007f]/g, " ").slice(0, 500);
    // Automatic moderation (lib/moderation.ts): nothing waits for a person to approve it.
    const wordsProblem = textProblem(program.name) ?? textProblem(note);
    if (wordsProblem) return fail(res, 422, `Not shared: ${wordsProblem}.`);
    if (videoId !== null) {
      const safety = await videoSafety(videoId, env("YOUTUBE_API_KEY"));
      const problem = videoProblem(safety);
      if (problem) return fail(res, 422, `Not shared: ${problem}.`);
      const ownKey = userGeminiKey(req);
      if (ownKey && safety) {
        const ai = await aiProblem(safety, ownKey, process.env.GEMINI_MODEL ?? "gemini-3.5-flash");
        if (ai) return fail(res, 422, `Not shared: ${ai}.`);
      }
    }
    const stored: StoredProgram = {
      id: newId(),
      program,
      videoId,
      note,
      createdAt: Date.now(),
      ratingSum: 0,
      ratingCount: 0,
      origin: body.origin === "analysis" ? "analysis" : "manual",
    };
    await savePublished(stored);
    res.status(201).json({ id: stored.id });
  } catch (e) {
    if (e instanceof ProgramError) return fail(res, 400, e.message);
    serverError(res, e);
  }
}
