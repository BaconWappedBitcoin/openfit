#!/usr/bin/env python3
"""Ask the video model what the runner is doing in each chunk of a video.

The chunks made by segment.py are uploaded one by one, then asked about in batches of ten through
the connector's video understanding tool. Each answer is a small JSON object: the speed, the
incline, the terrain, how sure the model is, and a note on what it saw.

The raw answers are kept next to the parsed ones, because the point of the exercise is to see how
often the model is right, and that can only be judged from what it actually said.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys

PROMPT = """You are analysing a clip of a running video so that a treadmill program can be built from it.
The clip is {seconds:.0f} seconds long and shows a runner, usually filmed from a chest or head
camera on the runner themselves, moving along a path.

Answer with ONE JSON object and nothing else, no markdown, no explanation outside the object:
{{
  "speed_kph": <number>,
  "incline_percent": <number>,
  "terrain": "<flat|rolling|uphill|downhill|stairs|trail|road|water|other>",
  "confidence": <number between 0 and 1>,
  "note": "<one short sentence on what you saw>"
}}

How to judge the two numbers:
- speed_kph is the runner's ground speed in kilometres per hour. Estimate it from the footfall
  cadence multiplied by the stride length: an adult runner's stride is roughly 1.0 to 1.3 metres,
  so use 1.1 metres when you are unsure. When the camera is on the runner, each footfall makes one
  downward bob, so counting the bobs over ten seconds gives roughly (bobs per 10 s) * 12 steps per
  minute. If nothing in the clip allows a judgement, give your best estimate and set the
  confidence low.
- incline_percent is the gradient of the ground under the runner in percent, where 10 means a ten
  per cent uphill slope and -10 a ten per cent downhill slope. Judge it from the visible slope of
  the path against the horizon and from the runner's posture.
