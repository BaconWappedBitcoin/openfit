// The programme shape the app reads (WorkoutCodec): a name and a list of steps.
// Everything that reaches the library passes through sanitize(), so a stored programme can never
// ask a machine for more than its ceilings, whatever the client sent.

export interface Step {
  speedKph: number;
  inclinePercent: number;
  durationSeconds: number;
}

export interface Program {
  name: string;
  steps: Step[];
  /**
   * The walker's or runner's own average ground speed in the video (km/h), before any limits:
   * the app's "Match my speed" plays the video at belt speed / this pace.
   */
  videoPaceKph?: number;
}

export const LIMITS = {
  maxSpeedKph: 12,
  minSpeedKph: 1.6,
  maxInclinePercent: 10,
  minInclinePercent: -5,
  maxSteps: 300,
  maxStepSeconds: 3600,
  maxTotalSeconds: 4 * 3600,
  maxNameLength: 80,
};

export class ProgramError extends Error {}

const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v));

/** Validate untrusted input and return a clean programme, or throw ProgramError. */
export function sanitize(input: unknown): Program {
  if (typeof input !== "object" || input === null) throw new ProgramError("programme must be an object");
  const raw = input as { name?: unknown; steps?: unknown };
  if (typeof raw.name !== "string") throw new ProgramError("name must be text");
  const name = raw.name.replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, LIMITS.maxNameLength);
  if (!name) throw new ProgramError("name is empty");
  if (!Array.isArray(raw.steps) || raw.steps.length === 0) throw new ProgramError("steps must be a non-empty list");
  if (raw.steps.length > LIMITS.maxSteps) throw new ProgramError(`at most ${LIMITS.maxSteps} steps`);

  let total = 0;
  const steps = raw.steps.map((s, i): Step => {
    const step = s as Partial<Step>;
    const { speedKph, inclinePercent, durationSeconds } = step;
    if (![speedKph, inclinePercent, durationSeconds].every((n) => typeof n === "number" && Number.isFinite(n))) {
      throw new ProgramError(`step ${i + 1} needs numeric speedKph, inclinePercent and durationSeconds`);
    }
    const duration = Math.round(durationSeconds as number);
    if (duration < 1 || duration > LIMITS.maxStepSeconds) throw new ProgramError(`step ${i + 1} has a bad duration`);
    total += duration;
    return {
      speedKph: clamp(speedKph as number, LIMITS.minSpeedKph, LIMITS.maxSpeedKph),
      inclinePercent: clamp(inclinePercent as number, LIMITS.minInclinePercent, LIMITS.maxInclinePercent),
      durationSeconds: duration,
    };
  });
  if (total > LIMITS.maxTotalSeconds) throw new ProgramError("programme is too long");
  // The video's own pace, when given: kept only if it is a plausible walking or running speed.
  const pace = (raw as { videoPaceKph?: unknown }).videoPaceKph;
  if (typeof pace === "number" && Number.isFinite(pace) && pace >= 0.5 && pace <= 30) {
    return { name, steps, videoPaceKph: Math.round(pace * 100) / 100 };
  }
  return { name, steps };
}

export function totalSeconds(p: Program): number {
  return p.steps.reduce((n, s) => n + s.durationSeconds, 0);
}

/**
 * How hard a workout is, for the app's Easy/Medium/Hard and Walk/Run labels: the time-weighted
 * average METs from the ACSM walking (below 6.4 km/h) and running equations, uphill grade only,
 * and the share of time spent at running speed. The app turns these into labels (WorkoutRating).
 */
export function effort(p: Program): { mets: number; runShare: number } {
  const steps = p.steps.filter((s) => s.durationSeconds > 0);
  const total = Math.max(1, steps.reduce((n, s) => n + s.durationSeconds, 0));
  let mets = 0;
  let run = 0;
  for (const s of steps) {
    mets += stepMets(s.speedKph, s.inclinePercent) * s.durationSeconds;
    if (s.speedKph >= 6.4) run += s.durationSeconds;
  }
  return { mets: Math.round((mets / total) * 100) / 100, runShare: Math.round((run / total) * 100) / 100 };
}

function stepMets(speedKph: number, inclinePercent: number): number {
  if (speedKph <= 0) return 1;
  const v = (speedKph * 1000) / 60;
  const grade = Math.max(0, inclinePercent) / 100;
  const vo2 = speedKph < 6.4 ? 0.1 * v + 1.8 * v * grade + 3.5 : 0.2 * v + 0.9 * v * grade + 3.5;
  return vo2 / 3.5;
}

/** A video title shortened for a workout name: cut at a word, never mid-word, with no trailing dash. */
export function tidyTitle(title: string, max = 60): string {
  const clean = title.replace(/[\u0000-\u001f\u007f]/g, " ").replace(/\s+/g, " ").trim();
  if (clean.length <= max) return clean;
  const cut = clean.slice(0, max + 1);
  const atWord = cut.lastIndexOf(" ");
  const short = (atWord > max / 2 ? cut.slice(0, atWord) : clean.slice(0, max)).replace(/[\s\-–—|:,]+$/, "");
  return short || clean.slice(0, max);
}
