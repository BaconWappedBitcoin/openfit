// Pre-scan a library of YouTube scenery videos (user request 2026-11-24): pick varied, embeddable
// walking and running videos, have the backend read each one into a workout (with the server's
// Gemini key), share it to the community library, and save the workout, the speed readings, the
// place facts and the landmarks into community/, so anyone gets them at once and for free, with or
// without a server. Videos are streamed by YouTube's own player: nothing is downloaded.
//
//   ADMIN_TOKEN=... node scripts/prescan.mjs pick <out.json>        choose the videos
//   ADMIN_TOKEN=... node scripts/prescan.mjs scan <list.json> <community dir>

import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const BASE = process.env.OPENFIT_BACKEND ?? "https://ofit-backend.vercel.app";
const TOKEN = process.env.ADMIN_TOKEN ?? "";
const headers = { "User-Agent": "openfit-prescan", "x-admin-token": TOKEN, "x-install-id": "openfit-prescan" };

async function call(method, path, body, timeoutMs = 340_000) {
  const res = await fetch(BASE + path, {
    method,
    headers: { ...headers, ...(body ? { "Content-Type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(timeoutMs),
  });
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch { json = { error: text.slice(0, 200) }; }
  if (!res.ok) throw new Error(`${path}: HTTP ${res.status} ${json.error ?? ""}`);
  return json;
}

// Varied scenery, walking and running, in the places people like to see.
const QUERIES = [
  "virtual run city 4k", "treadmill walk city scenery 4k", "virtual run mountain trail 4k",
  "virtual walk forest 4k", "treadmill run beach 4k", "virtual run lake scenery 4k",
  "virtual hike national park", "treadmill walk snow winter 4k", "virtual run countryside 4k",
  "virtual run japan", "virtual walk italy", "virtual run new york",
];

async function pick(out) {
  const chosen = [];
  const seen = new Set();
  for (const q of QUERIES) {
    const { videos } = await call("GET", "/api/search?q=" + encodeURIComponent(q));
    const good = videos.filter((v) => !seen.has(v.id) && v.durationSeconds >= 15 * 60 && v.durationSeconds <= 60 * 60);
    for (const v of good.slice(0, 2)) {
      seen.add(v.id);
      chosen.push({ id: v.id, title: v.title, channel: v.channel, durationSeconds: v.durationSeconds, query: q });
    }
  }
  writeFileSync(out, JSON.stringify(chosen.slice(0, 20), null, 1));
  console.log(`picked ${Math.min(chosen.length, 20)} of ${chosen.length}`);
  for (const v of chosen.slice(0, 20)) console.log(`${v.id}  ${Math.round(v.durationSeconds / 60)} min  ${v.title}`);
}

async function scan(listFile, dir) {
  const list = JSON.parse(readFileSync(listFile, "utf8"));
  const write = (path, value) => {
    const file = join(dir, path);
    mkdirSync(join(file, ".."), { recursive: true });
    writeFileSync(file, JSON.stringify(value, null, 1) + "\n");
  };
  const done = [];
  for (const v of list) {
    const start = Date.now();
    try {
      const analysisFile = join(dir, "cache", "analysis", `${v.id}.json`);
      let program;
      if (!existsSync(analysisFile)) {
        const a = await call("POST", "/api/analyze", { videoId: v.id });
        write(`cache/analysis/${v.id}.json`, a.readings);
        program = a.program;
        const { id } = await call("POST", "/api/programs", { program, videoId: v.id, origin: "analysis" });
        v.programId = id;
      }
      const facts = await call("GET", `/api/facts?v=${v.id}`);
      write(`cache/facts20/${v.id}.json`, facts);
      const landmarks = await call("GET", `/api/landmarks?v=${v.id}`);
      write(`cache/landmarks/${v.id}.json`, landmarks);
      console.log(`ok ${v.id} in ${Math.round((Date.now() - start) / 1000)} s: ${program?.steps?.length ?? "cached"} steps, ${facts.facts?.length ?? 0} facts, ${landmarks.landmarks?.length ?? 0} landmarks  ${v.title}`);
      done.push(v);
    } catch (e) {
      console.log(`FAILED ${v.id}: ${e.message}  ${v.title}`);
    }
  }
  console.log(`done ${done.length} of ${list.length}`);
}

const [mode, a, b] = process.argv.slice(2);
if (mode === "pick") await pick(a);
else if (mode === "scan") await scan(a, b);
else throw new Error("usage: prescan.mjs pick <out.json> | scan <list.json> <community dir>");
