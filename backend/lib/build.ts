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
}

const DEFAULTS: BuildOptions = { smooth: 3, mergeSpeed: 0.4, mergeIncline: 0.8 };

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

  const steps: Step[] = [];
  for (const r of rows) {
    // Limits, then the machine's own minimum: it cannot be told to stand still.
    const speed = Math.round(Math.max(LIMITS.minSpeedKph, Math.min(LIMITS.maxSpeedKph, r.speed)) * 20) / 20;
    const incline =
      Math.round(Math.max(LIMITS.minInclinePercent, Math.min(LIMITS.maxInclinePercent, r.incline)) * 2) / 2;
    const duration = Math.round(r.duration);
    if (duration <= 0) continue;
    const last = steps[steps.length - 1];
    if (last && Math.abs(last.speedKph - speed) <= o.mergeSpeed && Math.abs(last.inclinePercent - incline) <= o.mergeIncline) {
      last.durationSeconds += duration;
    } else {
      steps.push({ speedKph: speed, inclinePercent: incline, durationSeconds: duration });
    }
  }
  return { name, steps };
}
