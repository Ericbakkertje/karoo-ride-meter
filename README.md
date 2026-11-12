# Karoo Ride Meter

A combined graphical data field for the Hammerhead Karoo 3 (also reported working on the Karoo 2), built on the official
[`io.hammerhead:karoo-ext`](https://github.com/hammerheadnav/karoo-ext) SDK (1.1.9):

- speed on a 270° dial face with numbers and a red pointer — or power, power 3s, heart rate or cadence: each is its
  own field (**Ride Meter Power**, **Ride Meter Power 3s**, **Ride Meter Heart Rate**, **Ride Meter Cadence**), with a
  scale that fits (power from your FTP, heart rate 60–200, cadence 0–140) and speed in the first round field,
- two spots above the dial, chosen in Settings: by default the shifting battery on the left (SRAM AXS level from
  the Karoo, or Shimano Di2 % from the [Ki2](https://github.com/valterc/ki2) extension) and the gear (front / rear
  teeth) on the right; or distance, distance to finish, temperature, grade, altitude, elevation gain, or nothing
  (for mechanical shifting; with both set to nothing the dial moves up),
- distance or average speed under the speed (your choice), in big numbers,
- on the power dials, the ring fills up to the needle in your power-zone colours (from your FTP and zones in the
  Karoo settings under **Training metrics**),
- three round fields you choose in "Ride Meter Settings" (slope, cadence, power, HR, …), coloured by your zones;
  shown as a number in a coloured circle or as small dials with a needle,
- colours: normal (neon), bright, or classic (blue, green, yellow, orange, real red),
- follows the Karoo's light or dark mode,
- follows the Karoo's units: mph, mi, ft and °F when the Karoo is set to imperial.

| Full page | Light mode | Half page |
|---|---|---|
| ![Ride Meter, full page](docs/ride_meter_cadence_hr_power3s_478x721.png) | ![Ride Meter in light mode](docs/ride_meter_light_478x721.png) | ![Ride Meter, half page](docs/ride_meter_cadence_hr_power3s_478x456.png) |

| Power | Heart rate | Imperial |
|---|---|---|
| ![Ride Meter Power](docs/ride_meter_power_478x721.png) | ![Ride Meter Heart Rate](docs/ride_meter_heart_rate_478x721.png) | ![Ride Meter in mph](docs/ride_meter_imperial_478x721.png) |

| Small dials | Classic colours | Average speed |
|---|---|---|
| ![Round fields as small dials](docs/ride_meter_mini_dials_478x721.png) | ![Ride Meter Power, classic colours](docs/ride_meter_classic_power_478x721.png) | ![Average speed under the speed](docs/ride_meter_avg_speed_478x721.png) |

_Rendered by the app's own drawing code (version 1.4.0) with example values. Overview: [docs/ride_meter_info_1.4.0.png](docs/ride_meter_info_1.4.0.png)._

Split off from Karoo Graphic Meter. Status: see `PROJECT_STATUS.md`. Testers: see `TESTERS.md`.

## Support

Karoo Ride Meter is free and made in spare time. If you enjoy it and want to say thanks, you can buy me a coffee.
Completely optional, feedback and ideas are just as welcome!

[![Support me on Ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/karooridemeter)

## Build

Requirements: JDK 17, Android SDK (platform 34), and a GitHub token with `read:packages` in
`~/.gradle/gradle.properties` (`gpr.user`, `gpr.key`).

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew testDebugUnitTest assembleDebug
```

## Release build (for testers)

Needs `keystore.properties` in the project root (git-ignored) pointing to the release key:

```properties
storeFile=/path/to/karoo-release.jks
storePassword=...
keyAlias=karoo
keyPassword=...
```

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`. Tester notes: `TESTERS.md`.

## Install on the Karoo

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then add the field from the page editor: **Ride Meter → Ride Meter** (or Ride Meter Power / Power 3s / Heart Rate / Cadence). For Shimano Di2 gear data, add the
Di2 in the Karoo as a sensor (Sensors → + → Extensions → Ki2).

## License

Copyright (C) 2026 Ericbakkertje

Karoo Ride Meter is free software: you can redistribute it and/or modify it under the terms of the
[GNU General Public License v3.0](LICENSE) as published by the Free Software Foundation. It comes with
no warranty.
