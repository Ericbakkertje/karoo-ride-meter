# Karoo Ride Meter — Project Status

_Last updated: 2026-11-07 — 1.1.1: average speed under the speed with two decimals (1.1.0: status row, distance / average speed choice) (branch `feature/gear-top-row`)._

## Version

| | |
|---|---|
| App version | **1.1.1** (`versionCode 4`) |
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

## 1.1.0 layout changes (approved from previews on 2026-11-07)

- **Status row** above the dial: battery block on the left (icon + `Di2 70%` / `AXS GOOD`, centred on each other,
  starting at the dial's left edge) and gear block on the right (`52 / 14` with `GEAR` centred under it, ending at
  the dial's right edge). On full-page fields (h ≥ 600) the row starts below the top 6 %, where the Karoo's
  ride bar (time / paused) covers the field; the dial and circles move down to make room.
- The gear line inside the dial is gone. **Under the speed** (Settings, "Under the speed"): **Distance** (default,
  `142.7 km`) or **Average speed** (small grey `AVG` label above `26.08` — two decimals since 1.1.1; km/h is already shown above the speed;
  the round field "Average speed" keeps one decimal).
  A larger distance in the bottom gap of the dial was tried and rejected by the user (kept the small size).

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


## Release APK for testers (1.1.1)

```
~/Downloads/karoo-ride-meter/dist/KarooRideMeter-1.1.1.apk   (+ dist/TESTERS.md, install notes in Dutch)
```
Release build (`./gradlew assembleRelease`, not debuggable, no minify), 2,925,508 bytes ·
SHA-256 `881bd270c744faa552d8cca89cadcdf3d3e239cfe3ca97dc7d5dc2add8aa9082`.
Signed (v1 + v2) with the **release key** `~/Downloads/karoo-signing/karoo-release.jks` (alias `karoo`,
certificate SHA-256 `5a8c8cbe…415ae4`). Passwords are in `keystore.properties` (project root, git-ignored) and a
backup copy in `~/Downloads/karoo-signing/`. **Back up that folder**: every future release must be signed with this
key, or testers cannot update over their installed version.
**Installed on the developer's Karoo 2026-11-07 15:29:21** (debug build uninstalled first; on-device APK SHA-256
matches `dist/`, not debuggable, extension connected, no crash). Settings were reset by the uninstall; before it
they were: scale 0–90, fields Cadence / HR / Power 3s, under the speed = distance (default).
From now on install release builds there (`adb install -r dist/...apk`); a debug build no longer installs over it.
Seen on screen 15:31 (478×721, release build): field re-added via the picker (preview 478×642 at 15:30:54),
live speed 1.7 km/h, scale 0–60, under the speed = AVG (`--`, no ride recording), fields Cadence / HR / Power 3s,
`SHIFT --` and `-- / --` (shifting asleep). No errors.

## Latest APK

```
~/Downloads/karoo-ride-meter/app/build/outputs/apk/debug/app-debug.apk
```
Built 2026-11-07 (1.1.1) · 3,701,093 bytes ·
SHA-256 `6e860cada18f2e4f731187b664405a277d7c4fbba1c67e7122e7d2cdb683e8c4`

Build and test:
```bash
cd ~/Downloads/karoo-ride-meter
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew testDebugUnitTest assembleDebug
```
Requires a GitHub token with `read:packages` in `~/.gradle/gradle.properties` (`gpr.user`, `gpr.key`).

## Tests (Mac)

**22 / 22 passing** (`./gradlew testDebugUnitTest`):

| Test class | Tests | Covers |
|---|---|---|
| `RideMeterTest` | 16 | Texts/colours, Di2 memory when Ki2's % pauses (and forgetting it after 2 min of silence), every field choice, AXS level mapping, AXS/Di2 battery choice and Ki2 id, tick steps, previews at 7 sizes + every battery level (PNGs in `app/build/gauge-previews/`), dial and circles inside the field, lit/dim ticks, red pointer, ring colours |
| `SettingsActivityTest` | 4 | Default scale 60 + labels, saving a scale, under-the-speed choice, default fields and saving a choice |
| `ZonesTest` | 2 | Power zones from FTP / profile, HR zones from max HR / profile |

Rendering tests use Robolectric native graphics (real `android.graphics` on the Mac).

## Installation & testing status on the Karoo 3

- Installed: **1.1.1 release** (signed with the release key), 2026-11-07 15:29:21. Before: 1.1.1 debug 15:17:10, 1.1.0 debug 15:09:30 (extension connected, no crash). Seen on screen 15:11 (478×721, scale 0–90): status row with
  `SHIFT --` left and `-- / --` GEAR right (shifting asleep), `-- km` under the speed, round fields; layout as in the previews.
  Before: 1.0.1, 2026-11-07 14:47:22 (after the short ride was saved; extension reconnected, no crash).
  Earlier: 1.0.0 at 13:58:27, together with Karoo Graphic Meter 0.9.0 (which no longer has the
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

## Short ride 2026-11-07 (14:38–14:45, S-Works / Di2, no HR strap; saved 14:45, FIT `0b190b95…`)

- No crash of either app, and no new crash of the Karoo ride app (only the 14:02 OOM in the crash log).
- First values seen: power 51 W (3s/10s/30s 51), avg power 74, max 74, W/kg 0.689 (= 51 W / 74 kg, so **W/kg confirmed**),
  NP first value `4.9E-324` (Double.MIN_VALUE, the Karoo's "no value yet"; shown as 0), cadence first value 0
  (the FIT file has cadence: avg 46, max 55 rpm, so it did stream later), max speed 3.70 m/s (13.3 km/h), calories 0.16 (fractional kcal), ride time 0 at start.
- Screen at 14:43 (paused, 0:00:53, 0.2 km): dial scale **0–90** (chosen by the user), `Gear 52 / 19`, round fields `--`.
- **Bug:** the battery read **`AXS GOOD` on the Di2 bike**. Ki2's % stream was not streaming at that moment, so the
  Ride Meter fell back to the generic `SHIFTING_BATTERY` stream, which Ki2 also feeds (Di2 added as a Karoo sensor)
  and which the code always labels "AXS". **Fixed in 1.0.1** (`ShiftBatteryTracker`): once Ki2 reported a %, the field
  keeps "Di2" and the last known % while any shifting battery data keeps coming; it forgets Di2 only after 2 minutes
  with no battery data at all while the field is on screen (e.g. after switching to the AXS bike).
- The battery **icon** was hidden under the Karoo's yellow paused/recording bar at the top of the screen; only the
  text line below it was visible.
- Only first values are logged, so live values during the ride cannot be checked afterwards.
- FIT: 53 s moving, 0.21 km, avg 13.9 km/h, grade −1.1 … +0.6 %, altitude 0.0–0.2 m.

## Next steps

1. Install, re-add the Ride Meter field on the Diverge and S-Works pages, set the three fields in "Ride Meter Settings".
2. With the AXS awake (Diverge): check `first TYPE_SHIFTING_BATTERY...` and the gear teeth in the log
   (`adb logcat -s KarooRideMeter`).
3. On a ride: readability of the dial and round fields in daylight; ring colours with live cadence/power/HR.
