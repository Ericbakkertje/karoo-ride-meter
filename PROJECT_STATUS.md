# Karoo Ride Meter — Project Status

_Last updated: 2026-11-07 — 1.0.0: split off from Karoo Graphic Meter (where it was the Ride Meter field, 0.4.0–0.8.1)._

## Version

| | |
|---|---|
| App version | **1.0.0** (`versionCode 1`) |
| Application ID | `com.karooridemeter` |
| Extension ID | `karoo-ride-meter` (field typeId `ride-meter`) |
| Hammerhead SDK | `io.hammerhead:karoo-ext:1.1.9` |
| Toolchain | Kotlin 2.0.0, Android Gradle Plugin 8.5.2, Gradle 8.8 (wrapper), JDK 17 |
| Android | compileSdk/targetSdk 34, minSdk 23 (Karoo 3 runs Android 12L, API 32) |

## Origin

Split from `~/Downloads/karoo-graphic-meter` on 2026-11-07 at the user's request. The code is the Ride Meter
from Graphic Meter 0.8.1 (branch `feature/ride-dial`, commit history there), with its own:
- extension `RideMeterExtension` (id `karoo-ride-meter`, log tag `KarooRideMeter`),
- settings `RideSettings` (prefs `ride_meter_settings`: dial scale 30/45/60/90, default 60, and the three fields) and
  a settings screen "Ride Meter Settings" (dial scale + three dropdowns). Settings are **not** carried over from the
  Graphic Meter: they start at the defaults (0–60, Slope % / Time of day / Cadence).
- copies of the shared pieces: `GaugePalette` (`Palette.kt`), power and HR zones (`Zones.kt`), `GaugeViewSession`.

Because the extension id changed, a Ride Meter field placed on a page with the Graphic Meter must be
**re-added** from the "Ride Meter" extension in the Karoo field picker.

## The Ride Meter field (`ride-meter`) — `RideMeterDataType`, `RideGauge`, `RideRenderer`
- **0.8.0 design (approved from a preview on 2026-11-07):** 270° **dial face** instead of the colour band —
  thin grey ring, major ticks with numbers every 10 km/h and minor ticks every 2 (0–90: 10/5; 0–30 and 0–45: 5/1),
  ticks up to the current speed lit white, the rest grey; numbers up to the speed white. **Red tapered pointer**
  across the ticks with a dark outline; above the scale it pins to the end.
