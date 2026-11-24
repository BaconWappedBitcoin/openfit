import type { VercelRequest, VercelResponse } from "@vercel/node";
import { allow, cacheAnalysis, cachedAnalysis } from "../lib/store.js";
import { clientIp, env, fail, method, readBody, serverError } from "../lib/http.js";
import { VIDEO_ID, videoDetails } from "../lib/youtube.js";
import { analyseVideo } from "../lib/gemini.js";
import { buildProgram } from "../lib/build.js";
import { sanitize, tidyTitle, totalSeconds } from "../lib/program.js";

const MIN_SECONDS = 120;
const MAX_SECONDS = 3600;
const DAILY_CAP = 200; // analyses across everyone, a ceiling on what Gemini can cost

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

  try {
    const [info] = await videoDetails([videoId], env("YOUTUBE_API_KEY"));
    if (!info) return fail(res, 404, "video not found, or it is private");
    if (!info.embeddable) return fail(res, 422, "this video cannot be played in other apps");
    if (info.durationSeconds < MIN_SECONDS) return fail(res, 422, "video is too short to make a workout");
    if (info.durationSeconds > MAX_SECONDS) return fail(res, 422, "video is longer than 60 minutes");

    let readings = await cachedAnalysis(videoId);
    if (!readings) {
      // Only an uncached analysis costs money, so only that is limited.
      if (!(await allow("analyze", clientIp(req), 5, 3600))) return fail(res, 429, "too many analyses, try again later");
      if (!(await allow("analyze-day", "all", DAILY_CAP, 86400))) return fail(res, 429, "the daily analysis limit is used up");
      readings = await analyseVideo(
        env("GEMINI_API_KEY"),
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
      note: "Speed and incline are estimates by a video model, not measurements.",
    });
  } catch (e) {
    serverError(res, e);
  }
}
