# References and Attribution

Required by constitution Article VI. Every external reference consulted during this project's
analysis is listed with its licence and exactly what was taken from it.

## License policy applied

| Policy | Consequence |
|---|---|
| Permissive (MIT, Apache-2.0, BSD) | May be used, adapted, and vendored with attribution |
| No licence declared | No permission to reuse the code. Facts and descriptions only |
| GPL-3.0 | Source may **not** be incorporated into this project (copyleft would extend to the whole work) |
| AGPL-3.0 | Same as GPL-3.0, and stronger on network use |

This project implements the iFit wire protocol **from the documented byte format and observed
behaviour**. Protocol facts — field identifiers, byte layouts, command sequences, UUID values —
are not copyrightable expression. No source code from a copyleft project has been copied,
adapted, or translated into this repository.

The user has stated that reference code may be used freely. Where that conflicts with the
licences of the reference projects, the licence wins for code and the protocol facts are
reimplemented independently. Where a reference is permissively licensed, it is used directly and
attributed here.

## References

### 1. DavisGoglin/treadmill

- **URL**: https://github.com/DavisGoglin/treadmill
- **Licence**: MIT
- **Used for**:
  - The field table (id, size, converter) for FitPro1 — the authoritative source for the
    field list used in `data-model.md`.
  - The handshake and **arming** sequence: `IdleModeLockout = 0` then `RequireStartRequested = 1`,
    each required to report status `0x02`.
  - The claim that the unlock is **computed** from device-reported values rather than hardcoded.
  - The three poll masks and their field sets.
  - Documented failure modes: machine re-lock and `SecurityBlock` recovery, the intermittent
    GATT error on the first write after connect, and the request/response discipline.
- **Code incorporated**: No. The MIT licence would permit it, but the implementation is written
  independently from the format description.

### 2. ianchi/ifit

- **URL**: https://github.com/ianchi/ifit
- **Licence**: none declared (no `LICENSE` file, no SPDX identifier in the GitHub API)
- **Used for**:
  - The three-layer protocol model (BLE framing → command protocol → data encoding).
  - Command and equipment id tables.
  - Worked encoding examples (bitmaps, scaled integer encoding, little-endian ordering).
  - The activation-code discovery approach (BLE man-in-the-middle proxy).
- **Code incorporated**: No. No licence was declared, so no permission exists.
- **Note**: This project's documentation labels `1535` as "RX (client writes)" and `1534` as
  "TX (client receives)". The direct GATT capture in reference 3 shows the opposite direction.
  This project's implementation follows the capture.

### 3. taylorbowland.com — "Reversing Treadmill Bluetooth"

- **URL**: https://taylorbowland.com/posts/treadmill-sending-data/
- **Licence**: prose article, used as a factual description
- **Used for**:
  - The direct GATT capture: service handles, characteristic handles, and characteristic
    properties (`1534` = write, `1535` = notify), plus the CCC descriptor write of `0x0100`.
  - The `I_TL` advertised name confirmation.

### 4. mikepugh/NordicFTMS

- **URL**: https://github.com/mikepugh/NordicFTMS
- **Licence**: GPL-3.0
- **Used for**: Architecture reference only — the split between a console-facing protocol layer
  and a Bluetooth peripheral layer, and the general shape of an Android-based FTMS bridge.
- **Code incorporated**: No.
- **Not applicable here**: this project targets the single-master BLE console family
  (`SINGLE_MASTER`), not the Android-console gRPC family that NordicFTMS bridges.
  Confirmed by the vendor's own equipment profiles: the `MULTI_MASTER` profiles (FREEMOTION
  t10.8, NordicTrack Commercial X32i, S22i Studio Cycle) are the GlassOS Android consoles
  NordicFTMS targets, while the user's `SINGLE_MASTER` profile names an `NRF51` MCU and a
  `PM210_DISPLAY` — **no Android tablet, no gRPC endpoint, no touch screen**. NordicFTMS cannot
- **Deep scan (2026-11-22) — read**: `README.md`, `AGENTS.md`, `nordicrower/README.md`,
  and the full `git tree` of the repository.
- **What it actually is**: two separately packaged apps, both of which **run on the treadmill
  console itself**, not on the user's phone:
  - `NordicFTMS` — installs onto a GlassOS console (Android) via privileged mode
    (tap the white console area 10 times, wait 7 s, tap 10 more), enables USB debugging,
    then `adb connect <console-ip>:5555` and sideloads the APK. It reads live metrics by
    subscribing to **gRPC streams on `localhost:54321` over mTLS**
    (`GrpcControlService.java`), and republishes them as a Bluetooth FTMS/RSC GATT server
    (service `0x1826`, `0x1814`) plus Wahoo DIRCON over Wi-Fi.
  - `NordicRower` — a sibling app for Wolf/iFIT FitPro **USB rowers**; it talks FitPro over
    USB on the console and exposes FTMS. Measurements only, no control.
