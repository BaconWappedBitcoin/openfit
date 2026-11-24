import { test } from "node:test";
import assert from "node:assert/strict";
import { buildProgram, interpolate, type WindowReading } from "../lib/build.js";
import { effort, ProgramError, sanitize, tidyTitle } from "../lib/program.js";
import { parseIsoDuration } from "../lib/youtube.js";
import { ratingScore } from "../lib/store.js";
import { cleanFacts, cleanLandmarks } from "../lib/gemini.js";

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

test("the video's own pace is the time-weighted average of what was read", () => {
  const p = buildProgram([reading(0, 4, 0), reading(1, 6, 0), reading(2, null, 0)], "x", { smooth: 1 });
  assert.equal(p.videoPaceKph, 5);
});

test("sanitize keeps a plausible video pace and drops a silly one", () => {
  const steps = [{ speedKph: 5, inclinePercent: 0, durationSeconds: 60 }];
  assert.equal(sanitize({ name: "x", steps, videoPaceKph: 4.567 }).videoPaceKph, 4.57);
  assert.equal(sanitize({ name: "x", steps, videoPaceKph: 99 }).videoPaceKph, undefined);
  assert.equal(sanitize({ name: "x", steps }).videoPaceKph, undefined);
});

test("speed ramps gradually between readings instead of jumping", () => {
  const p = buildProgram([reading(0, 6, 0), reading(1, 9, 0)], "x", { smooth: 1 });
  assert.ok(p.steps.length >= 4, `expected a ramp, got ${p.steps.length} steps`);
  const speeds = p.steps.map((s) => s.speedKph);
  assert.ok(speeds.every((v, i) => i === 0 || v >= speeds[i - 1]), "never goes down on the way up");
  assert.equal(speeds[0], 6);
  assert.equal(speeds[speeds.length - 1], 9);
  assert.equal(p.steps.reduce((n, s) => n + s.durationSeconds, 0), 60, "keeps the video's length");
});

test("interpolate fills in on a straight line between window middles", () => {
  const out = interpolate([{ duration: 30, speed: 6, incline: 0 }, { duration: 30, speed: 9, incline: 3 }], 10);
  assert.equal(out.length, 6);
  assert.deepEqual(out.map((r) => r.speed), [6, 6, 7, 8, 9, 9]);
});

test("a long video stays inside the step limit", () => {
  const readings = Array.from({ length: 240 }, (_, i) => reading(i, 5 + (i % 2) * 2, (i % 3) * 2));
  const p = buildProgram(readings, "x", { smooth: 1 });
  assert.ok(p.steps.length <= 300, `${p.steps.length} steps`);
});

test("place facts are cleaned: short strings only, none without a place", () => {
  const ok = cleanFacts({ place: " Paris ", facts: ["The Eiffel Tower was built for the 1889 World's Fair.", 3, "x", "a".repeat(400)] });
  assert.equal(ok.place, "Paris");
  assert.deepEqual(ok.facts, ["The Eiffel Tower was built for the 1889 World's Fair."]);
  assert.deepEqual(cleanFacts({ place: null, facts: ["Something long enough to count."] }), { place: null, facts: [] });
  assert.deepEqual(cleanFacts("junk"), { place: null, facts: [] });
});

test("landmarks are cleaned: in the video, ordered, spaced, no repeats", () => {
  const out = cleanLandmarks({ landmarks: [
    { seconds: 300, name: "Eiffel Tower", fact: "Built for the 1889 World's Fair." },
    { seconds: 30, name: "Pont Alexandre III", fact: "An ornate bridge over the Seine." },
    { seconds: 310, name: "Too close", fact: "x" },
    { seconds: 400, name: "eiffel tower", fact: "again" },
    { seconds: 99999, name: "Out of the video", fact: "x" },
    { seconds: 500, name: "", fact: "no name" },
  ] }, 3600);
  assert.deepEqual(out.map((l) => l.name), ["Pont Alexandre III", "Eiffel Tower", "eiffel tower"].slice(0, 2).concat([]));
  assert.deepEqual(cleanLandmarks("junk", 100), []);
});

test("effort: average METs and the share of time at running speed", () => {
  const e = effort({ name: "x", steps: [{ speedKph: 4.8, inclinePercent: 0, durationSeconds: 600 }, { speedKph: 10, inclinePercent: 1, durationSeconds: 600 }] });
  assert.equal(e.runShare, 0.5);
  assert.ok(Math.abs(e.mets - (3.29 + 10.95) / 2) < 0.02, String(e.mets));
});
