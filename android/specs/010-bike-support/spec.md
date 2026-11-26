# 010: Bike support (NordicTrack S15i and indoor bikes in general)

Requested 2026-11-25 by a community user: "NordicTrack S15i bike and adding bike support in general".

## What is known

- OpenFit's protocol layer already carries the FitPro field ids that bikes use (`RESISTANCE`,
  `WATTS`, `RPM`, `GRADE`, `GEAR`, see `FieldModel.kt`), and the console names list already has
  `BIKE`. Nothing drives a bike yet: the app assumes a treadmill (speed and incline) everywhere.
- Most modern indoor bikes, and (per their owners and Zwift's own pairing lists) iFit bikes such as the
  S15i and S22i, also speak the **standard Bluetooth Fitness Machine Service (FTMS, 0x1826)** for
  third-party apps. That is a published specification, so it can be built and tested without the bike.
- `references.md` notes the S22i family uses an Android console; whether the S15i exposes the same
  FitPro Bluetooth service as the T 6.5's `I_TL` is **not known** and needs a capture from a real S15i.

## What is not known (do not guess)

- The S15i's advertised name, whether FTMS is enabled by default or only after the console's own
  pairing screen, and its resistance scale (iFit's 1 to 24 levels vs FTMS 0.1 steps) and incline range
  (about -10 % to +20 %). A user with an S15i needs to tell us, or run the capture guide.

## Plan, in order

1. **Wire formats (done 2026-11-25):** `protocol/Ftms.kt` + `FtmsTest`: Indoor Bike Data, Features,
   supported ranges, Control Point commands (target resistance, power, inclination, speed, simulation
   grade, start/stop/pause) and the machine's responses. No radio, no app change.
2. **Machine type:** `MachineKind { TREADMILL, BIKE }` chosen by what the device offers (FTMS Indoor Bike
   Data vs Treadmill Data, or the FitPro field set). Safety limits for a bike: a resistance ceiling and a
   power ceiling the user sets, checked in the protocol layer like speed and incline (constitution, Art. IV).
3. **Bluetooth layer:** an FTMS client in the app (scan for 0x1826, subscribe to Indoor Bike Data, take
   control with the Control Point, write targets). Same rule as for treadmills: connect only while a
   workout runs, let go when idle, never take over a machine someone is using by hand.
4. **Bike workouts:** steps in resistance or power (and grade for simulation) instead of speed and
   incline, cadence targets as a coach cue; the editor, quick start and training plans get a bike
   flavour; the HUD cards show resistance/power and cadence in place of speed/incline.
5. **Virtual rides:** the 3D trail for a bike (speed from the bike, grade drives the simulation grade).
6. **S15i specifics:** only after a capture or a user report: name prefix, FitPro vs FTMS, level mapping.

## Safety

A bike is a machine too: nothing is written until a workout the user started, limits are checked first,
and a stop is always one tap away (FTMS `stop`/`pause`).
