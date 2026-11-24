# The Treadmill Wire Format (iFit-compatible consoles), and How to Confirm It

This note records what is known about the protocol, what is still unverified, and exactly how to
capture the missing bytes from the user's own machine.

Everything here is implemented in `protocol/`. Every constant cites

## 1. The three layers

```text
BLE chunks        FE/FF framed 20 byte writes
      |
command frame    [device][len][cmd][content...][checksum]
      |
field payload    a bitmap of field ids, then values in ascending id order
```

## 2. BLE chunks

| Chunk | Layout |
|---|---|
| header | `FE 02 LL NN` - `LL` is the total payload length, `NN` is the number of payload chunks |
| payload | `II LL <data>` - `II` is the chunk index, or `FF` on the final chunk |

Eighteen payload bytes fit in each chunk. A short message is one header chunk plus one chunk
marked `FF`.

**Status: partly unverified.** The chunking itself is well corroborated. The exact meaning of the
`NN` byte, and whether the transport adds a further header before the command frame, are settled
by the capture procedure in section 7.

## 3. The command frame

```text
offset  0    1    2    3              n-2   n-1
        04   LL   02   content...      CS
        dev  len  cmd
```

- `dev` is the equipment family: `0x04` treadmill.
- `len` is the **total frame length**, header and checksum included.
- `cmd` is the command id.
- `CS` is the low byte of the sum of every preceding byte.

**This layer is confirmed against real traffic.** The vendor application's own byte log produces
request frames that begin `04 13 02 00 0d 1b 94 31 ...` for the telemetry poll. The protocol
library regenerates that frame byte for byte:

```console
$ protocol poll
poll request command : 041302000d1b9431000040500080180000012f
```

The command layer, the length byte, and the field bitmap all match. Only the trailing checksum
byte cannot be compared, because the captured log does not record it for that frame.

### Commands

| Id | Name | Used for |
|---|---|---|
| `0x02` | ReadWriteData | reading and writing fields |
| `0x04` | Connect | console connect |
| `0x05` | Disconnect | console disconnect |
| `0x06` | Calibrate | sensor calibration |
| `0x80` | SupportedDevices | which equipment types are supported |
| `0x81` | DeviceInfo | which fields are supported, and the serial number |
| `0x82` | SystemInfo | part number and model |
| `0x84` | VersionInfo | firmware string and library version |
| `0x88` | SupportedCommands | which commands are supported |
| `0x90` | VerifySecurity | the unlock |
| `0x95` | SerialNumber | the serial number |

### The order that matters

Discovery is `0x81` → `0x80` → `0x88` → `0x82` → `0x84` → `0x95`, then the unlock `0x90`, then the
arming writes. The unlock needs values that only the discovery queries return, so discovery has to
come first.

## 4. Fields

A field is addressed by its numeric id. A request carries a bitmap of ids; the values follow in
ascending id order, each sized by its converter. Multi-byte integers are little-endian.

| Id | Field | Size | Notes |
|---|---|---|---|
| 0 | Kph | 2 | u16 × 100, target speed |
| 1 | Grade | 2 | **s16** × 100, target incline |
| 4 | CurrentDistance | 4 | u32 metres |
| 7 | KeyObject | 14 | console key press; code 2 is START |
| 10 | Pulse | 4 | current bpm, average bpm, count, source |
| 12 | WorkoutMode | 1 | 0 unknown, 1 idle, 2 running, 3 pause, 4 results, 10 warm up |
| 16 | ActualKph | 2 | u16 × 100, observed speed |
| 17 | ActualIncline | 2 | s16 × 100, observed incline |
| 20 | CurrentTime | 4 | u32 seconds |
| 21 | CurrentCalories | 4 | u32 × 1024 / 1e8 |
| 27 / 28 | Max / Min Grade | 2 | the machine's incline limits |
| 30 / 31 | Max / Min Kph | 2 | the machine's speed limits |
| 95 | IdleModeLockout | 1 | write 0 to arm |
| 96 | StartRequested | 1 | |
| 100 | ActivationLock | 1 | |
| 108 | RequireStartRequested | 1 | write 1 to arm |