- **Consequence for this project**: NordicFTMS solves a *different* problem and cannot be
  adapted. It requires (a) a GlassOS Android console we do not have, (b) `adb` into the
  machine, (c) privileged-mode access, and (d) a second Bluetooth hop. It also cannot
  reach a machine that only exposes the `I_TL` FitPro BLE peripheral.
  It **is** a useful reference for one thing: the *shape* of a protocol layer isolated
  behind a transport interface, which is exactly this project's `protocol/` + `transport/`
  split.

### 5. cagnulein/QZCompanionNordictrackTreadmill

- **URL**: https://github.com/cagnulein/QZCompanionNordictrackTreadmill
- **Licence**: AGPL-3.0
- **Used for**: Architecture reference only — the Android app shape and the general problem of
  driving a NordicTrack from a phone.
- **Code incorporated**: No.
- **Deep scan (2026-11-22) — read**: `README.md`, `CLAUDE.md`, and the full `git tree` of the
  repository (183 entries, `truncated: false`).
- **Correction to an earlier assumption**: this repository has **no `docs/` directory and no
  `docs/PROTOCOL.md`** — a URL to that path returns `404`. There is no separate protocol
  document; the protocol lives in code and in `README.md`/`CLAUDE.md`.
- **Correction to a second assumption**: QZ Companion is **not** a phone app. Its README states
  that it is *"installed on your NordicTrack treadmill or bike"* and *"should work on any
  iFit-enabled/iFit-embedded NordicTrack or Pro-Form treadmill or bike **with built-in Android
  tablet**"*. It reports to a *separate* phone-side program ("QZ"); the open relative is
  `cagnulein/qdomyos-zwift`. So this is a **console-side** app, like NordicFTMS.
- **What it actually is**: a console-side app that drives the treadmill by **simulating touches
  on the console's own screen**:
  - `UDPListenerService.java` (59 KB) — the core. It listens on UDP for control datagrams and
    translates a requested speed/incline into an `input swipe` command executed on the
    console.
  - `MainActivity.sendCommand()` sends that command over **ADB** (`connection.queueCommand()`);
    some device variants instead use `shellRuntime.exec()` → `Runtime.getRuntime()` for a
    direct shell call, which skips ADB but still requires USB debugging on the machine.
  - Console interaction is a simulated **swipe** on the console's on-screen speed/incline
    sliders: for the S22i bike, `x1 = 75`,
    `y2 = (int)(616.18 - 17.223 * reqResistance)`, driven by `input swipe <x1> <y1> <x1> <y2> 200`.
    The sliders must first be enabled by hand (manual workout → tap an empty area → "Controls").
  - `QZService.getOCR()` reads the console's on-screen text to recover speed, incline,
    cadence, and watts (patterns `speed`, `incline`, `rpm`/`strokes per min`, `watt`, and a
    `500 split` → km/h conversion of `1800 / seconds`), plus `ScreenCaptureService` /
    `MediaProjection` for the screen feed.
  - `MyAccessibilityService` and `AndroidActivityResultReceiver` handle the overlay and the
    privileged-mode prompt.
  - Installation requires a **factory reset**, privileged mode (tap a white area 10×, wait 7 s,
    tap 10× more, redeem a 6-digit response code from `getresponsecode.com`), USB debugging, and
    `adb connect <console-ip>:5555` over Wi-Fi.
- **Consequence for this project**: QZCompanion's approach is a **screen-automation** fallback
  that depends on the console being a built-in Android tablet with a touch overlay. It is not a
  protocol implementation, it cannot read structured machine state, and it cannot drive a
  machine without a screen. It is strictly worse than a real protocol client for this goal,
  and it introduces factory-reset/privileged-mode/ADB as prerequisites.
  **Structurally impossible on the user's machine**: its README scopes it to equipment
  *"with built-in Android tablet"*, and the user's `SINGLE_MASTER` profile declares an `NRF51`
  MCU and a `PM210_DISPLAY` — an MCU console with no tablet and no touch screen. See
- **Salvaged idea (recorded, not adopted)**: QZCompanion's *console-side* UDP listener is a
  phone→machine path that would work without Bluetooth, and `cagnulein/qdomyos-zwift` is the
  related bridge. If the FitPro BLE path proves unworkable on the user's machine, a
  network path is the documented fallback. It is **out of scope for v1** (the spec's
  out-of-scope list excludes network/cloud transports) and is noted in `research.md` as a
  contingency only.

### 7. github/spec-kit

- **URL**: https://github.com/github/spec-kit
- **Licence**: MIT
- **Used for**: The specification-driven workflow that governs this project: `spec.md`,
  `plan.md`, `research.md`, `data-model.md`, `tasks.md`, and the constitution.

## Media, and what this project deliberately does not do

Third-party workout videos are protected content that belongs to their owners. OpenFit does not download, decrypt,
record or redistribute them, and never will. It plays only YouTube videos (through YouTube's own player)
and the user's own local files.
