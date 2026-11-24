# OpenFit

An open-source Android app that controls compatible treadmills over Bluetooth, from a tablet.

OpenFit connects to the treadmill console over Bluetooth Low Energy, runs speed-and-incline workouts
(including ones made from YouTube scenery videos), and shows a full-screen workout display over the video.
It was built against a NordicTrack T 6.5 (console `I_TL`); other iFit consoles may need work.

## What it does

- Connects to the console over Bluetooth (no pairing) only while a workout runs, and lets go when idle.
- Runs workouts step by step, with manual speed and incline overrides kept inside the machine's own limits
  and your ceilings. Stop and Emergency Stop are always one tap away.
- Workout screen: full-screen video, choosable stats, incline/speed cards, profile chart.
- Heart rate from any standard Bluetooth strap or watch, with zones.
- Profiles, themes, workout history, a browse screen, and a community workout library.
- Turns a YouTube walking or running video into a workout (needs the backend in `backend/`).

## Use your own Gemini key (optional, free)

Making a workout from a YouTube video uses Google's Gemini model. The shared service has a daily limit;
with your own key there is no OpenFit limit and Google's free tier covers normal use.

1. Open **[Google AI Studio → API keys](https://aistudio.google.com/app/apikey)** and sign in with your
   Google account.
2. Tap **Create API key** (pick the default project if asked) and copy the key. It starts with `AIza`.
3. In OpenFit, open the menu → **Your Gemini key**, paste it and tap **Save key**.

The key is stored encrypted on the tablet and sent only with video-to-workout requests; the server uses it
for that request and never stores or logs it. Keep it private, and delete it in AI Studio any time.

## Repository layout

| Path | What it is |
|------|------------|
| `android/protocol` | The console protocol as a plain JVM library, with tests. |
| `android/transport` | Android Bluetooth links (treadmill, heart-rate strap). |
| `android/app` | The Android app (Jetpack Compose). |
| `android/specs` | Feature specs. |
| `backend/` | Vercel functions for video search, video-to-workout and the community library. |

## Building

`android/` is a Gradle project: `gradle :protocol:test :app:assembleDebug`. The backend needs its own
keys in `backend/.env.local` (see `.env.example`); none are included.

## Safety

> **Read [DISCLAIMER.md](DISCLAIMER.md) before using OpenFit.** You use it at your own risk; it is not a
> medical device and is not affiliated with iFit or any manufacturer.

A treadmill can hurt you. OpenFit checks every command against the machine's limits and your ceilings, but
software fails: always use the safety key and know where the console's own STOP button is. You use this at
your own risk; see the warranty disclaimer in the licence.

## Not affiliated, and trademarks

OpenFit is an independent project. It is not made, endorsed or supported by iFit, NordicTrack or ProForm.
Those names are trademarks of their owners and are used here only to say which machines OpenFit works with.
No iFit code, apps or content are included in this repository.

iFIT, NordicTrack and ProForm are trademarks of their owner; Bluetooth® is a registered trademark of
Bluetooth SIG, Inc.; YouTube and Android are trademarks of Google LLC; other names are trademarks of
their respective owners. They are used only to describe compatibility. See [DISCLAIMER.md](DISCLAIMER.md).

## Licence

OpenFit is free software: you can redistribute it and/or modify it under the terms of the GNU General
Public License as published by the Free Software Foundation, version 3 or (at your option) any later
version. See [LICENSE](LICENSE).

Layout ideas for the profile picker and the browse screen were taken from
[NuvioMobile](https://github.com/NuvioMedia/NuvioMobile) (GPL-3.0); no code was copied.
