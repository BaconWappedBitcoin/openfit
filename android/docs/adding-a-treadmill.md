# Adding another treadmill

OpenFit was built and tested on one machine: a NordicTrack T 6.5 with an `I_TL` console. Other
iFit-compatible consoles speak the same family of protocol ("FitPro" frames over Bluetooth Low
Energy), but details differ. This is what it took for the `I_TL`, and what to check on yours.

**Safety:** clip the safety key on and stand beside the belt, not on it, until speed and stop work.

## 1. Find it

`Scanner` shows every Bluetooth device it sees and marks consoles by name: a device is a console
when its name starts with one in `Gatt.CONSOLE_NAMES` (`protocol/.../Transport.kt`). It does not
filter by service UUID, because the `I_TL` does not advertise its service.

If your console's name is not marked: note its exact advertised name (it shows in **Devices**), add
the prefix to `CONSOLE_NAMES`, and add a case to `ConsoleNameTest`.

## 2. Connect and read

The console service is `00001533-1412-efde-1523-785feabcd123`, with one characteristic to write
and one to notify (`Gatt.HOST_WRITE`, `Gatt.MACHINE_NOTIFY`). No pairing: the `I_TL` simply
connects and talks; asking Android to bond broke it.

Reads work straight away: in read-only mode OpenFit polls speed, incline, distance and so on while
the console stays in its own control. If reads fail, the framing differs; see
`docs/protocol-notes.md` for the three layers (BLE chunks, command frame, field payload) and how to
capture bytes from your own machine.

## 3. Unlock and arm

Writes need an unlock (`VerifySecurity`, `0x90`) and then arming. `UnlockCalculator` computes the
unlock from the console's own DeviceInfo (`0x81`), SystemInfo (`0x82`) and VersionInfo (`0x84`)
answers, checked byte for byte against a capture from the `I_TL`. It has one known special case for
a model number. If your console refuses the unlock, capture the vendor app's unlock (the 36 bytes
after `0x90`) and compare with what `UnlockCalculator` produces for your handshake: the difference
usually shows which input differs. Add your captured handshake and unlock to `UnlockCalculatorTest`.

## 4. Limits

`SafetyLimits` uses the machine's own minimum and maximum speed and incline as the console reports
them, and never assumes them. Check in **Manual control** that the reported limits match your
machine's manual before running a workout. Report a console that reports none.

## 5. Control

With the treadmill armed, check in this order, standing beside the belt:

1. **Stop** and **E-STOP** stop the belt from the app.
2. The console's own STOP still works while OpenFit is connected.
3. Speed steps up and down by 0.5 and the console display follows.
4. Incline steps up and down.
5. Make a two-step workout (1 minute slow, 1 minute a little faster) and run it: the belt starts,
   changes speed, and slows to a stop through the cool-down.
6. Close OpenFit while idle: the console goes back to its own control.

## 6. Send it back

Open a pull request (see `CONTRIBUTING.md`) or an issue with: the console name and machine model,
which of the checks passed, and any captures, with serial numbers removed. Even "it works as is on
model X" is worth adding to the README's compatibility list.
