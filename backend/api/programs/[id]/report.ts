import type { VercelRequest, VercelResponse } from "@vercel/node";
import { allow, getProgram, hideProgram, report } from "../../../lib/store.js";
import { clientIp, env, fail, method, readBody, serverError, voterHash } from "../../../lib/http.js";
import { REPORT_REASONS, REPORTS_TO_HIDE, videoProblem, videoSafety } from "../../../lib/moderation.js";

// POST /api/programs/:id/report  { reason }  -> { hidden: boolean }
// A report re-checks the video at once; otherwise REPORTS_TO_HIDE different people hide it.
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!method(req, res, ["POST"])) return;
  const id = String(req.query.id ?? "");
  const body = readBody(req, res);
  if (!body) return;
  if (!/^[A-Za-z0-9_-]{4,16}$/.test(id)) return fail(res, 400, "bad id");
  const reason = String(body.reason ?? "other");
  if (!(REPORT_REASONS as readonly string[]).includes(reason)) return fail(res, 400, "unknown reason");
  try {
    if (!(await allow("report", clientIp(req), 20, 3600))) return fail(res, 429, "too many reports, try again later");
    const p = await getProgram(id);
    if (!p) return fail(res, 404, "not found");
    if (p.videoId) {
      // YouTube being unreachable is not a verdict: then only the report count decides.
      let problem: string | null = null;
      try {
        problem = videoProblem(await videoSafety(p.videoId, env("YOUTUBE_API_KEY")));
      } catch {
        problem = null;
      }
      if (problem) {
        await hideProgram(id, `video: ${problem}`);
        return res.status(200).json({ hidden: true });
      }
    }
    const count = await report(id, voterHash(req), reason);
    if (count === null) return fail(res, 404, "not found");
    if (count >= REPORTS_TO_HIDE) {
      await hideProgram(id, `reported by ${count} people`);
      return res.status(200).json({ hidden: true });
    }
    res.status(200).json({ hidden: false });
  } catch (e) {
    serverError(res, e);
  }
}
