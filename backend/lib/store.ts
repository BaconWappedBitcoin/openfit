// Where the community data lives.
//
// In the repository (user decision 2026-11-24) when GITHUB_TOKEN and GITHUB_REPO are set: shared
// workouts, votes and the AI results are JSON files in community/ (see repo.ts), committed through
// GitHub's API, readable and forkable by anyone. Rate limits and the short-lived search cache are
// then kept in memory on each server instance (best effort, as they are not worth a commit each).
//
// Otherwise on Upstash Redis (Vercel Marketplace), as before:
//   program:<id>        JSON of a StoredProgram
//   programs:new        sorted set, score = publish time
//   programs:top        sorted set, score = rating score
//   votes:<id>          hash installHash -> 1..5
//   reports:<id>        hash installHash -> reason (moderation.ts)
//   analysis:<videoId>  cached readings, so a video is only ever paid for once
//   facts20:<videoId>, landmarks:<videoId>, search:<q>   cached answers
//   rl:<bucket>:<ip>    rate-limit counters

import { Redis } from "@upstash/redis";
import { createHmac, randomBytes } from "node:crypto";
import type { Program } from "./program.js";
import type { WindowReading } from "./build.js";
import { GitHubStore, type JsonStore, repoConfigured, update } from "./repo.js";

export interface StoredProgram {
  id: string;
  program: Program;
  videoId: string | null;
  note: string;
  createdAt: number;
  ratingSum: number;
  ratingCount: number;
  /** "analysis" if built from a video analysis, "manual" if hand-written. */
  origin: "analysis" | "manual";
  /** Hidden by moderation (reports, or its video went bad): kept, but never listed or served. */
  hidden?: { at: number; reason: string };
}

let redis: Redis | null = null;
export function db(): Redis {
  if (!redis) {
    const url = process.env.KV_REST_API_URL ?? process.env.UPSTASH_REDIS_REST_URL;
    const token = process.env.KV_REST_API_TOKEN ?? process.env.UPSTASH_REDIS_REST_TOKEN;
    if (!url || !token) throw new Error("storage is not configured");
    redis = new Redis({ url, token });
  }
  return redis;
}

let repo: JsonStore | null = null;
/** The repository store, or null when the data is on Redis. */
function repoStore(): JsonStore | null {
  if (repo) return repo;
  if (!repoConfigured()) return null;
  repo = GitHubStore.fromEnv();
  return repo;
}
/** Tests: use [store] (a MemoryStore) for the repository. */
export function useRepoStoreForTests(store: JsonStore | null) {
  repo = store;
  memory.clear();
}

export const newId = () => randomBytes(6).toString("base64url");

/** Bayesian average, so one 5-star vote does not outrank fifty 4.8s. */
export const ratingScore = (sum: number, count: number, prior = 3.5, weight = 5) =>
  (sum + prior * weight) / (count + weight);

const PROGRAMS = "programs.json";

export async function savePublished(p: StoredProgram) {
  const store = repoStore();
  if (store) {
    await update<StoredProgram[]>(store, PROGRAMS, [], `community: share "${p.program.name}"`, (all) => [...all, p]);
    return;
  }
  const r = db();
  await r.set(`program:${p.id}`, p);
  await r.zadd("programs:new", { score: p.createdAt, member: p.id });
  await r.zadd("programs:top", { score: ratingScore(0, 0), member: p.id });
}

/** One shared workout; a hidden one only with [includeHidden] (moderation's own use). */
export async function getProgram(id: string, includeHidden = false): Promise<StoredProgram | null> {
  const store = repoStore();
  let p: StoredProgram | null;
  if (store) {
    const all = (await store.read<StoredProgram[]>(PROGRAMS))?.value ?? [];
    p = all.find((x) => x.id === id) ?? null;
  } else {
    p = (await db().get<StoredProgram>(`program:${id}`)) ?? null;
  }
  return p && (includeHidden || !p.hidden) ? p : null;
}

export async function listPrograms(sort: "new" | "top", offset: number, limit: number): Promise<StoredProgram[]> {
  const store = repoStore();
  if (store) {
    const all = ((await store.read<StoredProgram[]>(PROGRAMS))?.value ?? []).filter((p) => !p.hidden);
    const sorted = [...all].sort((a, b) =>
      sort === "new" ? b.createdAt - a.createdAt : ratingScore(b.ratingSum, b.ratingCount) - ratingScore(a.ratingSum, a.ratingCount),
    );
    return sorted.slice(offset, offset + limit);
  }
  const r = db();
  const ids = await r.zrange<string[]>(`programs:${sort}`, offset, offset + limit - 1, { rev: true });
  if (ids.length === 0) return [];
  const rows = await r.mget<(StoredProgram | null)[]>(...ids.map((i) => `program:${i}`));
  return rows.filter((x): x is StoredProgram => x !== null && !x.hidden);
}

/** Hide a shared workout (moderation). It stays stored, with why and when. */
export async function hideProgram(id: string, reason: string): Promise<void> {
  const hidden = { at: Date.now(), reason };
  const store = repoStore();
  if (store) {
    await update<StoredProgram[]>(store, PROGRAMS, [], `community: hide ${id} (${reason})`, (all) => {
      const p = all.find((x) => x.id === id);
      if (!p || p.hidden) return undefined;
      p.hidden = hidden;
      return all;
    });
    return;
  }
  const r = db();
  const p = await r.get<StoredProgram>(`program:${id}`);
  if (!p || p.hidden) return;
  await r.set(`program:${id}`, { ...p, hidden });
  await r.zrem("programs:new", id);
  await r.zrem("programs:top", id);
}

