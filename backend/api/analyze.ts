import type { VercelRequest, VercelResponse } from "@vercel/node";
import { allow, cacheAnalysis, cachedAnalysis } from "../lib/store.js";
import { clientIp, env, fail, isAdmin, method, readBody, serverError, userGeminiKey } from "../lib/http.js";
import { VIDEO_ID, videoDetails } from "../lib/youtube.js";
import { analyseVideo } from "../lib/gemini.js";
import { buildProgram } from "../lib/build.js";
import { sanitize, tidyTitle, totalSeconds } from "../lib/program.js";

const MIN_SECONDS = 120;
const MAX_SECONDS = 3600;

// POST { videoId }  ->  { program, videoId, durationSeconds, confidence, note }
//
// Gemini reads the public video from its address. Nothing is downloaded. The result is an
// estimate from a model looking at pictures, and the response says so with a confidence figure.
// The programme is not published: the app shows it, the user decides.
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (!method(req, res, ["POST"])) return;
  const body = readBody(req, res);
  if (!body) return;
  const videoId = String(body.videoId ?? "");
  if (!VIDEO_ID.test(videoId)) return fail(res, 400, "videoId must be an 11 character YouTube id");

  // Bring your own key: the user's Gemini account pays, so the server's daily cap does not apply.
  // A header that is present but malformed is refused before anything else is called.
  const ownKey = userGeminiKey(req);
  if (req.headers["x-gemini-key"] && !ownKey) return fail(res, 400, "that does not look like a Gemini API key");

  try {
    const [info] = await videoDetails([videoId], env("YOUTUBE_API_KEY"));
    if (!info) return fail(res, 404, "video not found, or it is private");
    if (!info.embeddable) return fail(res, 422, "this video cannot be played in other apps");
    if (info.durationSeconds < MIN_SECONDS) return fail(res, 422, "video is too short to make a workout");
    if (info.durationSeconds > MAX_SECONDS) return fail(res, 422, "video is longer than 60 minutes");

    let readings = await cachedAnalysis(videoId);
    if (!readings) {
      // A new video is read only on the runner's own Gemini key (user decision 2026-11-24: no AI
      // on the project's key for the public, and no paid plan for now). Videos already read are
      // free for everyone. The owner's batch jobs (admin) use the server's key.
      const admin = isAdmin(req);
      if (!admin && !ownKey) {
        return fail(res, 403, "Making a workout from a new video needs your own free Gemini key: add it in OpenFit under Keys & services. Videos others have already made are free.");
      }
      if (!admin && !(await allow("analyze", clientIp(req), 30, 3600))) return fail(res, 429, "too many analyses, try again later");
      readings = await analyseVideo(
        ownKey ?? env("GEMINI_API_KEY"),
        process.env.GEMINI_MODEL ?? "gemini-3.5-flash",
        `https://www.youtube.com/watch?v=${videoId}`,
        info.durationSeconds,
      );
      await cacheAnalysis(videoId, readings);
    }

    const title = tidyTitle(info.title);
    const program = sanitize(buildProgram(readings, title));
    const confidence = readings.reduce((n, r) => n + r.confidence, 0) / readings.length;
    res.status(200).json({
      program,
      videoId,
      durationSeconds: info.durationSeconds,
      programSeconds: totalSeconds(program),
      confidence: Math.round(confidence * 100) / 100,
      // The model's readings per window, so a copy of the community data (community/cache/analysis)
      // lets the app rebuild the workout without a server.
      readings,
      note: "Speed and incline are estimates by a video model, not measurements.",
    });
  } catch (e) {
    serverError(res, e);
  }
}
