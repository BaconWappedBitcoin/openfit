# Contributing to OpenFit

Thanks for helping. OpenFit controls a machine people stand on, so a few rules are firmer than in
most projects.

## Before you start

- Read [DEVELOPMENT.md](DEVELOPMENT.md) for the layout, the build and the rules the code keeps.
- For anything bigger than a fix, open an issue first and say what you want to change and why.
- Check `android/specs/BACKLOG.md`: it may already be planned, or done differently on purpose.

## Pull requests

1. Keep each pull request to one change.
2. Put logic in `android/protocol` with unit tests. Pull requests that touch the protocol, the
   workout engine or `SafetyLimits` need tests that show the new behaviour.
3. Run `gradle :protocol:test :app:testDebugUnitTest :app:assembleDebug` (and `npm test` in
   `backend/` if you changed it) before you push.
4. If you tested on a real treadmill, say which machine and console, and what you checked. Never
   test belt control without the safety key clipped on.
5. Describe what the user will see, with a screenshot for UI changes.

## Things we will not merge

- Anything that sends speed or incline outside the machine's limits or the user's ceilings, or that
  weakens Stop / E-STOP, the control gate or the cool-down.
- Code, apps, firmware or media copied from iFit or any manufacturer, or anything decompiled from
  them. Protocol knowledge from your own Bluetooth captures and public research is welcome; cite it.
- Ads, tracking or analytics.
- Secrets: API keys, tokens or personal data in code, tests or captures. Scrub captures of device
  serial numbers before sharing them.

## Adding a treadmill

See [android/docs/adding-a-treadmill.md](android/docs/adding-a-treadmill.md). A capture from your own
machine and the console's name are the most useful things you can send, even without code.

## Community workouts

Shared workouts go through the app (Community → Share). They are moderated automatically; use the
flag on a card to report one.

## Licence

By contributing you agree that your work is released under the GNU GPL v3 or later, like the rest
of OpenFit.
