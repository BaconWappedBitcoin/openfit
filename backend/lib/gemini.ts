// Ask Gemini what the runner is doing in each window of a public YouTube video. Gemini reads the
// video from its address, so nothing is downloaded. Same prompt and schema as analyze_gemini.py.

import type { WindowReading } from "./build.js";

const ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models";

const PROMPT = (seconds: number) => `You are analysing a window of a running video so that a treadmill program can be built from it.
The window is ${seconds} seconds long and shows a runner, usually filmed from a chest or head
camera on the runner themselves, moving along a path.

How to judge the two numbers:
- speed_kph is the runner's ground speed in kilometres per hour. Estimate it from the footfall
  cadence multiplied by the stride length: an adult runner's stride is roughly 1.0 to 1.3 metres,
  so use 1.1 metres when you are unsure. When the camera is on the runner, each footfall makes one
  downward bob, so counting the bobs over ten seconds gives roughly (bobs per 10 s) * 12 steps per
  minute. If nothing in the window allows a judgement, give your best estimate and set the
  confidence low.
- incline_percent is the gradient of the ground under the runner in percent, where 10 means a ten
  per cent uphill slope and -10 a ten per cent downhill slope. Judge it from the visible slope of
  the path against the horizon and from the runner's posture.
- confidence is how sure you are about the two numbers together. Be honest: a low confidence is
  more useful than a confident guess.`;

const SCHEMA = {
  type: "OBJECT",
  properties: {
    speed_kph: { type: "NUMBER" },
    incline_percent: { type: "NUMBER" },
    terrain: {
      type: "STRING",
      enum: ["flat", "rolling", "uphill", "downhill", "stairs", "trail", "road", "water", "other"],
    },
    confidence: { type: "NUMBER" },
    note: { type: "STRING" },
  },
  required: ["speed_kph", "incline_percent", "terrain", "confidence", "note"],
};

const RETRYABLE = new Set([429, 500, 503, 504]);
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

async function call(key: string, model: string, body: unknown, attempts = 4): Promise<any> {
  for (let attempt = 1; ; attempt++) {
    const res = await fetch(`${ENDPOINT}/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": key },
      body: JSON.stringify(body),
    });
    if (res.ok) return res.json();
    if (RETRYABLE.has(res.status) && attempt < attempts) {
      await sleep(2 ** attempt * 1000);
      continue;
    }
    throw new Error(`Gemini returned HTTP ${res.status}: ${(await res.text()).slice(0, 300)}`);
  }
}

export async function analyseWindow(
  key: string,
  model: string,
  videoUrl: string,
  index: number,
  start: number,
  end: number,
): Promise<WindowReading> {
  const response = await call(key, model, {
    contents: [
      {
        parts: [
          { file_data: { file_uri: videoUrl }, video_metadata: { start_offset: `${start}s`, end_offset: `${end}s` } },
          { text: PROMPT(end - start) },
        ],
      },
    ],
    generationConfig: { temperature: 0.2, responseMimeType: "application/json", responseSchema: SCHEMA },
  });
  const answer = JSON.parse(response.candidates[0].content.parts[0].text);
  const num = (v: unknown) => (typeof v === "number" && Number.isFinite(v) ? v : null);
  return {
    index,
    startSeconds: start,
    durationSeconds: end - start,
    speedKph: num(answer.speed_kph),
    inclinePercent: num(answer.incline_percent),
    confidence: num(answer.confidence) ?? 0,
    terrain: String(answer.terrain),
    note: String(answer.note).slice(0, 300),
  };
}

/** Analyse every window with a bounded number in flight. Any failed window fails the run. */
export async function analyseVideo(
  key: string,
  model: string,
  videoUrl: string,
  durationSeconds: number,
  chunk = 30,
  concurrency = 16,
): Promise<WindowReading[]> {
  const windows: [number, number, number][] = [];
  for (let s = 0, i = 0; s < durationSeconds; s += chunk, i++) windows.push([i, s, Math.min(s + chunk, durationSeconds)]);
  const out: WindowReading[] = new Array(windows.length);
  let next = 0;
  const worker = async () => {
    while (next < windows.length) {
      const [i, s, e] = windows[next++];
      out[i] = await analyseWindow(key, model, videoUrl, i, s, e);
    }
  };
  await Promise.all(Array.from({ length: Math.min(concurrency, windows.length) }, worker));
  return out;
}