- confidence is how sure you are about the two numbers together. Be honest: a low confidence is
  more useful than a confident guess."""


def run(args: list[str]) -> tuple[int, str]:
    # mcode-tools is a .cmd shim on Windows, so the command is run through the shell.
    result = subprocess.run(
        subprocess.list2cmdline(args),
        capture_output=True,
        text=True,
        errors="replace",
        shell=True,
    )
    return result.returncode, result.stdout


def upload(path: str) -> str:
    code, out = run(["mcode-tools", "upload_temp_url", path])
    if code != 0:
        raise RuntimeError(f"upload failed for {path}: {out[:300]}")
    try:
        data = json.loads(out)
    except json.JSONDecodeError:
        # The CLI may print a preamble; take the last JSON object it printed.
        match = re.search(r"\{.*\}", out, re.S)
        if not match:
            raise RuntimeError(f"upload gave no JSON for {path}: {out[:300]}")
        data = json.loads(match.group(0))
    url = data.get("temp_url") or data.get("url")
    if not url:
        raise RuntimeError(f"upload gave no temp_url for {path}: {out[:300]}")
    return url


def ask(batch: list[dict]) -> object:
    payload = {"video_info": batch}
    path = os.path.join(os.environ.get("TEMP", "."), "ofit-re", "ask.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle)
    code, out = run(["mcode-tools", "connector", "call",
                     "connector__matrix__videos_understand", "--args-file", path])
    if code != 0:
        raise RuntimeError(f"the video tool failed: {out[:500]}")
    try:
        return json.loads(out)
    except json.JSONDecodeError:
        match = re.search(r"(\{.*\}|\[.*\])", out, re.S)
        if not match:
            raise RuntimeError(f"the video tool gave no JSON: {out[:500]}")
        return json.loads(match.group(0))


def answers_of(payload: object) -> list:
    """Pull the list of per-video answers out of whatever shape the tool returned."""
    if isinstance(payload, list):
        return payload
    if isinstance(payload, dict):
        for key in ("videos", "results", "answers", "video_list", "outputs", "data"):
            value = payload.get(key)
            if isinstance(value, list):
                return value
    return [payload]


def text_of(answer: object) -> str:
    if isinstance(answer, str):
        return answer
    if isinstance(answer, dict):
        for key in ("description", "text", "answer", "content", "result", "output", "message"):
            value = answer.get(key)
            if isinstance(value, str):
                return value
        return json.dumps(answer, ensure_ascii=False)
    return str(answer)


def numbers_from(text: str) -> dict:
    """Read the fields out of an answer, whatever shape the model wrote them in."""
    def grab(key: str) -> float | None:
        match = re.search(rf'"{key}"\s*:\s*(-?\d+(?:\.\d+)?)', text)
        if match:
            return float(match.group(1))
        match = re.search(rf"'{key}'\s*:\s*(-?\d+(?:\.\d+)?)", text)
        return float(match.group(1)) if match else None

    result = {"speedKph": grab("speed_kph"), "inclinePercent": grab("incline_percent"),
              "confidence": grab("confidence")}
    terrain = re.search(r'"terrain"\s*:\s*"([^"]*)"', text)
    result["terrain"] = terrain.group(1) if terrain else None
    note = re.search(r'"note"\s*:\s*"([^"]*)"', text)
    result["note"] = note.group(1) if note else None
    return result


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--chunks", required=True, help="the folder segment.py wrote")
    ap.add_argument("--limit", type=int, default=0, help="only the first N chunks")
    ap.add_argument("--batch", type=int, default=10)
    args = ap.parse_args()

    manifest_path = os.path.join(args.chunks, "manifest.json")
    if os.path.isfile(manifest_path):
        with open(manifest_path, encoding="utf-8") as handle:
            manifest = json.load(handle)
        chunks = manifest["chunks"]
    else:
        # The cutting may still be running: work from the files that are already there.
        chunks = []
        for name in sorted(os.listdir(args.chunks)):
            if not (name.startswith("chunk-") and name.endswith(".mp4")):
                continue
            index = int(name[len("chunk-"):-len(".mp4")])
            chunks.append({
                "index": index,
                "file": name,
                "startSeconds": index * 30,
                "durationSeconds": 30,
                "sizeBytes": os.path.getsize(os.path.join(args.chunks, name)),
            })
    if args.limit:
        chunks = chunks[: args.limit]

    raw_dir = os.path.join(args.chunks, "raw")
    os.makedirs(raw_dir, exist_ok=True)

    records = []
    for start in range(0, len(chunks), args.batch):
        group = chunks[start : start + args.batch]
        print(f"uploading {len(group)} chunks "
              f"({group[0]['index']}..{group[-1]['index']})", flush=True)
        batch = []
        for chunk in group:
            path = os.path.join(args.chunks, chunk["file"])
            url = upload(path)
            print(f"  {chunk['file']} uploaded", flush=True)
            batch.append({
                "url": url,
                "mime_type": "video/mp4",
                "prompt": PROMPT.format(seconds=chunk["durationSeconds"]),
            })
        payload = ask(batch)
        raw_path = os.path.join(raw_dir, f"batch-{group[0]['index']:04d}.json")
        with open(raw_path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, indent=2, ensure_ascii=False)
        answers = answers_of(payload)
        print(f"  answered: {len(answers)} (raw in {raw_path})", flush=True)
        for offset, chunk in enumerate(group):
            text = text_of(answers[offset]) if offset < len(answers) else ""
            parsed = numbers_from(text)
            parsed.update({
                "index": chunk["index"],
                "startSeconds": chunk["startSeconds"],
                "durationSeconds": chunk["durationSeconds"],
                "rawText": text[:2000],
            })
            records.append(parsed)
            print(f"    chunk {chunk['index']:04d}: speed={parsed['speedKph']} "
                  f"incline={parsed['inclinePercent']} conf={parsed['confidence']} "
                  f"terrain={parsed['terrain']}", flush=True)
        out_path = os.path.join(args.chunks, "analysis.json")
        with open(out_path, "w", encoding="utf-8") as handle:
            json.dump(records, handle, indent=2, ensure_ascii=False)

    print(f"\n{len(records)} chunks analysed -> {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
