# Developing OpenFit

How the code is laid out, how to build and test it, and the rules it keeps. For contributing, see
[CONTRIBUTING.md](CONTRIBUTING.md); for supporting another treadmill, see
[android/docs/adding-a-treadmill.md](android/docs/adding-a-treadmill.md).

## Layout

| Path | What it is |
|------|------------|
| `android/protocol` | Pure JVM library (no Android): the console protocol, the workout engine, safety limits, and every piece of app logic that can be tested on a desktop (plans, achievements, intensity, GPX, Health Connect mapping, ...). JUnit 5. |
| `android/transport` | Android Bluetooth Low Energy: the console link (`GattClient`, `BleTransport`), the scanner, and heart-rate straps (`HeartRateMonitor`, standard service `0x180D`). |
| `android/app` | The Jetpack Compose app. `TreadmillController` owns the long-lived pieces; screens live by feature (`workout/`, `history/`, `library/`, `browse/`, `heart/`, `profile/`, `settings/`, `ui/`). |
| `android/app/src/main/assets/coach` | The three.js virtual coach, served to a WebView at `https://ofit.local/coach/`. |
| `android/docs` | Protocol notes, references, how video-to-workout works. |
| `android/specs` | Feature specs and `BACKLOG.md`. |
| `backend/` | Optional Vercel functions (TypeScript): YouTube search, video-to-workout with Gemini, coach facts and landmarks, and the community library with automatic moderation. |
| `community/` | Shared workouts and cached AI results as JSON. The app reads it straight from GitHub when no server is used. |

## Build and test

Android (a recent JDK and the Android SDK, compileSdk 36):

```sh
cd android
gradle :protocol:test :app:testDebugUnitTest :app:assembleDebug
```

The APK is in `app/build/outputs/apk/debug/` (or under the system temp folder's `ofit-build/`
when the workspace is on a network drive; see `settings.gradle.kts`). Install it with
`adb install -r app-debug.apk`.

Backend (Node 20+):

```sh
cd backend
npm install
npm test
npm run typecheck
npx vercel dev        # local server; keys in backend/.env.local, see .env.example
```

Point the app at your own backend in **Keys & services → Server address**.

## Rules the code keeps

- **Safety first.** Every speed and incline command is checked against the machine's own limits
  (read from the console, never assumed) and the user's ceilings in `SafetyLimits`, in the protocol
  layer, so no screen can send more. Stop and E-STOP always work.
- **Hands off when idle** (spec 007). OpenFit only sends belt commands during a run it started
  (`ControlGate`), connects for a workout and lets go after; the console's own buttons keep working.
- **Logic in `protocol/`, with tests.** Anything that is not Android UI or Bluetooth goes in the JVM
  library with unit tests. `org.json` is not available in the app's JVM unit tests; keep JSON
  handling in `protocol/` or write it by hand.
- **Never reinstall while the belt moves.** Killing the app mid-workout leaves the belt running at
  its last speed (the console has no link-loss timeout). End the workout first.
- **No secrets in the repo.** Keys live in `backend/.env.local` or in Vercel; on the tablet they are
  encrypted with the Android Keystore. Users' own keys never go to our server except as a header
  for their own requests.
- **Wording.** OpenFit is independent. Mention iFit, NordicTrack and ProForm only to describe
  compatibility (see `DISCLAIMER.md`).

## Where data lives on the tablet

| What | Where |
|------|-------|
| Saved workouts | `files/` as JSON, one file per workout |
| History | `files/history[-profile].json` |
| Settings, profiles, plans, intensity, favourites | SharedPreferences (`ofit_*`) |
| Keys | `ofit_secrets`, AES-GCM with a Keystore key (not in backups) |

Android Auto Backup and the manual backup (menu → Save a backup) cover everything but the keys.
