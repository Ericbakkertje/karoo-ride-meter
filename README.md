# Karoo Ride Meter

A combined graphical data field for the Hammerhead Karoo 3, built on the official
[`io.hammerhead:karoo-ext`](https://github.com/hammerheadnav/karoo-ext) SDK (1.1.9):

- speed on a 270° dial face with numbers and a red pointer,
- gear (front / rear teeth) and distance,
- shifting battery top-left: SRAM AXS level from the Karoo, or Shimano Di2 % from the
  [Ki2](https://github.com/valterc/ki2) extension,
- three round fields you choose in "Ride Meter Settings" (slope, cadence, power, HR, …).

Split off from Karoo Graphic Meter (`~/Downloads/karoo-graphic-meter`). Status: see `PROJECT_STATUS.md`.

## Build

Requirements: JDK 17, Android SDK (platform 34), and a GitHub token with `read:packages` in
`~/.gradle/gradle.properties` (`gpr.user`, `gpr.key`).

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew testDebugUnitTest assembleDebug
```

## Install on Karoo 3

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then add the field from the page editor: **Ride Meter → Ride Meter**. For Shimano Di2 gear data, add the
Di2 in the Karoo as a sensor (Sensors → + → Extensions → Ki2).