/**
 * One report per install per workout (a new one replaces the old). Returns how many different
 * people have reported it, or null if there is no such workout.
 */
export async function report(id: string, voter: string, reason: string): Promise<number | null> {
  if (!(await getProgram(id))) return null;
  const store = repoStore();
  if (store) {
    const all = await update<Record<string, string>>(store, `reports/${id}.json`, {}, `community: report ${id}`, (v) => ({
      ...v,
      [publicVoter(voter)]: reason,
    }));
    return Object.keys(all ?? {}).length;
  }
  const r = db();
  await r.hset(`reports:${id}`, { [voter]: reason });
  return await r.hlen(`reports:${id}`);
}

/**
 * The voter as stored in the public repository: the voter hash keyed with a server-only secret, so
 * a published vote cannot be tied back to an install or an address.
 */
export function publicVoter(voter: string): string {
  const secret = process.env.VOTE_SECRET || process.env.GITHUB_TOKEN || "openfit";
  return createHmac("sha256", secret).update(voter).digest("base64url").slice(0, 16);
}

/** One vote per install; a new vote replaces the old one. */
export async function vote(id: string, voter: string, stars: number): Promise<StoredProgram | null> {
  const store = repoStore();
  if (store) {
    if (!(await getProgram(id))) return null;
    const votes = await update<Record<string, number>>(store, `votes/${id}.json`, {}, `community: rate ${id}`, (v) => ({
      ...v,
      [publicVoter(voter)]: stars,
    }));
    const tally = Object.values(votes ?? {});
    const sum = tally.reduce((a, b) => a + b, 0);
    let result: StoredProgram | null = null;
    await update<StoredProgram[]>(store, PROGRAMS, [], `community: rating of ${id}`, (all) => {
      const p = all.find((x) => x.id === id);
      if (!p) return undefined;
      p.ratingSum = sum;
      p.ratingCount = tally.length;
      result = p;
      return all;
    });
    return result;
  }
  const r = db();
  const p = await getProgram(id);
  if (!p) return null;
  const previous = await r.hget<number>(`votes:${id}`, voter);
  await r.hset(`votes:${id}`, { [voter]: stars });
  if (previous == null) {
    p.ratingSum += stars;
    p.ratingCount += 1;
  } else {
    p.ratingSum += stars - Number(previous);
  }
  await r.set(`program:${id}`, p);
  await r.zadd("programs:top", { score: ratingScore(p.ratingSum, p.ratingCount), member: id });
  return p;
}

// --- Caches -------------------------------------------------------------------------------------

/** Answers kept for good: in the repository as files, in Redis with the given time to live. */
export type CacheKind = "analysis" | "facts20" | "landmarks" | "search";
const KEPT: CacheKind[] = ["analysis", "facts20", "landmarks"];
const memory = new Map<string, { value: unknown; until: number }>();

export async function getCache<T>(kind: CacheKind, key: string): Promise<T | null> {
  const store = repoStore();
  if (store && KEPT.includes(kind)) {
    if (!/^[A-Za-z0-9_-]{11}$/.test(key)) return null;
    return (await store.read<T>(`cache/${kind}/${key}.json`))?.value ?? null;
  }
  if (store) {
    const hit = memory.get(`${kind}:${key}`);
    return hit && hit.until > Date.now() ? (hit.value as T) : null;
  }
  return (await db().get<T>(`${kind}:${key}`)) ?? null;
}

export async function setCache(kind: CacheKind, key: string, value: unknown, ttlSeconds: number) {
  const store = repoStore();
  if (store && KEPT.includes(kind)) {
    if (!/^[A-Za-z0-9_-]{11}$/.test(key)) return;
    const path = `cache/${kind}/${key}.json`;
    await update<unknown>(store, path, null, `community: ${kind} for ${key}`, () => value);
    return;
  }
  if (store) {
    memory.set(`${kind}:${key}`, { value, until: Date.now() + ttlSeconds * 1000 });
    return;
  }
  await db().set(`${kind}:${key}`, value, { ex: ttlSeconds });
}

export async function cachedAnalysis(videoId: string): Promise<WindowReading[] | null> {
  return getCache<WindowReading[]>("analysis", videoId);
}
export async function cacheAnalysis(videoId: string, readings: WindowReading[]) {
  // Kept for good: a video is only ever paid for once.
  const store = repoStore();
  if (store) return setCache("analysis", videoId, readings, 0);
  await db().set(`analysis:${videoId}`, readings);
}

// --- Rate limits --------------------------------------------------------------------------------

const counters = new Map<string, { n: number; until: number }>();

/** Fixed-window counter. Returns false when the caller is over the limit. */
export async function allow(bucket: string, who: string, max: number, windowSeconds: number): Promise<boolean> {
  const key = `rl:${bucket}:${who}`;
  if (repoStore()) {
    // Per server instance: a light brake, not a hard cap, once the data lives in the repository.
    const now = Date.now();
    const c = counters.get(key);
    if (!c || c.until <= now) {
      counters.set(key, { n: 1, until: now + windowSeconds * 1000 });
      return 1 <= max;
    }
    c.n++;
    return c.n <= max;
  }
  const r = db();
  const n = await r.incr(key);
  if (n === 1) await r.expire(key, windowSeconds);
  return n <= max;
}