- Centre: `km/h`, big speed, `Gear 40 / 15` (AXS front/rear teeth), **distance only** (`61.2 km`, ride time removed
  at the user's request; ride time is still a field choice).
- **Three round data fields** (circles with a coloured ring), chosen in Settings (`SettingsActivity` / `RideSettings`, dropdown per field, default
  Slope % / Time of day / Cadence). Choices: slope, time of day, cadence, HR, power, power 3s, W/kg, avg power, NP,
  avg/max speed, distance, ride time, climb (m), gear, temperature, calories. Power/HR use the profile zone colours.
  Tall fields: three circles in a row under the dial (dial + row centred together). Wide fields: three circles
  stacked on the right if each is ≥ 90 px, otherwise circle / one-line `LABEL: value` / circle.
  Small fields (h < 150 or w < 200): dial and text only.
- **Shifting battery** top-left instead of the Karoo battery (`ShiftBattery`), for SRAM AXS and (0.8.1) Shimano Di2:
  - **Shimano Di2** via the **Ki2** extension (`com.valterc.ki2`, id `ki2`, v16.0 on the device; Hammerhead does
    not support Di2 itself). Ki2 exposes a numeric field `DATATYPE_SHIFTING_BATTERY_PERCENTAGE` (0–100 %), read as
    `TYPE_EXT::ki2::DATATYPE_SHIFTING_BATTERY_PERCENTAGE`. Shown as `Di2 87%`, icon filled to the %, colour by the
    same level thresholds. Di2 wins when both report.
  - **Di2 gear:** Ki2's own gear fields are view-only, but when the Di2 is **added as a Karoo sensor**
    (Sensors → + → Extensions → Ki2 connection, gearing set in Ki2 and in the Karoo sensor), Ki2 publishes the
    built-in `SHIFTING_FRONT_GEAR` / `SHIFTING_REAR_GEAR` (index, max, TEETH) and `SHIFTING_BATTERY`
    (`SHIFTING_BATTERY_STATUS` = BatteryStatus ordinal) — source: valterc/ki2 `karoo/device/ShiftingDevice.kt`.
    **Verified 2026-11-07 13:45–13:48** after the user added the sensor: log `FRONT_GEAR = {..., TEETH=52.0}`,
    `REAR_GEAR = {GEAR=8, MAX=12, TEETH=15.0}`, `SHIFTING_BATTERY = {FIELD_SHIFTING_BATTERY_STATUS_ID=1.0}` (GOOD);
    the Ride Meter showed `Gear 52 / 14` live. This also confirms the BatteryStatus-ordinal assumption for the stream
    (seen via Ki2; AXS itself still unseen). The status arrives in the generic `SHIFTING_BATTERY_STATUS` field,
    which the Ride Meter reads as fallback. **Verified on the device 2026-11-07 13:44** (S-Works profile):
    log `first TYPE_EXT::ki2::DATATYPE_SHIFTING_BATTERY_PERCENTAGE = {FIELD_SINGLE_ID=70.0}`, field showed `Di2 70%`
    in yellow, matching Ki2's own "Shifting Battery %" field (70).
  - **SRAM AXS**: icon filled to the level + `AXS <LEVEL>`; unknown = grey outline + `SHIFT --`.
  The Karoo sends a level, not a percentage (`SHIFTING_BATTERY`, rear derailleur field; SDK `BatteryStatus`):
  NEW > 95 % (full, turquoise), GOOD 81–95 (turquoise), OK 46–80 (yellow), LOW 16–45 (pink), CRITICAL ≤ 15 (red);
  grey outline + `AXS --` when unknown. The raw value is **assumed** to be the BatteryStatus ordinal (0–4, 5 = invalid);
  values > 5 are read as a percentage. **Unverified** — check `first TYPE_SHIFTING_BATTERY... = {...}` in the log.
  On 2026-11-07 13:21 the AXS sent nothing (asleep), so the field showed `AXS --`.
- Ring colours (slope/cadence): slope blue (< −2 %), turquoise (flat), yellow (2–5), pink (5–8), rose red (≥ 8);
  cadence yellow (< 70), turquoise (70–99), purple (100+), grey when 0 / no data.
- Streams: SPEED, DISTANCE, RIDE_TIME, ELEVATION_GRADE, CADENCE, SHIFTING_BATTERY, Ki2 battery %,
  SHIFTING_FRONT_GEAR / SHIFTING_REAR_GEAR (TEETH fields), plus every stream a field can show (all subscribed).
  Clock from the Karoo's own time. BATTERY_PERCENT (Karoo battery) is no longer used.
- **Units:** grade % and elevation m confirmed from the 2026-11-07 ride file. Still **unverified**: ride time
  (ms assumed), W/kg, AXS gear teeth and battery (Di2 via Ki2 verified). `GaugeViewSession` logs the
  first raw values of every stream (`first <type> = {...}`).
- User's bike (from the 2026-11-06 ride file): SRAM AXS 1×12, 40T chainring, cassette 11…32+ (gears 1–2 unseen),
  mostly on the 15/13/17.


## Latest APK

```
~/Downloads/karoo-ride-meter/app/build/outputs/apk/debug/app-debug.apk
```
Built 2026-11-07 (1.0.0) · 3,695,643 bytes ·
SHA-256 `0544a7d979df9b89330ad002d3fa37244872af6deecb8ac62a3c32ffc102f2f8`

Build and test:
```bash
cd ~/Downloads/karoo-ride-meter
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew testDebugUnitTest assembleDebug
```
Requires a GitHub token with `read:packages` in `~/.gradle/gradle.properties` (`gpr.user`, `gpr.key`).

## Tests (Mac)

**18 / 18 passing** (`./gradlew testDebugUnitTest`):

| Test class | Tests | Covers |
|---|---|---|
| `RideMeterTest` | 13 | Texts/colours, every field choice, AXS level mapping, AXS/Di2 battery choice and Ki2 id, tick steps, previews at 7 sizes + every battery level (PNGs in `app/build/gauge-previews/`), dial and circles inside the field, lit/dim ticks, red pointer, ring colours |
| `SettingsActivityTest` | 3 | Default scale 60 + labels, saving a scale, default fields and saving a choice |
| `ZonesTest` | 2 | Power zones from FTP / profile, HR zones from max HR / profile |

Rendering tests use Robolectric native graphics (real `android.graphics` on the Mac).

## Installation & testing status on the Karoo 3

- Installed: **1.0.0**, 2026-11-07 13:58:27, together with Karoo Graphic Meter 0.9.0 (which no longer has the
  Ride Meter). Extension connected (`KarooRideMeter: Karoo system connected: true`), no crash of this app.
- **Seen on the device 2026-11-07 14:04** (S-Works profile, field re-added, 478×721 page): dial, pointer,
  `Di2 70%`, `Gear 52 / 14`, round fields Cadence / HR / Power 3s (chosen by the user in Ride Meter Settings).
  Log: Ki2 battery `70.0`, `SHIFTING_BATTERY_STATUS = 1.0`, front 52 T (2/2), rear 14 T (9/12).
- **Watch:** at 14:02:02, while the user was editing pages, the Karoo's own ride app (`io.hammerhead.rideapp`)
  crashed with `OutOfMemoryError` (128 MB heap limit) while reading an incoming Binder parcel (`Parcel.readStringList`
  in a timeseries data model). It restarted by itself (14:04:21). Not shown to be caused by this app: our processes
  kept running, and it was the only OOM in the crash log since 10:04 (that included a 2 h ride with Graphic Meter 0.7.0).
  If it happens again, suspects are the size/rate of the graphical frames (Ride Meter frame ≈ 364×549 RGB_565
  ≈ 400 KB, up to 2/s) and the many stream subscriptions; options: lower MAX_PIXELS or the frame rate.
- Verified as part of Graphic Meter 0.8.0/0.8.1 on 2026-11-07: dial and round fields render (478×721 page);
  Di2 battery (`Di2 70%`) and gear (`Gear 52 / 14`) via Ki2 on the S-Works profile.

## Next steps

1. Install, re-add the Ride Meter field on the Diverge and S-Works pages, set the three fields in "Ride Meter Settings".
2. With the AXS awake (Diverge): check `first TYPE_SHIFTING_BATTERY...` and the gear teeth in the log
   (`adb logcat -s KarooRideMeter`).
3. On a ride: readability of the dial and round fields in daylight; ring colours with live cadence/power/HR.
