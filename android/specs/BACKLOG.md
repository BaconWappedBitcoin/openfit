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

- [x] (2026-11-23, seen on tablet) Every tap on the workout screen did nothing (Start, ⋯, Hide) when the side menu had
  been left open: on that screen the menu draws nothing but its scrim still caught taps. The menu now shuts on every
  screen change. This was the real cause of "No treadmill Start does nothing".

- [x] (2026-11-23, seen on tablet: 3:00 remaining, Step 1 of 4, next values) Before Start, the workout screen's chart chip says "Last step" and "remaining" shows 0:00; show the first step's next and the total time.

- [x] (2026-11-23) App locked to landscape (sensorLandscape).

- [x] (2026-11-23, seen on tablet) My workouts redesigned: card grid, search, sort, delete confirmation; incline drawings stay in frame.

- [x] (2026-11-24, seen on tablet: 147 in the log, no banner on Home) Opening a workout with the treadmill off leaves "Could not connect … (Bluetooth status 147)" on Home after the workout (seen 2026-11-24 after a no-treadmill run); with No treadmill in use it should not try, or say it quietly.

## Lane 1b — User walkthrough feedback (2026-11-24), in work order

- [x] Coach designer: woman/man, outfit, top and bottom colours, skin tone, hair, with a live preview; from the side menu and from the coach menu during a workout (user idea 2026-11-24; installed, checked on the tablet).
- [x] (2026-11-24) Race kit's number bib is a clean printed card (kit-colour OPENFIT band, number 42, thin edge) on a skinned layer over the back, so it bends with her as she runs; painted bib and chest band removed (installed, seen on the tablet mid-jog).
- [x] Home: the This week, Streak and Treadmill cards are the same size (user request 2026-11-24; installed, seen on the tablet).
- [x] Coach outfit: the race kit, picked by the user from the mockups (2026-11-24; installed).
- [x] A speed or incline set by hand holds through step changes until Rejoin program is tapped; Coach pace changes only how fast the legs move, not walk vs run (user requests 2026-11-24; installed).
- [x] Coach wears real garments (shirt with collar, cuffs and a reflective back stripe; shorts with a waistband; shoes with coloured soles) with thickness, not painted on (user request 2026-11-24; installed).
- [x] Coach menu goes see-through while dragging Coach size or pace, so the coach shows (user request 2026-11-24; installed).
- [x] The runner's music pauses when the workout stops (end of cool-down, End, E-STOP) (user request 2026-11-24; installed).
- [x] Coach talks more: a check-in every minute, half of them facts, up to 20 facts per place (user request 2026-11-24; backend deployed; installed).
- [x] Starting music no longer leaves the video paused (2 s watchdog) (user report 2026-11-24; installed).
- [ ] [you] Video showing about one frame every 5 s after music started (user report 2026-11-24; not reproduced: measured page 117 fps, video advancing; on the 2026-11-24 test run music start left the video playing. Tell us if it happens again).
- [x] Coach size slider (60 % to 160 %) in the coach menu, remembered, feet kept on the bottom edge (user request 2026-11-24; installed).
- [x] 3-2-1 countdown on Start: the belt spins up while the video and the workout clock wait (user request 2026-11-24; installed).
- [x] End cool-down stops at once, no "are you sure" (user request 2026-11-24; installed).
- [x] Coach pace slider (0.5x to 2x) in the coach menu, remembered per workout, to match the coach to videos whose pace was read wrong; coach feet at the bottom edge of the screen (user requests 2026-11-24; installed).
- [x] Workout screen: Menu before Back like every other screen; tapping the video's play button before Start starts the workout; coach head keeps the body skin tone with hair only at the back (user reports 2026-11-24; installed).
- [x] Coach keeps pace with the video's scenery and steps at real cadences; coaches have hair round the back of the head, the woman a ponytail (user reports 2026-11-24; installed).
- [x] Cool-down shows a drawn path at dusk (hills, trees, path markings rolling at belt speed) instead of the video frozen on its last frame; the coach walks on it (user request 2026-11-24; installed; not yet seen in a cool-down).
- [x] Match my speed on by default; coach lines never talk over each other (queued, and quiet for the 26 s before a change warning); coach 20 % bigger; walk-to-run blends over 1.5 s with eased stride speed and a margin at each gait change (user requests 2026-11-24; installed; coach size and gait not yet seen on a run).
- [x] Every workout rated Easy / Medium / Hard and Walk / Run / Run-walk (from its speeds, inclines and length, ACSM equations), shown on cards in My workouts, Browse and Community, with effort and type filters on all three (user request 2026-11-24; backend deployed; installed and seen on the tablet).
- [x] Coach names landmarks just before you pass them, with a fact (AI reads the video once at low detail); switching the coach between woman and man works without turning it off (user requests 2026-11-24; backend deployed; installed).
- [x] Coach speaks (optional, woman or man voice, music dips): changes coming up with speed, incline and how hard, time left, encouragement, and AI facts about the place in the video (user request 2026-11-24; backend deployed; not yet heard on a real run).
- [x] If OpenFit closes mid-workout and the belt keeps going, the next start offers to stop it (user report 2026-11-24; live check pending).
- [x] Stay connected to the treadmill for 5 idle minutes (was 60 s), so connecting from Devices outside a workout lasts (user request 2026-11-24). Still disconnects at once if the belt is run by hand from the console.
- [x] AI-made workouts ramp speed and incline smoothly in 10-second steps between readings, and carry the video's own pace (user request 2026-11-24; backend deployed 2026-11-24; real analysis not yet run).
- [x] Optional virtual coach running with you over the video (user idea 2026-11-24; first version done 2026-11-24): runner button on the workout bar (Off / woman / man, remembered); Quaternius CC0 character in the profile's theme colour as one suit, UAL1 walk/jog/sprint/idle by belt speed; short lines from CoachTalk (start, 10 s before hills and speed changes, halfway, last minute, cool-down, encouragement every 4 min). Seen on tablet idle and jogging (jog forced); not yet seen on a live run. Dressed (shirt, shorts, shoes) and running forward since 2026-11-24. Next idea: the coach's lines spoken aloud.
- [x] Video paused during the cool-down, and the ad hold no longer freezes the cool-down clock (user request 2026-11-24; live check pending).
- [x] Match my speed compares the belt to the workout's pace (the video's own pace from the AI analysis, else the workout's average), not each step (user request 2026-11-24; backend part needs deploying; live check pending).
- [x] About card with a link to the GitHub source and issues (user request 2026-11-24, seen on tablet).
- [x] mph / km/h setting lives in Profiles (Edit profile), not the menu (user request 2026-11-24, seen on tablet).
- [x] Heart rate in History kept as a 10-second average of every reading, so grip readings that come and go show up (user request 2026-11-24).
- [x] Starting a workout no longer leaves your music paused: OpenFit restarts it and the video plays muted (user report 2026-11-24; live check pending).
- [x] Workout charts: value labels on the side, time along the bottom (user request 2026-11-24, seen on tablet).
- [x] Start, Skip and End fixed beside E-STOP so they never move (user request 2026-11-24, seen on tablet).
- [x] One Sound button on the workout bar instead of separate music and video-volume buttons (user request 2026-11-24, seen on tablet).
- [x] Show the song playing (art, title, artist) on the workout bar and in the music panel; controls use the playing app's session (user request 2026-11-24; seen live 2026-11-24 after the user allowed notification access).
- [x] History heart-rate chart: Paris run saved one heart-rate sample though the strap read 115 avg / 131 max (seen 2026-11-24; done 2026-11-24: readings were intermittent, about 15 s in all; charts now break at missing readings and clock jumps, a lone reading is a dot, heart rate scales to its own range and says how often it was read).
- [x] 10-minute cool-down when a workout ends or is ended (belt slows to a walk, then stops); only E-STOP stops at once (user request 2026-11-24; built and unit-tested, live check pending).
- [x] Music panel: play/pause/skip sent to the music app opened from the panel, music volume slider and mute, labelled video sound slider (user report 2026-11-24; live check pending).
- [x] Workout video stuck / jumpy after starting music: the video now carries on muted when another app takes the audio, and starts muted when its sound is off (user report and fix 2026-11-24; needs a live check with music, new build to install).
- [x] Workout values never stop a start: steps outside the treadmill's range or the ceilings are fitted into it (user report 2026-11-24, done 2026-11-24; live start on the T 6.5 worked, 0 mph raised to 1.0, -0.5 % to 0 %).
Workout screen (bugs first)
- [x] (2026-11-24, seen on tablet; WorkoutSkipTest) Skip step does not skip ahead: the clock (and video) now jump to the next step; skipping while paused sends nothing until Resume.
- [x] (2026-11-24, seen on tablet) End workout stays on the video screen; it should go to a summary. End workout asks "Quit workout?" first.
- [x] (2026-11-24, seen on tablet) Android Back does not leave a workout (ask "Quit workout?" when one is running).
- [x] (2026-11-24, seen on tablet) A back button on the workout screen.
- [x] (2026-11-24, seen on tablet) A touch shield over the video: YouTube's own play button cannot be tapped, so the video plays only with the workout. (Was: YouTube's own play button in the video is not in step with Start workout (block it or follow it).
- [x] (2026-11-24) "Start again" restarts the whole video: a finished or quit workout now goes to its summary, so the screen no longer sits on "Again"; restarting from the start is then deliberate.
- [x] (2026-11-24, seen on tablet) Skip step and End workout as always-visible buttons, not in a ⋯ menu; the bottom row mostly square buttons.
- [x] (2026-11-24, seen on tablet) Fullscreen (hidden HUD) stop button should be a pause icon.
- [x] (2026-11-24, seen on tablet; WorkoutSeekTest) Progress bar: tap/drag to seek (chart tap with a treadmill); ±30 s rewind / fast forward; playback speed 0.75–2× (program clock runs at the same rate).
- [x] (2026-11-24, seen on tablet) Mute and volume for the workout's video (YouTube and files), remembered.
- [x] (2026-11-24, menu seen on tablet; MatchSpeedTest; tried live 2026-11-24: on plan stays 1× by design; changed to 0.05× steps so a +0.5 mph press shows, new build waiting to install after the workout; "remaining" now real time at the current rate) "Match my speed": video (and program clock) play at belt speed ÷ programmed speed, so going faster or slower than the plan moves the scenery to match (treadmill only).
Editor
- [x] (2026-11-24, seen on tablet) White dots looked draggable up/down: step-edge grips are now ‹ › pills in a strip under the chart.
- [x] (2026-11-24, seen on tablet) Dragging near 0 % pulled up the taskbar: the chart's floor now sits above the grip strip (34 dp) and the timeline excludes system gestures; incline scale includes downhill.
- [x] (2026-11-24, seen on tablet) Speed topped out at 7.5 mph (the 12 km/h default used when disconnected): the treadmill's reported range is now remembered between connections (stand-in 10 mph / 0-10 % for a set-up treadmill until it reports). Workouts made earlier were capped at 7.5 mph when created.
App-wide
- [x] (2026-11-24, seen on tablet) Hamburger menu on every screen, always (workout screen beside Back; from a running workout a menu choice asks "Quit workout?"; no edge swipe over the workout).
- [x] (2026-11-24, seen on tablet) Back is one style everywhere: a round arrow button (BackButton, after Nuvio).
- [x] (2026-11-24) Manual control's Workouts buttons removed.
- [x] (2026-11-24, seen on tablet) Profile editing holds health info (age, sex, weight, max HR) and units for the profile in use; the heart-rate screen keeps the strap, zones and Hold zone and points to the profile.
- [x] (2026-11-24, seen on tablet) Profile icons (20 + the initial); one colour choice that is also the app's theme (blue turned the app blue; restored to Lavender).
- [x] (2026-11-24, seen on tablet) One "Devices" menu item and screen: treadmill (status, Connect now, No treadmill switch, look for another) beside the heart-rate strap (strap, zones, Hold zone). Home's treadmill card opens it.
- [x] (2026-11-24, seen on tablet) Home redesigned (user: "more exciting"): rotating featured banner on the video image with Start / Edit, weekly-goal ring (150 min), streak, treadmill card, shelves (Your workouts, From the community, Start something new), quick-link pills. Own design after Nuvio's system; no other brand's look copied.
- [x] (2026-11-24: closed after step 8; My workouts, Community and History already had Home-style cards, thumbnails, effort badges and filter chips from earlier work, so no separate pass was needed) (step 1 done 2026-11-24: shared ScreenHeader with big title, subtitle and theme glow on every screen; step 2 2026-11-24: Browse with sharper banner, white Start pill, lit-edge cards, "Explore by scenery" tiles and a find-a-video banner; step 4 2026-11-24: Manual control treadmill hero card; step 3 2026-11-24: Quick start with colour preset tiles (Easy walk, Brisk walk, Hill walk, Jog, Run), a "Your workout" card with its shape, time, distance, estimated calories and a white Open workout pill; step 5 2026-11-24: Find a video with a lit search panel, white Search pill, "Where to today?" colour place tiles that search in one tap, and a How it works strip; step 6 2026-11-24: Devices with a purple treadmill hero card (round Bluetooth badge, white Connect now pill) and a red heart-rate hero card (heart badge, status pill, big bpm), zones as colour chips; step 7 2026-11-24: Profiles picker with a theme-colour glow, a 56 sp "Who's running?", 156 dp avatars and a glowing ring on the profile in use; step 8 2026-11-24: About & legal in two columns, the app, donations, a green "No ads. Ever." card and credits on the left, Safety and legal on the right) "Exciting screen" pass on every menu screen, in Home's style (user request): My workouts, Browse, Community, Find a video, History, Quick start, Manual control, Heart rate / Devices, Profiles, About.
- [x] (2026-11-24, seen on tablet; CommunityFilterTest) Community workouts: the same thumbnail grid as My workouts (duration and rating badges, Save / In your workouts, tap-to-rate stars); both with search and filters (With video, Under 20 min, 40 min +; Community also Made from a video).
- [x] (2026-11-24, seen on tablet) Find a video: a preview popup (tap the picture or Preview): the video plays from a quarter in, with its details and both make-a-workout buttons.
- [x] (2026-11-24; AnalysisProgressTest; not yet seen during a real run) Making a workout with AI: an estimated progress bar with stages and %, from the video's length, holding at 95 % until done (the server sends no progress).
- [x] (2026-11-24, seen on tablet) History: thumbnails (video, or the workout drawing, or the recorded speeds) you can tap, and "Repeat" on each row, in the detail and on the summary. Rows slimmed to time, miles, cals, bpm; tags on one line.
- [x] Music from outside apps (YouTube Music, Spotify…) during a workout. (2026-11-24: Music button on the workout bar: open an installed music app, previous/play-pause/next media keys, mute-the-video toggle.)

