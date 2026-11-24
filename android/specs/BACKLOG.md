# OpenFit backlog, by spec lane

Updated 2026-11-23. One writer works the lanes top to bottom (the project is one folder, not yet a git
repo, so parallel writers would overwrite each other). Items marked **[you]** need the user at the treadmill
or a decision; everything else is worked on without them. An hourly check in the working session picks up
the next open item.

Status: `[ ]` open · `[~]` in progress · `[x]` done (date) · `[you]` waiting on the user.

## Lane 0 — Needs you (physical tests and decisions)

- [you] Confirm by eye that the belt stops on Pause (console accepts WorkoutMode PAUSE; not yet watched).
- [you] Strap retest with the wider (unfiltered) scan; tell us the strap model and whether it is paired
  to a phone or watch.
- [you] Hold zone (spec 003) live test: zone 2 from 3 km/h; strap off → hold; touch speed − → off.
- [x] (2026-11-23) Key rotation waived by the user (no keys are in the repo; scan clean).
- [x] (2026-11-23) Decided: no third-party catalogue import or reverse-engineering notes in the public repo.
- [x] (2026-11-23) Private repo BaconWappedBitcoin/openfit created and pushed (685caec).
- [you] Make the repo public when ready.
- [you] Spec 007 live check with the console awake: open a workout, leave, wait 60 s → link drops and the
  console buttons work; with the workout screen open, start the belt by hand → OpenFit disconnects and
  sends nothing; during an OpenFit workout press the console STOP → no further belt writes.

## Lane 1 — Bugs (spec 001/002 behaviour)

- [x] (2026-11-23, backstop stop after 22 s) Strap scan once showed "Looking…" past its 20 s window (seen once, not reproduced). Add a hard stop.
- [x] (2026-11-23, checked on tablet) VideoSearch Back returns to My workouts even when opened from Browse; return to where it came from.
- [x] (2026-11-23: backend tidyTitle already cuts at a word; only names saved before it are affected) Browse hero title can be cut mid-word ("…Switzerland - Vir"): imported names are truncated; show the
  full name or trim at a word.
- [x] (2026-11-23) Portrait HUD ignores Swap sides.
- [x] (2026-11-23, now "Step 1 of 4 · 0:22 / 0:45") HUD "Step n of m · time" uses whole-workout time; show step time.
- [x] (2026-11-23, checkbox in the delete dialog) Profile delete leaves its prefs/history files on disk (by design); add "delete data too".
- [x] (2026-11-23, speed card shows ♥ COACH) Coach changes speed via overrideSpeed, so the speed card shows MANUAL; show "♥ coach" instead.

- [ ] Before Start, the workout screen's chart chip says "Last step" and "remaining" shows 0:00; show the first step's next and the total time.

- [x] (2026-11-23) App locked to landscape (sensorLandscape).

- [x] (2026-11-23, seen on tablet) My workouts redesigned: card grid, search, sort, delete confirmation; incline drawings stay in frame.

## Lane 2 — Home and navigation (spec 004, to write)

- [x] (2026-11-23, seen on tablet) Home dashboard: treadmill status card, "Start where you left off", this week's totals, quick links;
  manual control (sliders, Start/Stop) moved to its own "Manual control" screen in the menu.
- [x] (2026-11-23, seen on tablet) Hamburger menu on every top-level screen (app-level drawer + ☰ in each header; off on the workout and profile screens).
- [x] (2026-11-23) Quick start → see Lane 7 P3.

## Lane 3 — Workouts (spec 005, to write)

- [x] (2026-11-23, seen on tablet) Visual workout editor under the video: tap to seek, drag levels, drag step edges, split, delete, undo; "Make it myself" from video search (no AI).
- [ ] Editor: zoom the timeline for long workouts with many steps; local video files in the editor.
- [ ] Templates: intervals, hill climb, warm-up/cool-down, steady.
- [ ] Duplicate / rename a workout.

## Lane 4 — History and stats (spec 006, to write)

- [ ] Workout detail: speed, incline and heart-rate charts over time.
- [ ] Personal bests (longest, farthest, most climb) and a weekly streak.
- [ ] Export a workout as TCX/GPX-less TCX for Strava (no GPS).

## Lane 5 — Heart rate (spec 002 §3b, spec 003)

- [x] Strap support, zones, HR tile, avg/max (2026-11-23)
- [x] Hold zone built with unit tests (2026-11-23), live test in Lane 0
- [x] (2026-11-23) Hold zone aggressiveness: Gentle / Normal / Strong + warm-up hold (spec 003 §6).
- [ ] Zone colour bar on the HUD chart (spec 002 H5 optional).

## Lane 7 — Roadmap features (spec 008, in its order)

- [x] (2026-11-23, QuickStartTest 3/3, seen on tablet) P3 Quick start with a goal (time/distance/kcal) and optional warm-up / cool-down (replaces Lane 2's quick start).
- [ ] P15 Post-workout summary sheet with a rating.
- [ ] P4 Resume an interrupted workout.
- [ ] P16 Favourites + Favourites row on Browse.
- [ ] P2 Filters on Browse and My workouts.
- [ ] P18 "For you" row from History.
- [ ] P13 Achievements and a monthly distance challenge.
- [ ] P11/P12 Training plans with a schedule and reminders.
- [ ] P17 Health Connect sync + TCX export.
- [ ] P7 Per-profile intensity scale (SmartAdjust-like).
- [ ] P21 Body weight and own calorie estimate.
- [ ] P6 GPX route import → incline workout.
- [ ] P19 Video volume / mute on the HUD.

## Lane 8 — Safety (spec 007)

- [x] (2026-11-23) Command gate: belt commands only during OpenFit's own run; console STOP wins; manual use refused.
- [x] (2026-11-23) Hands off when idle: release + disconnect after 60 s (5 min on the workout screen); disconnect at once on manual use.
- [x] (2026-11-23) Connect on demand (not at app start); "Connect now" on Home.

## Lane 6 — Open-source release (spec 009)

- [ ] Split handoff.md into public DEVELOPMENT.md + private git-ignored handoff.
- [x] (2026-11-23, app + backend built, backend not deployed) Bring your own Gemini key (menu → Your Gemini key).
- [ ] "Backend URL" setting so self-hosters point OpenFit at their own backend (all Pro features free).
- [ ] Free daily allowance per install on the hosted analyze endpoint (until Pro exists).
- [ ] Pro: payment method decision [you], then Pro key check on api/analyze.

- [x] GPL-3.0 LICENSE, README, .gitignore (2026-11-23)
- [ ] CONTRIBUTING.md and a short "adding another treadmill" guide (what was needed for the I_TL console).
- [ ] Strip personal/session notes from handoff.md into a public DEVELOPMENT.md; keep handoff private.
- [x] (2026-11-23) Removed comments citing third-party decompiled code; neutral wording throughout.
