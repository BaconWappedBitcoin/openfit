# Spec 003 — Heart-rate-led speed ("hold my zone")

Status: built 2026-11-23, NOT tested on the treadmill. Follows spec 002 H7. Logic in
`protocol/HeartRateCoach.kt` (HeartRateCoachTest 8/8); app glue in `app/heart/CoachManager.kt`; HUD: ♥ square
button in the bottom bar (Hold zone 1-5 / Turn off) and a red "Holding zone" banner (also on the hidden
overlay) that turns it off on tap. Coach speed changes go through WorkoutManager.overrideSpeed, so they show
as MANUAL on the speed card.

## 1. Goal

The runner picks a heart-rate zone (or a target BPM) and OpenFit adjusts the belt speed, a little at a
time, to keep them in it. Incline stays as the program says. This is the one OpenFit feature that moves the
belt on its own judgement rather than on a step the runner chose, so it has its own safety rules.

## 2. Preconditions (all must hold, or the feature cannot be switched on)

- P1 A strap is connected and has given a usable reading in the last 5 s (`HeartRateSource.STRAP`).
  The console's grip pulse is never used: it vanishes when the hands leave the grips.
- P2 Zones are known (age or max HR set; spec 002 H5).
- P3 A workout is running and the machine is armed.
- P4 The runner switched it on for this workout. It is never on by default and never remembered on.

## 3. Control

- C1 Every 15 s, compare the 30 s average BPM with the target band (the zone, or target ±5 BPM).
- C2 Below the band: speed +0.2 km/h. Above: −0.3 km/h (down faster than up). Inside: no change.
- C3 Never more than one change per 15 s, and never more than +1.0 km/h above the step's own speed or
  below the machine minimum (0.8 km/h on the T 6.5). The user ceiling and machine maximum still apply,
  checked in the protocol layer as for every command (SafetyLimits).
- C4 After a program step change, wait 45 s before the first adjustment (heart rate lags).

## 4. Safety

- S1 Strap lost or stale for 10 s: stop adjusting, hold the current speed, show "Heart rate lost: holding
  speed". Never speed up without a fresh reading.
- S2 BPM above max HR × 0.95 for 10 s: drop to the step's own speed (or lower, never higher), show a red
  "Heart rate very high" banner, and switch the feature off.
- S3 An implausible jump (more than 40 BPM in 2 s) is ignored as strap noise.
- S4 Any manual speed change by the runner (cards, dialog, console buttons) switches the feature off for
  the rest of the step: the runner is in charge.
- S5 Pause, End, Emergency Stop and the console's STOP behave exactly as without it.
- S6 The HUD shows that it is on all the time (a ♥ badge on the speed card with the target band) and one
  tap switches it off.

## 5. Tests

- Unit: a simulated heart-rate series drives a fake sink; check C1–C4 and S1–S4, including that no
  command ever exceeds the step speed + 1.0 km/h or the limits.
- Live (runner present, low speeds): target zone 2, start at 3 km/h, confirm small steps up, take the strap
  off and confirm the hold, touch speed − and confirm it switches off.

## 6. Aggressiveness (user request, 2026-11-23)

Heart-rate-led speed can feel too aggressive, especially at the start of a workout when heart rate is
naturally low. OpenFit lets the runner choose how hard Hold zone pushes, saved per profile (Heart-rate
monitor screen, and the ♥ menu on the workout screen):

| Level | Step up / down | Check every | Max above the step | Warm-up (no speed-ups) |
|-------|----------------|-------------|--------------------|------------------------|
| Gentle | +0.1 / −0.3 km/h | 30 s | +0.5 km/h | 5 min |
| Normal (default) | +0.2 / −0.3 km/h | 15 s | +1.0 km/h | 3 min |
| Strong | +0.3 / −0.4 km/h | 10 s | +1.5 km/h | 2 min |

W1 During the warm-up the coach never speeds up; it may slow down. W2 No level can go above the user's
ceiling or the machine maximum (SafetyLimits) or more than its own cap above the program step.
Tests: HeartRateCoachTest (11), including each level's cap and the warm-up hold.