- [x] (2026-11-24, seen on tablet) History rows: a long name squeezed "ended early" into a vertical column; tags now sit by the date. No-treadmill distance and every workout's time now come from time actually spent, so seeking or fast-forward no longer inflates miles, minutes or personal bests (entries made before this keep their numbers).

- [x] (2026-11-24, seen on tablet; HistoryFilterTest) History search and filters (user request): name search; All / Treadmill / No treadmill / Completed / Last 30 days; sort Newest / Longest / Farthest / Calories.

## Lane 2 — Home and navigation (spec 004, to write)

- [x] (2026-11-23, seen on tablet) Home dashboard: treadmill status card, "Start where you left off", this week's totals, quick links;
  manual control (sliders, Start/Stop) moved to its own "Manual control" screen in the menu.
- [x] (2026-11-23, seen on tablet) Hamburger menu on every top-level screen (app-level drawer + ☰ in each header; off on the workout and profile screens).
- [x] (2026-11-23) Quick start → see Lane 7 P3.

## Lane 3 — Workouts (spec 005, to write)

- [x] (2026-11-23, seen on tablet) Visual workout editor under the video: tap to seek, drag levels, drag step edges, split, delete, undo; "Make it myself" from video search (no AI).
- [x] (2026-11-23, seen on tablet) No treadmill mode: run any workout with no machine. Nothing is sent; no
  +/- cards, ♥ Hold zone, swap, E-STOP, video button or speed/incline chart. The top bar shows time and heart rate only,
  a slim progress bar replaces the chart, and read-only "Suggested" incline/speed cards (with the next step) show
  what the program asks for. Machine limits don't apply (downhill steps run). History marks the run "no treadmill".
