import { test } from "node:test";
import assert from "node:assert/strict";
import { MemoryStore } from "../lib/repo.js";
import { aiProblem, textProblem, videoProblem, videoSafety, type VideoSafety } from "../lib/moderation.js";
import { getProgram, hideProgram, listPrograms, report, savePublished, useRepoStoreForTests, type StoredProgram } from "../lib/store.js";

const ok: VideoSafety = {
  title: "4K Treadmill Run | Lake Thun, Switzerland",
  description: "Scenic run. Subscribe: https://youtube.com/@channel",
  channel: "Scenic Runs",
  thumbnail: "",
  embeddable: true,
  privacyStatus: "public",
  uploadStatus: "processed",
  ageRestricted: false,
  madeForKids: false,
};

test("the word filter catches bad words and dodges, not innocent ones, and refuses links", () => {
  assert.equal(textProblem("Morning hill walk"), null);
  assert.equal(textProblem("Essex coastal run · Sussex downs"), null);
  assert.equal(textProblem("Analysis of pace"), null);
  assert.notEqual(textProblem("Free p0rn run"), null);
  assert.notEqual(textProblem("NSFW workout"), null);
  assert.notEqual(textProblem("S3XY run"), null);
  assert.notEqual(textProblem("great run see www.example.com"), null);
  assert.notEqual(textProblem("visit https://x.y"), null);
  assert.equal(textProblem("see https://youtube.com", true), null);
  process.env.MODERATION_WORDS = "badword";
  assert.notEqual(textProblem("a BadWord run"), null);
  delete process.env.MODERATION_WORDS;
});

test("videos must be public, playable, not age-restricted, with a clean title", () => {
  assert.equal(videoProblem(ok), null);
  assert.match(videoProblem(null)!, /not on YouTube/);
  assert.match(videoProblem({ ...ok, ageRestricted: true })!, /age-restricted/);
  assert.match(videoProblem({ ...ok, privacyStatus: "unlisted" })!, /not public/);
  assert.match(videoProblem({ ...ok, embeddable: false })!, /cannot be played/);
  assert.match(videoProblem({ ...ok, title: "xxx treadmill" })!, /not suitable/);
});

test("YouTube's fields are read into VideoSafety", async () => {
  const fake = (async () =>
    new Response(
      JSON.stringify({
        items: [
          {
            snippet: { title: "Run", description: "d", channelTitle: "c", thumbnails: { medium: { url: "t" } } },
            contentDetails: { contentRating: { ytRating: "ytAgeRestricted" } },
            status: { embeddable: true, privacyStatus: "public", uploadStatus: "processed" },
          },
        ],
      }),
    )) as unknown as typeof fetch;
  const v = await videoSafety("abcdefghijk", "key", fake);
  assert.equal(v?.ageRestricted, true);
  assert.equal(v?.thumbnail, "t");
  const none = (async () => new Response(JSON.stringify({ items: [] }))) as unknown as typeof fetch;
  assert.equal(await videoSafety("abcdefghijk", "key", none), null);
});

test("the AI verdict refuses unsuitable videos and never blocks when the service fails", async () => {
  const answer = (suitable: boolean) =>
    (async () =>
      new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify({ suitable, reason: "r" }) }] } }] }))) as unknown as typeof fetch;
  assert.equal(await aiProblem(ok, "k", "m", answer(true)), null);
  assert.notEqual(await aiProblem(ok, "k", "m", answer(false)), null);
  const blocked = (async () => new Response(JSON.stringify({ promptFeedback: { blockReason: "SAFETY" } }))) as unknown as typeof fetch;
  assert.notEqual(await aiProblem(ok, "k", "m", blocked), null);
  const down = (async () => new Response("no", { status: 503 })) as unknown as typeof fetch;
  assert.equal(await aiProblem(ok, "k", "m", down), null);
  const throws = (async () => { throw new Error("offline"); }) as unknown as typeof fetch;
  assert.equal(await aiProblem(ok, "k", "m", throws), null);
});

test("reports are counted once per person, and a hidden workout is neither listed nor served", async () => {
  useRepoStoreForTests(new MemoryStore());
  const p: StoredProgram = {
    id: "rrrr",
    program: { name: "Run", steps: [{ speedKph: 8, inclinePercent: 0, durationSeconds: 600 }] },
    videoId: null, note: "", createdAt: 1, ratingSum: 0, ratingCount: 0, origin: "manual",
  };
  await savePublished(p);
  assert.equal(await report("rrrr", "a", "spam"), 1);
  assert.equal(await report("rrrr", "a", "adult"), 1);
  assert.equal(await report("rrrr", "b", "adult"), 2);
  assert.equal(await report("nope", "a", "adult"), null);
  await hideProgram("rrrr", "reported by 3 people");
  assert.equal(await getProgram("rrrr"), null);
  assert.equal((await getProgram("rrrr", true))?.hidden?.reason, "reported by 3 people");
  assert.deepEqual(await listPrograms("new", 0, 10), []);
  assert.equal(await report("rrrr", "c", "adult"), null);
  useRepoStoreForTests(null);
});
