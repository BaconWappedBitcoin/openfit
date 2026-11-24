import { test } from "node:test";
import assert from "node:assert/strict";
import { buildProgram, type WindowReading } from "../lib/build.js";
import { ProgramError, sanitize, tidyTitle } from "../lib/program.js";
import { parseIsoDuration } from "../lib/youtube.js";
import { ratingScore } from "../lib/store.js";

const reading = (i: number, speed: number | null, incline: number | null): WindowReading => ({
  index: i, startSeconds: i * 30, durationSeconds: 30, speedKph: speed, inclinePercent: incline,
  confidence: 0.5, terrain: "flat", note: "",
});

test("agreeing windows merge into one step", () => {
  const p = buildProgram([0, 1, 2, 3].map((i) => reading(i, 8, 1)), "x");
  assert.equal(p.steps.length, 1);
  assert.equal(p.steps[0].durationSeconds, 120);
});

test("output is held inside the machine's limits", () => {
  const p = buildProgram([0, 1, 2].map((i) => reading(i, 30, 40)), "x", { smooth: 1 });
  assert.equal(p.steps[0].speedKph, 12);
  assert.equal(p.steps[0].inclinePercent, 10);
});

test("a still moment is raised to the minimum, a missing reading is not zero", () => {
  const p = buildProgram([reading(0, 0, 0), reading(1, null, null), reading(2, 8, 0)], "x", { smooth: 1 });
  assert.equal(p.steps[0].speedKph, 1.6);
  assert.ok(p.steps.every((s) => s.speedKph >= 1.6));
});

test("a lone outlier is replaced by its neighbours", () => {
  const p = buildProgram([reading(0, 8, 0), reading(1, 12, 9), reading(2, 8, 0)], "x", { smooth: 1 });
  assert.equal(p.steps.length, 1);
  assert.equal(p.steps[0].speedKph, 8);
});

test("no readings at all is an error", () => {
  assert.throws(() => buildProgram([reading(0, null, null)], "x"));
});

test("sanitize clamps ceilings and rejects junk", () => {
  const ok = sanitize({ name: " Run\n ", steps: [{ speedKph: 99, inclinePercent: -99, durationSeconds: 60 }] });
  assert.equal(ok.name, "Run");
  assert.equal(ok.steps[0].speedKph, 12);
  assert.equal(ok.steps[0].inclinePercent, -5);
  assert.throws(() => sanitize({ name: "a", steps: [] }), ProgramError);
  assert.throws(() => sanitize({ name: "a", steps: [{ speedKph: "8", inclinePercent: 0, durationSeconds: 5 }] }), ProgramError);
  assert.throws(() => sanitize({ name: "a", steps: [{ speedKph: 8, inclinePercent: 0, durationSeconds: 0 }] }), ProgramError);
  assert.throws(() => sanitize(null), ProgramError);
});

test("ISO durations", () => {
  assert.equal(parseIsoDuration("PT1H2M3S"), 3723);
  assert.equal(parseIsoDuration("PT39M"), 2340);
  assert.equal(parseIsoDuration("garbage"), 0);
});

test("rating score favours many good votes over one", () => {
  assert.ok(ratingScore(240, 50) > ratingScore(5, 1));
});

test("a long title is cut at a word with no trailing dash", () => {
  const t = tidyTitle("Virtual Run 4k - Interlaken - Scenery Lake Switzerland - Virtual Running Video for Treadmill");
  assert.equal(t, "Virtual Run 4k - Interlaken - Scenery Lake Switzerland");
  assert.equal(tidyTitle("Short run"), "Short run");
  assert.ok(tidyTitle("x".repeat(200)).length <= 60);
});
