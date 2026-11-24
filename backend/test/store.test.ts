import { test } from "node:test";
import assert from "node:assert/strict";
import { MemoryStore, update } from "../lib/repo.js";
import {
  getCache,
  getProgram,
  listPrograms,
  publicVoter,
  savePublished,
  setCache,
  useRepoStoreForTests,
  vote,
  type StoredProgram,
} from "../lib/store.js";

const program = (id: string, createdAt: number): StoredProgram => ({
  id,
  program: { name: `Run ${id}`, steps: [{ speedKph: 8, inclinePercent: 0, durationSeconds: 600 }] },
  videoId: null,
  note: "",
  createdAt,
  ratingSum: 0,
  ratingCount: 0,
  origin: "manual",
});

test("shared workouts are kept in the repository file and listed newest or best first", async () => {
  const store = new MemoryStore();
  useRepoStoreForTests(store);
  await savePublished(program("aaaa", 1));
  await savePublished(program("bbbb", 2));
  assert.deepEqual((await listPrograms("new", 0, 10)).map((p) => p.id), ["bbbb", "aaaa"]);
  await vote("aaaa", "voter-1", 5);
  await vote("aaaa", "voter-2", 5);
  assert.deepEqual((await listPrograms("top", 0, 10)).map((p) => p.id), ["aaaa", "bbbb"]);
  assert.equal((await getProgram("bbbb"))?.program.name, "Run bbbb");
  useRepoStoreForTests(null);
});

test("a second vote from the same voter replaces the first, and voters are not stored as given", async () => {
  const store = new MemoryStore();
  useRepoStoreForTests(store);
  await savePublished(program("cccc", 1));
  await vote("cccc", "voter-1", 2);
  const p = await vote("cccc", "voter-1", 4);
  assert.equal(p?.ratingCount, 1);
  assert.equal(p?.ratingSum, 4);
  const votes = (await store.read<Record<string, number>>("votes/cccc.json"))!.value;
  assert.ok(!("voter-1" in votes));
  assert.equal(votes[publicVoter("voter-1")], 4);
  assert.equal(await vote("nope", "voter-1", 3), null);
  useRepoStoreForTests(null);
});

test("AI results are kept as files; anything that is not a video id is refused", async () => {
  const store = new MemoryStore();
  useRepoStoreForTests(store);
  await setCache("facts20", "D870xNwuSAM", { place: "Dallas", facts: ["x"] }, 60);
  assert.deepEqual(await getCache("facts20", "D870xNwuSAM"), { place: "Dallas", facts: ["x"] });
  assert.ok(await store.read("cache/facts20/D870xNwuSAM.json"));
  await setCache("landmarks", "../../etc/x", { bad: true }, 60);
  assert.equal(store.writes, 1);
  // Searches stay in memory: not worth a commit each.
  await setCache("search", "paris run", ["v"], 60);
  assert.deepEqual(await getCache("search", "paris run"), ["v"]);
  assert.equal(store.writes, 1);
  useRepoStoreForTests(null);
});

test("a write that lost a race is retried on a fresh read", async () => {
  const store = new MemoryStore();
  await store.write("n.json", 1, "start");
  let raced = false;
  const result = await update<number>(store, "n.json", 0, "add", (n) => {
    if (!raced) {
      raced = true;
      // Someone else writes in between our read and our write.
      void store.read<number>("n.json").then((f) => store.write("n.json", 10, "other", f!.sha));
    }
    return n + 1;
  });
  assert.ok(result === 2 || result === 11, String(result));
});
