# Building a programme out of a running video

This is how a freely published running video becomes a treadmill programme: the video is the
scenery, the programme is the machine.

Nothing here touches protected media. The videos are published freely on YouTube and are downloaded
with `yt-dlp`; the programme that comes out is written by us and is a plain JSON file.

## The four steps

```console
$ python tools/video-to-program/segment.py \
    --video android/dist/generated/media/86bVEtj8W1c.mp4 \
    --out  android/dist/generated/chunks \
    --chunk 30

$ python tools/video-to-program/analyze.py \
    --chunks android/dist/generated/chunks

$ python tools/video-to-program/build_program.py \
    --analysis android/dist/generated/chunks/analysis.json \
    --out      android/dist/generated/programs/interlaken-virtual-run.json \
    --name     "Virtual Run 4k - Interlaken" \
    --report   android/dist/generated/interlaken-report.md
```

`segment.py` cuts the video into thirty-second chunks and writes `manifest.json`.
`analyze.py` asks a video model what the runner is doing in each chunk and writes `analysis.json`
next to the raw answers. `build_program.py` writes the programme and a report that shows every
chunk's numbers, its confidence and the model's own note.

## Putting it on the phone

The programme is the same format the app already reads, so it is a file to copy in:

```console
$ adb push interlaken-virtual-run.json /data/local/tmp/
$ adb shell "run-as com.ofit.app sh -c 'cat /data/local/tmp/interlaken-virtual-run.json > files/workouts/interlaken-virtual-run.json'"
```

The video is picked in the app with **Add a video from this phone**. It is the user's own file, and
the app plays it beside the running programme.

## What the numbers are

They are the model's reading of the picture, not a measurement: speed from footfall cadence times
stride length, incline from the visible slope of the ground. Each answer carries its own
confidence, and the report keeps it, so a shaky reading can be seen for what it is.

The programme that comes out is held inside the machine's own ceilings — 12 km/h, +10 % and
−5 % incline — so it can never ask the treadmill for something the treadmill refuses.

## The model

`connector__matrix__videos_understand`, in batches of ten videos. There is no Gemini in this
environment; the MiniMax video understanding does the same job.

If the connector is not reachable, the pipeline fails at the upload step and says so; the chunks
stay on disk and the analysis can be run again without cutting the video again.
