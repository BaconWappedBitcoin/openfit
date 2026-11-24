// Storage on Upstash Redis (Vercel Marketplace). Programmes are tiny JSON, so one key each.
//
//   program:<id>        JSON of a StoredProgram
//   programs:new        sorted set, score = publish time
//   programs:top        sorted set, score = rating score
//   votes:<id>          hash installHash -> 1..5
//   analysis:<videoId>  cached readings, so a video is only ever paid for once
//   rl:<bucket>:<ip>    rate-limit counters

import { Redis } from "@upstash/redis";
import { randomBytes } from "node:crypto";
import type { Program } from "./program.js";
import type { WindowReading } from "./build.js";

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

export const newId = () => randomBytes(6).toString("base64url");

/** Bayesian average, so one 5-star vote does not outrank fifty 4.8s. */
export const ratingScore = (sum: number, count: number, prior = 3.5, weight = 5) =>
  (sum + prior * weight) / (count + weight);

export async function savePublished(p: StoredProgram) {
  const r = db();
  await r.set(`program:${p.id}`, p);
  await r.zadd("programs:new", { score: p.createdAt, member: p.id });
  await r.zadd("programs:top", { score: ratingScore(0, 0), member: p.id });
}

export async function getProgram(id: string): Promise<StoredProgram | null> {
  return (await db().get<StoredProgram>(`program:${id}`)) ?? null;
}

export async function listPrograms(sort: "new" | "top", offset: number, limit: number): Promise<StoredProgram[]> {
  const r = db();
  const ids = await r.zrange<string[]>(`programs:${sort}`, offset, offset + limit - 1, { rev: true });
  if (ids.length === 0) return [];
  const rows = await r.mget<(StoredProgram | null)[]>(...ids.map((i) => `program:${i}`));
  return rows.filter((x): x is StoredProgram => x !== null);
}

/** One vote per install; a new vote replaces the old one. */
export async function vote(id: string, voter: string, stars: number): Promise<StoredProgram | null> {
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

export async function cachedAnalysis(videoId: string): Promise<WindowReading[] | null> {
  return (await db().get<WindowReading[]>(`analysis:${videoId}`)) ?? null;
}
export async function cacheAnalysis(videoId: string, readings: WindowReading[]) {
  await db().set(`analysis:${videoId}`, readings);
}

/** Fixed-window counter. Returns false when the caller is over the limit. */
export async function allow(bucket: string, who: string, max: number, windowSeconds: number): Promise<boolean> {
  const key = `rl:${bucket}:${who}`;
  const r = db();
  const n = await r.incr(key);
  if (n === 1) await r.expire(key, windowSeconds);
  return n <= max;
}