- [x] (2026-11-23, seen on tablet) Calories estimated by OpenFit, not the console: Keytel 2005 (heart rate, age, sex,
  weight) when a strap reads 90+ bpm and all three are set, else the ACSM walking/running equations (speed, incline,
  weight; 70 kg if unset). Counted on the program's clock, so pauses and video holds add nothing. Shown on the HUD
  (also in No treadmill mode) and saved to History. Weight and sex are on the heart-rate screen, per profile.
- [x] (2026-11-23, unit tested; not yet seen with a strap) Time in each heart-rate zone (the profile's zones from max HR
  or age) counted during the workout and saved to History, shown there as a zone-coloured bar with minutes per zone.
- [ ] [you] Live check with the strap: Keytel calories and time-in-zone on a real run (Lane 0).
- [x] (2026-11-23) Ads: OpenFit shows none. When the YouTube video stands still for 2 s (an ad or buffering), the
  program clock holds, so no step changes (VideoHold, 2 min cap). Not yet seen during a real ad.
- [x] (2026-11-23, seen on tablet) Editor: zoom the timeline (1x-16x, follows the playhead, overview strip to jump) for
  long workouts; "Video file…" in the editor (a device video replaces a YouTube one and vice versa); video row no longer
  pushed off screen.
- [x] (2026-11-23, seen on tablet) Local video files drew smaller than their box (top-left): the TextureView crop scaled by video pixels instead of relative to the view. Now fills and crops (editor and run screen share LocalVideoPlayer).
- [x] (2026-11-23, seen on tablet) Templates: New opens a chooser (Steady, Intervals, Hill climb, Pyramid, Blank; all with warm-up/cool-down, incline ≤ 8 %), then the editor. Thumbnails draw speed when incline is flat.
- [x] (2026-11-23, seen on tablet) Duplicate a workout (card button, copies its video too); rename is the editor's name field.

