#!/usr/bin/env python3
"""Cut a running video into analysis chunks.

The chunks are the unit of work for the video-understanding step: each one is a short clip with a
known start and end time, so the answer the model gives for it can be placed on a timeline and
turned into program steps.

Every chunk is re-encoded: no audio (it carries nothing the program needs), scaled to 720p on the
long side, and compressed hard enough to upload quickly.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys


def probe_duration(ffmpeg: str, path: str) -> float:
    out = subprocess.run(
        [ffmpeg, "-hide_banner", "-i", path],
        capture_output=True,
        text=True,
        errors="replace",
    ).stderr
    for line in out.splitlines():
        if "Duration:" in line:
            stamp = line.split("Duration:")[1].split(",")[0].strip()
            hours, minutes, seconds = stamp.split(":")
            return int(hours) * 3600 + int(minutes) * 60 + float(seconds)
    return 0.0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--video", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--chunk", type=int, default=30, help="chunk length in seconds")
    ap.add_argument("--scale", default="1280:720")
    ap.add_argument("--crf", type=int, default=28)
    ap.add_argument("--ffmpeg", default="ffmpeg")
    args = ap.parse_args()

    if not os.path.isfile(args.video):
        print(f"no such video: {args.video}", file=sys.stderr)
        return 1
    if not os.path.isfile(args.ffmpeg):
        print(f"no such ffmpeg: {args.ffmpeg}", file=sys.stderr)
        return 1

    os.makedirs(args.out, exist_ok=True)
    total = probe_duration(args.ffmpeg, args.video)
    if total <= 0:
        print("could not read the video duration", file=sys.stderr)
        return 1

    chunks = []
    index = 0
    start = 0.0
    while start < total:
        length = min(args.chunk, total - start)
        name = f"chunk-{index:04d}.mp4"
        target = os.path.join(args.out, name)
        cmd = [
            args.ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
            "-ss", f"{start:.3f}", "-i", args.video,
            "-t", f"{length:.3f}",
            "-vf", f"scale={args.scale}:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", str(args.crf),
            "-an", "-movflags", "+faststart",
            target,
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
        if result.returncode != 0 or not os.path.isfile(target):
            print(f"chunk {index} failed:\n{result.stderr[:500]}", file=sys.stderr)
            return 1
        chunks.append(
            {
                "index": index,
                "file": name,
                "startSeconds": round(start, 3),
                "durationSeconds": round(length, 3),
                "sizeBytes": os.path.getsize(target),
            }
        )
        print(f"  chunk {index:04d}  {start:8.1f}s -> {start + length:8.1f}s  "
              f"{os.path.getsize(target) / 1e6:5.1f} MB", flush=True)
        index += 1
        start += length

    manifest = {
        "video": os.path.basename(args.video),
        "videoSeconds": round(total, 3),
        "chunkSeconds": args.chunk,
        "chunks": chunks,
    }
    path = os.path.join(args.out, "manifest.json")
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2)
    print(f"\n{len(chunks)} chunks, manifest at {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
