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
  return { name, steps };
}

export function totalSeconds(p: Program): number {
  return p.steps.reduce((n, s) => n + s.durationSeconds, 0);
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