## Lane 4 — History and stats (spec 006, to write)

- [x] (2026-11-24, seen on tablet) Workout detail: tap a History entry for its stats and speed, incline and heart-rate charts (heart rate only when a strap was read). Samples every 10 s of workout time are saved with each new entry; older entries say they have no charts.
- [x] (2026-11-24, seen on tablet) Personal bests (longest, farthest, most climb, most calories; tap to open that workout) and weeks in a row (alive through last week) with the best run, on History.
- [x] (2026-11-24, seen on tablet) Export a workout as TCX (no GPS): "Export TCX" in the workout detail saves via the system picker; time, distance per point, heart rate, speed, lap totals. Imports in Strava/Garmin Connect not tried by hand yet.

## Lane 5 — Heart rate (spec 002 §3b, spec 003)

- [x] Strap support, zones, HR tile, avg/max (2026-11-23)
- [x] Hold zone built with unit tests (2026-11-23), live test in Lane 0
- [x] (2026-11-23) Hold zone aggressiveness: Gentle / Normal / Strong + warm-up hold (spec 003 §6).
- [x] (2026-11-24, built and unit tested; needs a strap run to see) Zone colour bar on the HUD chart: a 5 dp strip along the bottom of the profile chart, coloured by heart-rate zone over the part already run (ZoneTimeline).

