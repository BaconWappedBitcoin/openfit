# Spec 007 — Hands off when no workout is running

Status: built 2026-11-23. Unit-tested (ControlGateTest). Connect-on-open seen on the tablet; the idle
release and the manual-use disconnect still need a live check with the console awake.

## Problem

An app that stays connected, or reconnects, while someone uses the treadmill by hand with the
console buttons, and sends it commands then, is a safety problem: a belt changing speed under someone who
did not ask for it.

## Rules

- R1 **Commands only during OpenFit's own run.** Speed, incline and pause are sent only between an
  accepted Start from OpenFit and the end of that run. Stop is always allowed. Enforced in the protocol
  layer (`Session` + `ControlGate`), so no screen can get around it. Refusal: `NO_APP_WORKOUT`.
- R2 **The console's STOP wins.** If the console ends OpenFit's run (STOP button, safety key, its own
  results screen), OpenFit stops sending belt commands at once (`APP_RUN_ENDED`).
- R3 **Manual use is detected and respected.** A running belt that OpenFit did not start is manual use:
  OpenFit refuses to start over it, sends nothing, and drops the Bluetooth link at once.
- R4 **No idle link.** With no workout running, OpenFit gives the console back to its own buttons (undoes
  the arming write RequireStartRequested) and disconnects after 5 min with no workout (60 s until 2026-11-24, raised at the user's request so Devices can stay connected), the same while a workout screen is
  open so Start stays quick.
- R5 **Connect on demand.** OpenFit does not connect at app start. It connects to the remembered
  treadmill when a workout screen opens, when Start is pressed, or on "Connect now". The heart-rate strap
  still connects at start (it cannot move anything).
- R6 Everything is logged (`ofit-ble: hands off …`) and the user sees why OpenFit disconnected.

## Tests

- Unit: ControlGateTest — no commands before a start; manual use blocks commands; the console's STOP
  ends the app's run; the start grace; unknown modes change nothing.
- Live [user]: (1) open a workout, leave it, wait 5 min: link drops, console buttons work normally.
  (2) With OpenFit's workout screen open, start the belt with the console's own START: OpenFit says it
  disconnected and sends nothing. (3) During an OpenFit workout, press the console's STOP: no further
  speed/incline writes in the log.
