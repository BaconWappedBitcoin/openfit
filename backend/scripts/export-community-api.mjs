// One-off: copy the shared workouts from the live backend's public API into community/programs.json
// (user decision 2026-11-24: keep the community data in the repo). Used because the database
// credentials are sensitive on Vercel and cannot be read back. Individual votes are not public, so
// each workout keeps its vote count and a rating total rebuilt from its (rounded) score.
//
//   node scripts/export-community-api.mjs <out dir> [backend url]

import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const out = process.argv[2];
const base = process.argv[3] ?? "https://ofit-backend.vercel.app";
if (!out) throw new Error("usage: export-community-api.mjs <out dir> [backend url]");

const get = async (path) => {
  const res = await fetch(base + path, { headers: { "User-Agent": "openfit-export" } });
  if (!res.ok) throw new Error(`${path}: HTTP ${res.status}`);
  return res.json();
};

const ids = new Set();
for (let offset = 0; ; offset += 50) {
  const { programs } = await get(`/api/programs?sort=new&offset=${offset}&limit=50`);
  programs.forEach((p) => ids.add(p.id));
  if (programs.length < 50) break;
}

const PRIOR = 3.5, WEIGHT = 5; // as ratingScore in lib/store.ts
const programs = [];
for (const id of ids) {
  const p = await get(`/api/programs/${encodeURIComponent(id)}`);
  const count = p.ratings ?? 0;
  const sum = count === 0 ? 0 : Math.max(count, Math.min(5 * count, Math.round(p.rating * (count + WEIGHT) - PRIOR * WEIGHT)));
  programs.push({
    id: p.id,
    program: p.program,
    videoId: p.videoId ?? null,
    note: p.note ?? "",
    createdAt: p.createdAt,
    ratingSum: sum,
    ratingCount: count,
    origin: p.origin === "analysis" ? "analysis" : "manual",
  });
}
programs.sort((a, b) => a.createdAt - b.createdAt);
mkdirSync(out, { recursive: true });
writeFileSync(join(out, "programs.json"), JSON.stringify(programs, null, 1) + "\n");
console.log(`programs ${programs.length}`);
