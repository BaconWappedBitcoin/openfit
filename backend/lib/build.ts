// A port of re/tools/video-to-program/build_program.py: the model's per-window answers become a
// programme. Same three things are done to the numbers: outliers replaced, a running mean, the
// machine's limits, then neighbouring windows that agree are merged into one step.

import { LIMITS, type Program, type Step } from "./program.js";

export interface WindowReading {
  index: number;
  startSeconds: number;
  durationSeconds: number;
  speedKph: number | null;
  inclinePercent: number | null;
  confidence: number;
  terrain: string;
  note: string;
}

const median = (xs: number[]) => {
  const s = [...xs].sort((a, b) => a - b);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};

export interface BuildOptions {
  smooth: number;
  mergeSpeed: number;
  mergeIncline: number;
  /**
   * Seconds per step after filling in between readings: speed and incline change gradually
   * between one reading and the next, as they do on screen, rather than jumping every window
   * (user request, 2026-11-24). 0 keeps one step per window.
   */
  resolution: number;
}

const DEFAULTS: BuildOptions = { smooth: 3, mergeSpeed: 0.15, mergeIncline: 0.4, resolution: 10 };

/**
 * Fill in between readings: each window is cut into pieces of [resolution] seconds, and each piece
 * gets the value on the straight line between the middles of the windows around it.
 */
export function interpolate(
  rows: { duration: number; speed: number; incline: number }[],
  resolution: number,
): { duration: number; speed: number; incline: number }[] {
  if (resolution <= 0 || rows.length < 2) return rows;
  const centres: number[] = [];
  let t = 0;
  for (const r of rows) {
    centres.push(t + r.duration / 2);
    t += r.duration;
  }
  const at = (time: number, key: "speed" | "incline") => {
    if (time <= centres[0]) return rows[0][key];
    for (let i = 1; i < rows.length; i++) {
      if (time <= centres[i]) {
        const share = (time - centres[i - 1]) / (centres[i] - centres[i - 1]);
        return rows[i - 1][key] + (rows[i][key] - rows[i - 1][key]) * share;
      }
    }
    return rows[rows.length - 1][key];
  };
  const out: { duration: number; speed: number; incline: number }[] = [];
  let start = 0;
  for (const r of rows) {
    let done = 0;
    while (done < r.duration - 1e-9) {
      const piece = Math.min(resolution, r.duration - done);
      const mid = start + done + piece / 2;
      out.push({ duration: piece, speed: at(mid, "speed"), incline: at(mid, "incline") });
      done += piece;
    }
    start += r.duration;
  }
  return out;
}

export function buildProgram(
  readings: WindowReading[],
  name: string,
  options: Partial<BuildOptions> = {},
): Program {
  const o = { ...DEFAULTS, ...options };
  const speeds = readings.map((r) => r.speedKph).filter((v): v is number => v !== null);
  if (speeds.length === 0) throw new Error("no speeds were read from the video");
  const inclines = readings.map((r) => r.inclinePercent).filter((v): v is number => v !== null);

  // A missing reading is not a stationary runner: fill with the median, never zero.
  const fallbackSpeed = median(speeds);
  const fallbackIncline = inclines.length ? median(inclines) : 0;
  let rows = readings.map((r) => ({
    duration: r.durationSeconds,
    speed: r.speedKph ?? fallbackSpeed,
    incline: r.inclinePercent ?? fallbackIncline,
  }));

  // One window that disagrees with both neighbours becomes their average.
  const smoothed = rows.map((r) => ({ ...r }));
  for (let i = 1; i < rows.length - 1; i++) {
    const [a, b, c] = [rows[i - 1], rows[i], rows[i + 1]];
    const speedGap = Math.abs(b.speed - a.speed) + Math.abs(b.speed - c.speed);
    const inclineGap = Math.abs(b.incline - a.incline) + Math.abs(b.incline - c.incline);
    if (speedGap > 3 && inclineGap > 6) {
      smoothed[i].speed = (a.speed + c.speed) / 2;
      smoothed[i].incline = (a.incline + c.incline) / 2;
    }
  }
  rows = smoothed;

  // Running mean, to drop the jitter of a model reading pictures.
  if (o.smooth > 1) {
    const half = o.smooth >> 1;
    rows = rows.map((r, i) => {
      const w = rows.slice(Math.max(0, i - half), Math.min(rows.length, i + half + 1));
      return {
        duration: r.duration,
        speed: w.reduce((n, x) => n + x.speed, 0) / w.length,
        incline: w.reduce((n, x) => n + x.incline, 0) / w.length,
      };
    });
  }

  const fine = interpolate(rows, o.resolution);
  // The finest steps that stay inside the step limit: coarser merging if a long video needs it.
  let mergeSpeed = o.mergeSpeed;
  let mergeIncline = o.mergeIncline;
  let steps = toSteps(fine, mergeSpeed, mergeIncline);
  while (steps.length > LIMITS.maxSteps) {
    mergeSpeed *= 1.5;
    mergeIncline *= 1.5;
    steps = toSteps(fine, mergeSpeed, mergeIncline);
  }

  // The video's own pace: the time-weighted average of what was read, before limits and rounding,
  // so the app can play the video at belt speed / this pace ("Match my speed").
  const timed = readings.filter((r) => r.speedKph !== null && r.durationSeconds > 0);
  const seconds = timed.reduce((n, r) => n + r.durationSeconds, 0);
  const videoPaceKph = seconds > 0 ? timed.reduce((n, r) => n + (r.speedKph as number) * r.durationSeconds, 0) / seconds : fallbackSpeed;
  return { name, steps, videoPaceKph: Math.round(videoPaceKph * 100) / 100 };
}

/** Round to what the machine takes, then merge neighbours that are close enough. */
function toSteps(rows: { duration: number; speed: number; incline: number }[], mergeSpeed: number, mergeIncline: number): Step[] {
  const steps: Step[] = [];
  for (const r of rows) {
    // Limits, then the machine's own minimum: it cannot be told to stand still.
    const speed = Math.round(Math.max(LIMITS.minSpeedKph, Math.min(LIMITS.maxSpeedKph, r.speed)) * 20) / 20;
    const incline =
      Math.round(Math.max(LIMITS.minInclinePercent, Math.min(LIMITS.maxInclinePercent, r.incline)) * 2) / 2;
    const duration = r.duration;
    if (duration <= 0) continue;
    const last = steps[steps.length - 1];
    if (last && Math.abs(last.speedKph - speed) <= mergeSpeed && Math.abs(last.inclinePercent - incline) <= mergeIncline) {
      last.durationSeconds += duration;
    } else {
      steps.push({ speedKph: speed, inclinePercent: incline, durationSeconds: duration });
    }
  }
  for (const s of steps) s.durationSeconds = Math.round(s.durationSeconds);
  return steps.filter((s) => s.durationSeconds > 0);
}
