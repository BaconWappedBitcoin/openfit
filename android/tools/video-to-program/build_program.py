#!/usr/bin/env python3
"""Turn what the video model said about each chunk into a treadmill program.

The analysis gives one speed and one incline per chunk. This turns that into the program's own
shape: a list of steps, each with a speed, an incline and a duration, in the format the app and
the protocol library already read.

Three things are done to the numbers before they are written:

  * outliers are removed. A single chunk that disagrees with both its neighbours is replaced by
    the average of the two, because one misread frame should not become a step in the program.
  * the numbers are held inside the machine's limits, so the program cannot ask the treadmill for
    something the machine will refuse.
  * neighbouring chunks that agree are merged into one step, so the program has a handful of
    steps rather than one per chunk, and the runner is not handed a new number every thirty
    seconds.
"""

from __future__ import annotations

import argparse
import json
import os
import statistics
import sys


def median(values: list[float]) -> float:
    return statistics.median(values)


def smooth(records: list[dict]) -> list[dict]:
    """Replace a chunk that disagrees with both of its neighbours by their average."""
    out = [dict(r) for r in records]
    for i in range(1, len(out) - 1):
        before, here, after = out[i - 1], out[i], out[i + 1]
        if before["speedKph"] is None or here["speedKph"] is None or after["speedKph"] is None:
            continue
        if before["inclinePercent"] is None or here["inclinePercent"] is None or after["inclinePercent"] is None:
            continue
        speed_gap = abs(here["speedKph"] - before["speedKph"]) + abs(here["speedKph"] - after["speedKph"])
        incline_gap = abs(here["inclinePercent"] - before["inclinePercent"]) + abs(here["inclinePercent"] - after["inclinePercent"])
        if speed_gap > 3.0 and incline_gap > 6.0:
            here["speedKph"] = (before["speedKph"] + after["speedKph"]) / 2
            here["inclinePercent"] = (before["inclinePercent"] + after["inclinePercent"]) / 2
            here["smoothed"] = True
    return out


def moving_average(records: list[dict], window: int) -> list[dict]:
    """Take the running mean of the series over a window of chunks.

    A model reading a picture moves its answer about by a few tenths from one clip to the next, and
    a treadmill that is told to change speed every thirty seconds by two tenths is harder to run on
    than the ground is. The mean keeps the shape of the run and drops the jitter.
    """
    if window <= 1:
        return records
    out = []
    half = window // 2
    for index, record in enumerate(records):
        low = max(0, index - half)
        high = min(len(records), index + half + 1)
        window_records = records[low:high]
        speeds = [r["speedKph"] for r in window_records if r.get("speedKph") is not None]
        inclines = [r["inclinePercent"] for r in window_records if r.get("inclinePercent") is not None]
        smoothed = dict(record)
        if speeds:
            smoothed["speedKph"] = sum(speeds) / len(speeds)
        if inclines:
            smoothed["inclinePercent"] = sum(inclines) / len(inclines)
        out.append(smoothed)
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--analysis", required=True)
    ap.add_argument("--out", required=True, help="the program JSON to write")
    ap.add_argument("--name", default=None)
    ap.add_argument("--max-speed", type=float, default=12.0)
    ap.add_argument("--max-incline", type=float, default=10.0)
    ap.add_argument("--min-incline", type=float, default=-5.0)
    ap.add_argument("--min-speed", type=float, default=1.6,
                    help="the slowest speed the machine is asked to hold, in km/h")
    ap.add_argument("--smooth", type=int, default=3,
                    help="window of chunks the running mean is taken over")
    ap.add_argument("--merge-speed", type=float, default=0.4, help="kph difference that still counts as the same")
    ap.add_argument("--merge-incline", type=float, default=0.8, help="percent difference that still counts as the same")
    ap.add_argument("--report", default=None)
    args = ap.parse_args()

    with open(args.analysis, encoding="utf-8") as handle:
        records = json.load(handle)

    speeds = [r["speedKph"] for r in records if r.get("speedKph") is not None]
    inclines = [r["inclinePercent"] for r in records if r.get("inclinePercent") is not None]
    if not speeds:
        print("no speeds were read from the video", file=sys.stderr)
        return 1

    # A speed the model never actually reported is replaced by the median of the ones it did,
    # rather than by zero: a missing reading is not a stationary runner.
    fallback_speed = median(speeds)
    fallback_incline = median(inclines) if inclines else 0.0
    for record in records:
        if record.get("speedKph") is None:
            record["speedKph"] = fallback_speed
            record["speedFilled"] = True
        if record.get("inclinePercent") is None:
            record["inclinePercent"] = fallback_incline
            record["inclineFilled"] = True

    records = smooth(records)

    # The running mean, then the limits.
    records = moving_average(records, args.smooth)

    for record in records:
        record["speedKph"] = max(0.0, min(args.max_speed, record["speedKph"]))
        record["inclinePercent"] = max(args.min_incline, min(args.max_incline, record["inclinePercent"]))

    # A treadmill cannot be told to stand still: the machine's own minimum is above zero, and a
    # step below it is refused. A still moment in the video therefore becomes the slowest running
    # speed the machine can hold.
    raised = 0
    for record in records:
        if record["speedKph"] < args.min_speed:
            record["speedKph"] = args.min_speed
            record["raisedToMinimum"] = True
            raised += 1

    steps = []
    for record in records:
        speed = round(record["speedKph"] * 20) / 20      # to the nearest 0.05 km/h
        incline = round(record["inclinePercent"] * 2) / 2  # to the nearest 0.5 %
        duration = int(round(record["durationSeconds"]))
        if duration <= 0:
            continue
        if steps:
            last = steps[-1]
            if (abs(last["speedKph"] - speed) <= args.merge_speed
                    and abs(last["inclinePercent"] - incline) <= args.merge_incline):
                last["durationSeconds"] += duration
                continue
        steps.append({
            "speedKph": speed,
            "inclinePercent": incline,
            "durationSeconds": duration,
        })

    name = args.name or f"From video ({len(steps)} steps)"
    program = {"name": name, "steps": steps}
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(program, handle, indent=2)
        handle.write("\n")

    total = sum(s["durationSeconds"] for s in steps)
    print(f"{len(steps)} steps, {total} s, "
          f"speed {min(s['speedKph'] for s in steps):.2f}-{max(s['speedKph'] for s in steps):.2f} km/h, "
          f"incline {min(s['inclinePercent'] for s in steps):.1f}..{max(s['inclinePercent'] for s in steps):.1f} %")
    if raised:
        print(f"{raised} still moments raised to the machine's slowest speed ({args.min_speed} km/h)")
    print(f"written to {args.out}")

    if args.report:
        lines = [
            f"# {name}",
            "",
            f"Source video: {records[0].get('video', 'the downloaded video')}",
            f"Chunks analysed: {len(records)}",
            "",
            "| # | start | speed km/h | incline % | confidence | terrain | note |",
            "|---|---|---|---|---|---|---|",
        ]
        for record in records:
            lines.append(
                f"| {record['index']} | {record['startSeconds']:.0f}s | "
                f"{record['speedKph']:.2f} | {record['inclinePercent']:.1f} | "
                f"{record.get('confidence')} | {record.get('terrain')} | {record.get('note')} |"
            )
        with open(args.report, "w", encoding="utf-8") as handle:
            handle.write("\n".join(lines) + "\n")
        print(f"report written to {args.report}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