## Lane 7 — Roadmap features (spec 008, in its order)

- [x] (2026-11-23, QuickStartTest 3/3, seen on tablet) P3 Quick start with a goal (time/distance/kcal) and optional warm-up / cool-down (replaces Lane 2's quick start).
- [x] (2026-11-24, seen on tablet) P15 Post-workout summary with a rating: "How was it?" 1-5 stars and "How did it feel?" Too easy / Just right / Too hard on the summary and in History's workout detail, saved with the entry.
- [x] (2026-11-24, offer seen on tablet; the resume seek needs a run) P4 Resume an interrupted workout: position saved every 5 s while running (ResumeStore), cleared on cool-down/finish/End/E-STOP; Home shows "Pick up where you left off" (recent, past the first minute, over a minute left) with Resume (opens the workout; after Start and the countdown it jumps there) and Dismiss.
- [x] (2026-11-24, seen on tablet) P16 Favourites: a heart on each My workouts card, a Favourites filter chip, and a Favourites shelf at the top of Browse (kept in ofit_favourites).
- [x] (2026-11-24) P2 Filters on Browse and My workouts: video/length plus effort (Easy/Medium/Hard) and type (Walk/Run/Run-walk) on My workouts, Browse and Community.
- [x] (2026-11-24, seen on tablet) P18 "For you" row on Browse: workouts like your usual effort and type over the last 30 days, none run in the last two days, longest-unrun first (ForYou, tested).
- [x] (2026-11-24, user request while making the launch video) Demo treadmill (for our own use, not in any menu; on a debug build: `adb shell am start -n com.ofit.app/.MainActivity --ez demo_treadmill true`): a simulated machine that drives the full workout screen, cool-down included, with nothing real moving; demo runs are recorded as no-treadmill workouts; deleting a History entry also removes it from Health Connect.
- [x] (2026-11-24, user request) Coach audio: no more ducking (the coach no longer turns the video and music down); a Coach volume slider in the coach menu instead.
- [x] (2026-11-24, user request) Coach speech bubble above the coach with its name, growing with the line (long lines were cut off); Speech bubbles and Coach voice are separate switches.
- [x] (2026-11-24, user request) Coach designer: name each coach (woman and man separately) and pick a voice from the tablet's voices, best sounding first, with Hear it. Not yet checked on a live run: the bubble during a workout.
- [x] (2026-11-24, user request) No treadmill mode: once a workout runs, the speed and incline cards have +/- so the runner sets the speed they are really going; the coach runs at that speed, Match my speed works from it, and distance follows it.
- [x] (2026-11-24, user request) After a workout it is saved by default, with a Don't save button (asks first) that removes it from History and Health Connect.
- [ ] [you] Video sound together with another app's music (user report 2026-11-24): not possible as built. Android gives sound to one media app at a time and YouTube's player in the WebView takes it, so the two pause each other; the music panel now says so. Would need a way to stop the WebView taking audio focus; any attempt needs a live test with a music app playing on the tablet (hourly check 2026-11-24: marked [you] for that).
- [x] (2026-11-24, user request) Intros and outros: the AI scan (server and on-tablet) marks each window intro, running or outro, and a workout keeps a suggested start and end (introEndSeconds, outroStartSeconds; nothing is cut). "Skip intro and outro" in the workout's video menu, on by default, jumps to the start and ends at the end. The editor sets both from the playhead. Server change deployed 2026-11-24; the 20 library videos had their first minute scanned (owner-only /api/intro): 12 got a suggested start, and the tablet's saved copies were updated; outros of existing workouts are not scanned.
- [x] P13 Achievements and a monthly distance challenge. (2026-11-24)
- [x] (2026-11-24, seen on tablet) P11/P12 Training plans with a schedule and reminders: Walking habit, Walk to run, Hill strength, and (user request) 5K, 10K, half marathon and marathon plans; training days, a 6 PM-style reminder only when the session is still to do, Home "Today's session" banner, progress from History.
- [x] (2026-11-24, built and seen up to the permission screen; a real write needs the user to allow access) P17 Health Connect sync (TCX export done 2026-11-24, Lane 4): menu → Health Connect → Send workouts; each finished workout becomes a treadmill-running or walking session with distance, calories, climb, heart rate and speed (write only), and "Send all past workouts too".
- [x] (2026-11-24, seen on tablet) P7 Per-profile intensity scale: Gentle / Easy / Standard / Hard / Hardest (pace -20% to +20%, incline -2 to +2) on any workout, video ones included (user request: different intensities of the same videos), picked before Start, remembered per profile, with a suggestion from the last "Too easy"/"Too hard" rating.
- [x] (2026-11-23, see Lane 3 calories entry) P21 Body weight and own calorie estimate.
- [x] (2026-11-24, seen on tablet) P6 GPX route import → incline workout: My workouts → New → "From a GPX route file"; the route's hills become ~200 m steps at their grade (merged when within 0.75 %, capped at 15 %), at 5 km/h, opened in the visual editor to set the pace and save.
- [x] (2026-11-24) P19 Video volume / mute on the HUD: the Sound button (music and video sliders, mute).

## Lane 8 — Safety (spec 007)

- [x] (2026-11-23) Command gate: belt commands only during OpenFit's own run; console STOP wins; manual use refused.
- [x] (2026-11-23) Hands off when idle: release + disconnect after 60 s (5 min on the workout screen); disconnect at once on manual use.
- [x] (2026-11-23) Connect on demand (not at app start); "Connect now" on Home.

## Lane 6 — Open-source release (spec 009)
- [x] (2026-11-24) Backup: Android backup to Google Drive with explicit rules (no keys, no treadmill link), plus Save / Restore a backup file from the menu (Drive via the picker); save and restore tested on the tablet.
- [x] (2026-11-24) Pre-scanned library: 19 YouTube scenery videos in community/ (one failed twice and was left out).
- [x] (2026-11-24) Secret scan of files and history before going public: clean.
- [x] (2026-11-24) Workout summary found no entry for a run at a matched video rate and said "under a minute" (the 1:37 run was saved): the summary now looks the entry up by the wall-clock start (installed; History shows the run).
- [x] (2026-11-24) No paid AI for now: new AI work needs the user's own Gemini key; cached results free (deployed, installed).
- [ ] Later, if wanted: Stripe credit packs (cellardoor's code) for AI on the owner's key, outside Play only.
- [x] (2026-11-24) No-server mode: Keys & services switch; search, video-to-workout and coach facts/landmarks on the tablet with the user's own keys; community read from the repo's community/ folder (installed; live check needs keys and a public repo).
- [x] (2026-11-24) Donation addresses (Bitcoin, Ethereum) on About and in README.
- [x] (2026-11-24, pushed d57150f at the user's request; raw community/programs.json now 200) Repo public and main pushed so the app can read community/ without a server.

- [ ] [you] Switch community data to the repo: commit/push community/, make a fine-grained GitHub token, set GITHUB_TOKEN, GITHUB_REPO, VOTE_SECRET in Vercel (writes go to main, user choice 2026-11-24), redeploy (code done and deployed 2026-11-24, still on Redis).
- [x] (2026-11-24) Split handoff.md into public DEVELOPMENT.md + private git-ignored handoff (handoff.md was already in .gitignore).
- [x] (2026-11-23, app + backend built, backend not deployed) Bring your own Gemini key (menu → Your Gemini key).
- [x] (2026-11-24) "Backend URL" setting so self-hosters point OpenFit at their own backend: Keys & services → Server address (HTTPS, or HTTP on a home network; ServerUrlTest).
- [x] (2026-11-24) Superseded: no AI on the project's key for the public; new AI work needs the user's own key.
- [x] (2026-11-24) Superseded: no paid plan for now (user decision); Stripe credit packs noted above for later.

- [x] GPL-3.0 LICENSE, README, .gitignore (2026-11-23)
- [x] (2026-11-24) CONTRIBUTING.md and android/docs/adding-a-treadmill.md (what was needed for the I_TL console).
- [x] (2026-11-23) Removed comments citing third-party decompiled code; neutral wording throughout.
