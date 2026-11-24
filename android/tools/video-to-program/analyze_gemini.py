#!/usr/bin/env python3
"""Ask Gemini what the runner is doing in each thirty seconds of a YouTube video.

Gemini reads a public YouTube video straight from its address, so nothing is downloaded and no
chunk files are made: each request names the video and the window of it to look at. The answers
are written to analysis.json in the same shape analyze.py writes, so build_program.py takes
either one.

    uv run analyze_gemini.py --url https://www.youtube.com/watch?v=KiEN4HxM4ic \
        --duration-seconds 2400 --out T:\\git\\oFit\\android\\dist\\generated\\chunks-blue-pools

The key is read from GEMINI_API_KEY, in the environment or in a .env file in the working
directory or the project root. It is sent in a header, never in the address.

Failures are loud on purpose: a chunk Gemini did not answer stops the run, and the answers
already received are kept in raw/ so the run can be resumed with --resume.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"

# Same instructions as analyze.py, so the two providers are comparable.
PROMPT = """You are analysing a window of a running video so that a treadmill program can be built from it.
The window is {seconds:.0f} seconds long and shows a runner, usually filmed from a chest or head
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
  more useful than a confident guess."""

SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "speed_kph": {"type": "NUMBER"},
        "incline_percent": {"type": "NUMBER"},
        "terrain": {
            "type": "STRING",
            "enum": ["flat", "rolling", "uphill", "downhill", "stairs", "trail", "road", "water", "other"],
        },
        "confidence": {"type": "NUMBER"},
        "note": {"type": "STRING"},
    },
    "required": ["speed_kph", "incline_percent", "terrain", "confidence", "note"],
}

# Statuses worth trying again: rate limit and the server being busy. Anything else is a bug.
RETRYABLE = {429, 500, 503, 504}


def load_key() -> str:
    if "GEMINI_API_KEY" in os.environ:
        return os.environ["GEMINI_API_KEY"]
    for folder in (Path.cwd(), Path(__file__).resolve().parents[3]):
        env = folder / ".env"
        if env.is_file():
            for line in env.read_text(encoding="utf-8").splitlines():
                name, _, value = line.partition("=")
                if name.strip() == "GEMINI_API_KEY":
                    return value.strip().strip('"').strip("'")
    raise RuntimeError("GEMINI_API_KEY is not set: put it in the environment or in a .env file")


def request_body(url: str, start: int, end: int) -> dict:
    return {
        "contents": [{
            "parts": [
                {"file_data": {"file_uri": url},
                 "video_metadata": {"start_offset": f"{start}s", "end_offset": f"{end}s"}},
                {"text": PROMPT.format(seconds=end - start)},
            ],
        }],
        "generationConfig": {
            "temperature": 0.2,
            "responseMimeType": "application/json",
            "responseSchema": SCHEMA,
        },
    }


def call(key: str, model: str, body: dict, attempts: int = 5) -> dict:
    data = json.dumps(body).encode("utf-8")
    for attempt in range(1, attempts + 1):
        req = urllib.request.Request(
            ENDPOINT.format(model=model), data=data, method="POST",
            headers={"Content-Type": "application/json", "x-goog-api-key": key},
        )
        try:
            with urllib.request.urlopen(req, timeout=180) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code in RETRYABLE and attempt < attempts:
                wait = 2 ** attempt
                print(f"  HTTP {error.code}, retrying in {wait}s (attempt {attempt}/{attempts})", flush=True)
                time.sleep(wait)
                continue
            raise RuntimeError(f"Gemini returned HTTP {error.code}: {error.read().decode('utf-8', 'replace')[:500]}")
    raise RuntimeError("unreachable")


def answer_of(response: dict) -> dict:
    """The model's JSON answer. Raises if the response is not what the schema promised."""
    candidates = response["candidates"]
    text = candidates[0]["content"]["parts"][0]["text"]
    return json.loads(text)


def analyse_chunk(key: str, model: str, url: str, index: int, start: int, end: int, raw_dir: Path) -> dict:
    raw_path = raw_dir / f"chunk-{index:04d}.json"
    if raw_path.is_file():
        response = json.loads(raw_path.read_text(encoding="utf-8"))
    else:
        response = call(key, model, request_body(url, start, end))
        raw_path.write_text(json.dumps(response, indent=2, ensure_ascii=False), encoding="utf-8")
    answer = answer_of(response)
    return {
        "index": index,
        "startSeconds": start,
        "durationSeconds": end - start,
        "speedKph": float(answer["speed_kph"]),
        "inclinePercent": float(answer["incline_percent"]),
        "confidence": float(answer["confidence"]),
        "terrain": answer["terrain"],
        "note": answer["note"],
        "rawText": json.dumps(answer, ensure_ascii=False),
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", required=True, help="a public YouTube video address")
    ap.add_argument("--duration-seconds", type=int, required=True, help="length of the video")
    ap.add_argument("--out", required=True, help="folder for analysis.json and raw/")
    ap.add_argument("--model", default="gemini-3.5-flash",
                    help="a Gemini model that accepts video; check the current name in Google AI Studio")
    ap.add_argument("--chunk", type=int, default=30, help="seconds per step")
    ap.add_argument("--limit", type=int, default=0, help="only the first N chunks, to try it cheaply")
    ap.add_argument("--workers", type=int, default=8, help="requests in flight at once")
    ap.add_argument("--dry-run", action="store_true", help="print the first request and stop")
    args = ap.parse_args()

    windows = [(i, s, min(s + args.chunk, args.duration_seconds))
               for i, s in enumerate(range(0, args.duration_seconds, args.chunk))]
    if args.limit:
        windows = windows[: args.limit]

    if args.dry_run:
        index, start, end = windows[0]
        print(json.dumps(request_body(args.url, start, end), indent=2))
        print(f"\n{len(windows)} requests would be made to model {args.model}")
        return 0

    key = load_key()
    out = Path(args.out)
    raw_dir = out / "raw-gemini"
    raw_dir.mkdir(parents=True, exist_ok=True)

    records: list[dict] = []
    started = time.time()
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(analyse_chunk, key, args.model, args.url, i, s, e, raw_dir): i
                   for i, s, e in windows}
        for done, future in enumerate(as_completed(futures), start=1):
            record = future.result()  # raises, and stops the run, if a chunk failed
            records.append(record)
            print(f"[{done}/{len(windows)}] {time.time() - started:5.0f}s  chunk {record['index']:04d}: "
                  f"speed={record['speedKph']} incline={record['inclinePercent']} "
                  f"conf={record['confidence']} terrain={record['terrain']}", flush=True)

    records.sort(key=lambda r: r["index"])
    out_path = out / "analysis.json"
    out_path.write_text(json.dumps(records, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\n{len(records)} chunks analysed -> {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