**Sign matters.** Grade, and every incline readback, is a *signed* 16-bit value. Encoding the sign
wrongly would command a wildly incorrect incline.

## 5. Arming, the step that decides everything

A connected machine accepts reads immediately. It accepts *writes* only after it is unlocked and
armed.

1. **Unlock**: `0x90` with a 36 byte payload. The payload is a 32 byte security hash followed by a
   4 byte secret derived from the machine's master library version.
2. **Arm**: write `IdleModeLockout = 0`, then `RequireStartRequested = 1`. Each must be answered
   with status `0x02` (Done).

Without arming, the machine accepts writes and ignores them, and its physical START button starts
its own local workout instead of being reported to the app.

**The unlock hash construction is not published** in the sources consulted. Two routes are
supported by the implementation:

- **Captured unlock** - 36 bytes captured from the vendor application for this machine. This is the
  default route, because it needs no knowledge of the hash.
- **Computed unlock** - the published structure with a pluggable hash function. It is disabled by
  default (`FeatureFlags.unlockComputed`), because the hash itself is unknown.

## 6. Read-only mode

Read polls work with no handshake at all. That leaves the machine in local-control mode, so its
physical buttons keep working normally while the user watches the metrics. This is deliberate:
arming the board changes that behaviour, so the app only arms when the user asks for control.

## 7. Capturing the ground truth

Two captures are needed. Both are done on the user's phone, with the vendor application connected
to the treadmill.

### 7a. The Bluetooth bytes (HCI snoop)

This settles the chunk layout and the transport header.

1. On the phone, open **Settings → About phone** and tap **Build number** seven times to unlock
   developer options.
2. In **Developer options**, turn on **Enable HCI snoop log**.
3. Turn on the treadmill and connect to it with a controlling app.
4. In the application, do one of each: start a workout, change the speed, change the incline, stop.
5. Turn off **Enable HCI snoop log**.
6. In **Developer options**, turn on **Get a bug report**, wait a minute, then take the bug report
   zip. On a computer connected by USB, `adb bugreport` does the same.
7. Open the zip. The capture is at `fs/data/misc/bluedroid/btsnoop_hci.log`.
8. Open it in Wireshark with the Bluetooth HCI and ATT dissectors, filter for the treadmill's
   address, and look at the ATT write requests to handle `0x000e` and the notifications from handle
   `0x000b`.

What to record:

| Question | Where to look |
|---|---|
| Does the write begin `FE 02 LL NN`? | the first bytes of the ATT write payload |
| What does `NN` count? | a capture with a payload long enough for three chunks |
| Is there a transport header before the command frame? | the bytes between `FE` and `04` |
| What is the checksum rule in practice? | a full frame in one write |
| What does the unlock payload look like? | the write carrying command `0x90` |


### 7c. Turning a capture into a test

Each captured frame becomes a golden vector in
`protocol/src/test/kotlin/com/ofit/protocol/fixtures/golden-vectors.json`, with a test that asserts
the library produces the same bytes. That is how the unverified parts become verified, one at a
time, and it is the only way the project can honestly claim the format is right.

## 8. What is still unverified

| Item | How it is handled in the code |
|---|---|
| The `NN` byte in the chunk header | one implementation, isolated in `Framing.encode` |
| A transport header before the command frame | `Wire.HEADER`, one constant, one function |
| The unlock hash construction | `FeatureFlags.unlockComputed` is off; the captured route is the default |
| The response layout and status byte | `Session.readFields` assumes status first, then values |
| Which fields this machine populates | polled at runtime, with absent values distinguished from zero |
| The machine's own speed and incline limits | read at connect time, never assumed |
