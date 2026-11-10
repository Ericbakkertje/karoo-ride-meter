package com.karooridemeter

import java.util.Locale
import kotlin.math.roundToInt

/**
 * AXS derailleur battery as the Karoo reports it (SHIFTING_BATTERY stream): a level, not a percentage.
 * SDK BatteryStatus mapping: NEW > 95 %, GOOD 81-95, OK 46-80, LOW 16-45, CRITICAL <= 15.
 * [fill] is how full the battery icon is drawn.
 */
enum class DrivetrainBattery(val fill: Float, val color: Int, val label: String) {
    /** The SDK calls this level NEW; on screen it is FULL (the battery is full, not new). */
    NEW(1.0f, GaugePalette.ZONES[1], "FULL"),
    GOOD(0.85f, GaugePalette.ZONES[1], "GOOD"),
    OK(0.6f, GaugePalette.ZONES[3], "OK"),
    LOW(0.3f, GaugePalette.ZONES[4], "LOW"),
    CRITICAL(0.1f, GaugePalette.ZONES[5], "CRITICAL"),
    ;

    companion object {
        /**
         * Raw stream value to a level. Assumed to be the SDK BatteryStatus ordinal
         * (0 NEW .. 4 CRITICAL, 5 INVALID); values above 5 are treated as a percentage. Unverified on the device.
         */
        fun fromRaw(v: Double?): DrivetrainBattery? {
            if (v == null || v < 0) return null
            val i = v.toInt()
            return when {
                i <= 4 -> entries[i]
                i == 5 -> null
                else -> fromPercent(v)
            }
        }

        /** Percentage to a level, with the SDK's BatteryStatus.fromPercentage thresholds. */
        fun fromPercent(pct: Double): DrivetrainBattery = when {
            pct > 95 -> NEW
            pct > 80 -> GOOD
            pct > 45 -> OK
            pct > 15 -> LOW
            else -> CRITICAL
        }
    }
}

/**
 * Battery of the electronic shifting, shown top-left on the Ride Meter.
 * SRAM AXS (and other systems the Karoo pairs itself, such as Campagnolo) come from the Karoo as a level;
 * Shimano Di2 comes from the Ki2 extension as a percentage.
 */
data class ShiftBattery(
    /** Name shown before the level: "AXS", "Campa", "Batt" (brand unknown) or "Di2"; see [ShiftingBrand]. */
    val system: String,
    val level: DrivetrainBattery,
    /** Exact percentage when known (Di2 via Ki2), else null. */
    val percent: Int? = null,
) {
    /** How full the icon is drawn. */
    val fill: Float get() = percent?.let { it.coerceIn(0, 100) / 100f } ?: level.fill
    /** Text after the system name: "87%" or the level label ("FULL", "GOOD", ...). */
    val text: String get() = percent?.let { "$it%" } ?: level.label

    companion object {
        /** From the Karoo SHIFTING_BATTERY stream; [system] is the brand name to show (default SRAM AXS). */
        fun axs(raw: Double?, system: String = ShiftingBrand.SRAM): ShiftBattery? =
            DrivetrainBattery.fromRaw(raw)?.let { ShiftBattery(system, it) }

        /** From Ki2's DATATYPE_SHIFTING_BATTERY_PERCENTAGE stream (Shimano Di2), 0-100. */
        fun di2(pct: Double?): ShiftBattery? = pct?.takeIf { it in 0.0..100.0 }?.let {
            ShiftBattery("Di2", DrivetrainBattery.fromPercent(it), it.roundToInt())
        }

        /** Di2 (Ki2) wins when it has a value, otherwise AXS, otherwise unknown. */
        fun pick(axsRaw: Double?, di2Pct: Double?): ShiftBattery? = di2(di2Pct) ?: axs(axsRaw)
    }
}

/**
 * Picks the shifting battery to show and remembers Di2.
 *
 * With Ki2, the Di2 also feeds the Karoo's generic SHIFTING_BATTERY status, and Ki2's % stream can pause
 * (seen on a ride: the field then read "AXS GOOD" on the Di2 bike). So once Ki2 reported a %, keep showing
 * "Di2" with the last known % while any shifting battery data keeps coming. Forget it only after no battery
 * data at all for [FORGET_AFTER_MS] while the field is on screen (e.g. after switching to the AXS bike).
 * Gaps while the field is not on screen do not count, so a short "no data yet" at page start is harmless.
 */
class ShiftBatteryTracker(private val now: () -> Long = System::currentTimeMillis) {
    private var lastDi2Pct: Double? = null
    private var silentSince: Long? = null

    /** [axsRaw] is the Karoo's own shifting battery level (any brand); [system] the brand name to show with it. */
    @Synchronized
    fun update(axsRaw: Double?, di2Pct: Double?, system: String = ShiftingBrand.SRAM): ShiftBattery? {
        val t = now()
        val di2 = ShiftBattery.di2(di2Pct)
        if (di2 != null || ShiftBattery.axs(axsRaw) != null) silentSince = null
        if (di2 != null) {
            lastDi2Pct = di2Pct
            return di2
        }
        if (axsRaw == null) {
            val since = silentSince ?: t.also { silentSince = it }
            if (t - since > FORGET_AFTER_MS) lastDi2Pct = null
        }
        lastDi2Pct?.let { return ShiftBattery.di2(it) }
        return ShiftBattery.axs(axsRaw, system)
    }

    companion object {
        const val FORGET_AFTER_MS = 2 * 60 * 1000L
    }
}

/** What the line under the speed shows. Chosen in Settings. */
enum class UnderSpeed(val settingsName: String) {
    DISTANCE("Distance"),
    AVG_SPEED("Average speed"),
    ;

    companion object {
        val DEFAULT = DISTANCE
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** What the top-left and top-right spots above the dial show. Chosen in Settings. */
enum class StatusValue(val settingsName: String) {
    SHIFT_BATTERY("Shifting battery (AXS / Di2)"),
    GEAR("Gear (52 / 17)"),
    TEMPERATURE("Temperature"),
    GRADE("Grade (%)"),
    ALTITUDE("Altitude"),
    CLIMB("Elevation gain"),
    TO_FINISH("Distance to finish (on a route)"),
    NONE("Nothing"),
    ;

    companion object {
        val DEFAULT_LEFT = SHIFT_BATTERY
        val DEFAULT_RIGHT = GEAR
        fun fromName(name: String?, fallback: StatusValue) = entries.firstOrNull { it.name == name } ?: fallback
    }
}

/** One spot in the status row: the battery block, or a value with a small label under it. */
sealed class StatusItem {
    data class Battery(val battery: ShiftBattery?) : StatusItem()
    data class Value(val text: String, val label: String, val known: Boolean) : StatusItem()
}

/** What a Ride Meter box can show. Chosen per box in Settings; [label] is drawn in the box. */
enum class RideSlotValue(val label: String, val settingsName: String) {
    SLOPE("SLOPE %", "Slope %"),
    SPEED("SPEED", "Speed"),
    CLOCK("NOW", "Time of day"),
    CADENCE("CADENCE", "Cadence (rpm)"),
    HEART_RATE("HR", "Heart rate (bpm)"),
    POWER("POWER", "Power (W)"),
    POWER_3S("POWER 3s", "Power, 3-second average (W)"),
    POWER_TO_WEIGHT("W/KG", "Power to weight (W/kg)"),
    AVG_POWER("AVG W", "Average power (W)"),
    NP("NP", "Normalized power (W)"),
    AVG_SPEED("AVG km/h", "Average speed"),
    MAX_SPEED("MAX km/h", "Max speed"),
    DISTANCE("DIST km", "Distance"),
    RIDE_TIME("RIDE TIME", "Ride time"),
    CLIMB("CLIMB m", "Elevation gain"),
    GEAR("GEAR", "Gear (front / rear teeth)"),
    TEMPERATURE("TEMP °C", "Temperature"),
    CALORIES("KCAL", "Calories (kcal)"),
    ;

    companion object {
        val DEFAULTS = listOf(SLOPE, CLOCK, CADENCE)
        fun fromName(name: String?, fallback: RideSlotValue) = entries.firstOrNull { it.name == name } ?: fallback
    }
}

/**
 * What the big dial shows. Each value is its own data field on the Karoo ([typeId]), so a page can show
 * speed and another page power. Speed uses the scale chosen in Settings; the others scale themselves.
 */
enum class DialValue(val typeId: String) {
    SPEED("ride-meter"),
    POWER("ride-meter-power"),
    POWER_3S("ride-meter-power-3s"),
    HEART_RATE("ride-meter-heart-rate"),
    CADENCE("ride-meter-cadence"),
    ;

    /** The round field with the same value; in this field it makes way for speed. */
    val slot: RideSlotValue?
        get() = when (this) {
            SPEED -> null
            POWER -> RideSlotValue.POWER
            POWER_3S -> RideSlotValue.POWER_3S
            HEART_RATE -> RideSlotValue.HEART_RATE
            CADENCE -> RideSlotValue.CADENCE
        }
}

/**
 * Units from the Karoo profile (Settings > units on the Karoo). Distance also sets speed (mph),
 * elevation sets altitude and climb (ft), temperature sets °F.
 */
data class Units(
    val imperialDistance: Boolean = false,
    val imperialElevation: Boolean = false,
    val imperialTemperature: Boolean = false,
) {
    val speed get() = if (imperialDistance) "mph" else "km/h"
    val distance get() = if (imperialDistance) "mi" else "km"
    val elevation get() = if (imperialElevation) "ft" else "m"
    val temperature get() = if (imperialTemperature) "°F" else "°C"

    /** km (or km/h) to the chosen distance (or speed) unit. */
    fun dist(km: Double) = if (imperialDistance) km / KM_PER_MILE else km
    fun elev(m: Double) = if (imperialElevation) m * FEET_PER_METRE else m
    fun temp(c: Double) = if (imperialTemperature) c * 9.0 / 5.0 + 32.0 else c

    companion object {
        val METRIC = Units()
        val IMPERIAL = Units(true, true, true)
        const val KM_PER_MILE = 1.609344
        const val FEET_PER_METRE = 3.28084

        /** Speed scales in mph that match the km/h choices in Settings (30, 45, 60, 90 km/h). */
        fun mphScale(kmhScale: Int) = when {
            kmhScale <= 30 -> 20
            kmhScale <= 45 -> 30
            kmhScale <= 60 -> 40
            else -> 60
        }
    }
}

/** Live values for one Ride Meter frame. Speed and distance in km(/h), elevation in m, temperature in °C; [units] converts. Null = not available ("--"). */
data class RideState(
    val kmh: Double?,
    val scaleMaxKmh: Int,
    /** What the dial shows (which Ride Meter field this is). */
    val dial: DialValue = DialValue.SPEED,
    val units: Units = Units.METRIC,
    /** From the Karoo profile, for the power and heart rate scales; null or 0 = unknown. */
    val ftp: Int? = null,
    val maxHr: Int? = null,
    val avgHeartRate: Int? = null,
    val avgCadence: Int? = null,
    val frontTeeth: Int? = null,
    val rearTeeth: Int? = null,
    val distanceKm: Double? = null,
    val rideTimeSec: Long? = null,
    /** Shifting battery (AXS or Di2); null when unknown (asleep / not paired). */
    val shiftBattery: ShiftBattery? = null,
    /** Current time of day, already formatted (e.g. "12:44"). */
    val clock: String = "--",
    /** What the line under the speed shows. */
    val underSpeed: UnderSpeed = UnderSpeed.DEFAULT,
    /** What the three boxes show (top/left first). */
    val slots: List<RideSlotValue> = RideSlotValue.DEFAULTS,
    /** What the spots above the dial show. */
    val statusLeft: StatusValue = StatusValue.DEFAULT_LEFT,
    val statusRight: StatusValue = StatusValue.DEFAULT_RIGHT,
    /** Karoo in light mode (light card, always the bright colours). */
    val light: Boolean = false,
    /** Bright ring colours in dark mode (chosen in Settings). */
    val brightColors: Boolean = false,
    val altitudeM: Double? = null,
    val toFinishKm: Double? = null,
    val gradePct: Double? = null,
    val cadenceRpm: Int? = null,
    val heartRate: Int? = null,
    val power: Int? = null,
    val power3s: Int? = null,
    val powerToWeight: Double? = null,
    val avgPower: Int? = null,
    val np: Int? = null,
    val avgKmh: Double? = null,
    val maxKmh: Double? = null,
    val climbM: Double? = null,
    val temperatureC: Double? = null,
    val calories: Int? = null,
    /** For zone colors; empty = no colors. */
    val powerZones: List<PowerZone> = emptyList(),
    val hrZones: List<HrZone> = emptyList(),
)

/** One box: label, value text (null = "--") and border color. */
data class RideSlot(val label: String, val value: String?, val color: Int)

/** Everything one Ride Meter frame shows, as display-ready text and colors. */
data class RideModel(
    /** Big value in the middle (speed, power, heart rate or cadence); [unit] is drawn above it. */
    val speedText: String?,
    val unit: String,
    /** Value position on the 270° arc, 0..1, or null when there is no value. */
    val position: Float?,
    /** Bottom and top of the dial scale, in the dial's unit (speed 0-60 km/h, heart rate 60-200 bpm, ...). */
    val scaleMax: Int,
    val scaleMin: Int = 0,
    val gearText: String,
    /** Value under it: distance ("61.2 km") or an average ("26.08"; the unit is shown above the big value). */
    val underSpeedText: String,
    /** Small label drawn above [underSpeedText] ("AVG"), or null for none (distance). */
    val underSpeedLabel: String? = null,
    /** The three boxes, top/left first. */
    val slots: List<RideSlot>,
    val shiftBattery: ShiftBattery?,
    /** Top-left and top-right spots; null = empty. Both null = no status row (the dial moves up). */
    val statusLeft: StatusItem? = StatusItem.Battery(shiftBattery),
    val statusRight: StatusItem? = null,
    val theme: RideTheme = RideTheme.DARK,
)

/** Turns raw ride values into a [RideModel]. Pure Kotlin, unit-tested on the Mac. */
object RideGauge {
    /** Neutral border for boxes without a value or without color meaning. */
    const val NEUTRAL = 0xFF475569.toInt()

    fun model(s: RideState): RideModel {
        val u = s.units
        val (scaleMin, scaleMax) = scale(s)
        val value: Double? = when (s.dial) {
            DialValue.SPEED -> s.kmh?.let { u.dist(it) }
            DialValue.POWER -> s.power?.toDouble()
            DialValue.POWER_3S -> s.power3s?.toDouble()
            DialValue.HEART_RATE -> s.heartRate?.toDouble()
            DialValue.CADENCE -> s.cadenceRpm?.toDouble()
        }
        val (underText, underLabel) = if (s.dial == DialValue.SPEED) {
            when (s.underSpeed) {
                UnderSpeed.DISTANCE -> "${s.distanceKm?.let { oneDecimal(u.dist(it)) } ?: "--"} ${u.distance}" to null
                UnderSpeed.AVG_SPEED -> (s.avgKmh?.let { String.format(Locale.US, "%.2f", u.dist(it)) } ?: "--") to "AVG"
            }
        } else {
            val avg = when (s.dial) {
                DialValue.POWER, DialValue.POWER_3S -> s.avgPower
                DialValue.HEART_RATE -> s.avgHeartRate
                else -> s.avgCadence
            }
            (avg?.toString() ?: "--") to "AVG"
        }
        // In a power, heart rate or cadence field, speed takes the first round field and the dial's own value
        // is left out of the others.
        val slotValues = if (s.dial == DialValue.SPEED) s.slots.take(3) else listOf(RideSlotValue.SPEED) + s.slots.filter { it != s.dial.slot }.take(2)
        return RideModel(
            speedText = value?.let { if (s.dial == DialValue.SPEED) oneDecimal(it) else it.roundToInt().toString() },
            unit = when (s.dial) {
                DialValue.SPEED -> u.speed
                DialValue.POWER, DialValue.POWER_3S -> "watt"
                DialValue.HEART_RATE -> "bpm"
                DialValue.CADENCE -> "rpm"
            },
            position = value?.let { ((it.coerceIn(scaleMin.toDouble(), scaleMax.toDouble()) - scaleMin) / (scaleMax - scaleMin)).toFloat() },
            scaleMax = scaleMax,
            scaleMin = scaleMin,
            gearText = "${s.frontTeeth ?: "--"} / ${s.rearTeeth ?: "--"}",
            underSpeedText = underText,
            underSpeedLabel = underLabel,
            slots = slotValues.map { slot(it, s) },
            shiftBattery = s.shiftBattery,
            statusLeft = status(s.statusLeft, s),
            statusRight = status(s.statusRight, s),
            theme = RideTheme(light = s.light, brightColors = s.brightColors),
        )
    }

    /** Lowest heart rate on the dial; below it the needle rests at the start. */
    const val HR_SCALE_MIN = 60
    const val CADENCE_SCALE_MAX = 140
    /** Power scale when the FTP is unknown. */
    const val DEFAULT_POWER_SCALE = 400

    /**
     * Dial scale (bottom, top) in the dial's unit.
     * Speed: the Settings choice (or the matching mph scale). Power: twice the FTP, rounded up to 100 W
     * (300-1000 W). Heart rate: 60 up to 200 bpm, or the max heart rate rounded up to 20 if that is higher.
     * Cadence: 0-140 rpm (0 stays on the dial for coasting).
     */
    fun scale(s: RideState): Pair<Int, Int> = when (s.dial) {
        DialValue.SPEED -> {
            val kmh = s.scaleMaxKmh.coerceAtLeast(RideSettings.SCALE_OPTIONS_KMH.first())
            0 to if (s.units.imperialDistance) Units.mphScale(kmh) else kmh
        }
        DialValue.POWER, DialValue.POWER_3S -> 0 to (s.ftp?.takeIf { it > 0 }?.let { ((it * 2 + 99) / 100 * 100).coerceIn(300, 1000) } ?: DEFAULT_POWER_SCALE)
        DialValue.HEART_RATE -> HR_SCALE_MIN to maxOf(200, s.maxHr?.takeIf { it > 0 }?.let { (it + 19) / 20 * 20 } ?: 0).coerceAtMost(240)
        DialValue.CADENCE -> 0 to CADENCE_SCALE_MAX
    }

    fun status(v: StatusValue, s: RideState): StatusItem? {
        val u = s.units
        fun value(text: String?, label: String) = StatusItem.Value(text ?: "--", label, text != null)
        return when (v) {
            StatusValue.SHIFT_BATTERY -> StatusItem.Battery(s.shiftBattery)
            StatusValue.GEAR -> StatusItem.Value(
                "${s.frontTeeth ?: "--"} / ${s.rearTeeth ?: "--"}", "GEAR", s.frontTeeth != null && s.rearTeeth != null,
            )
            StatusValue.TEMPERATURE -> value(s.temperatureC?.let { oneDecimal(u.temp(it)) }, "TEMP ${u.temperature}")
            StatusValue.GRADE -> value(s.gradePct?.let { oneDecimal(it) }, "GRADE %")
            StatusValue.ALTITUDE -> value(s.altitudeM?.let { u.elev(it).roundToInt().toString() }, "ALT ${u.elevation}")
            StatusValue.CLIMB -> value(s.climbM?.let { u.elev(it).roundToInt().toString() }, "CLIMB ${u.elevation}")
            StatusValue.TO_FINISH -> value(s.toFinishKm?.let { oneDecimal(u.dist(it)) }, "TO FINISH ${u.distance}")
            StatusValue.NONE -> null
        }
    }

    fun slot(v: RideSlotValue, s: RideState): RideSlot {
        val u = s.units
        val label = when (v) {
            RideSlotValue.AVG_SPEED -> "AVG ${u.speed}"
            RideSlotValue.MAX_SPEED -> "MAX ${u.speed}"
            RideSlotValue.DISTANCE -> "DIST ${u.distance}"
            RideSlotValue.CLIMB -> "CLIMB ${u.elevation}"
            RideSlotValue.TEMPERATURE -> "TEMP ${u.temperature}"
            else -> v.label
        }
        fun box(value: String?, color: Int = NEUTRAL) = RideSlot(label, value, if (value == null) NEUTRAL else color)
        return when (v) {
            RideSlotValue.SLOPE -> box(s.gradePct?.let { oneDecimal(it) }, s.gradePct?.let { slopeColor(it) } ?: NEUTRAL)
            RideSlotValue.CLOCK -> box(s.clock)
            RideSlotValue.CADENCE -> box(s.cadenceRpm?.toString(), s.cadenceRpm?.let { cadenceColor(it) } ?: NEUTRAL)
            RideSlotValue.HEART_RATE -> box(s.heartRate?.toString(), s.heartRate?.let { hrColor(it, s.hrZones) } ?: NEUTRAL)
            RideSlotValue.POWER -> box(s.power?.toString(), s.power?.let { powerColor(it, s.powerZones) } ?: NEUTRAL)
            RideSlotValue.POWER_3S -> box(s.power3s?.toString(), s.power3s?.let { powerColor(it, s.powerZones) } ?: NEUTRAL)
            RideSlotValue.POWER_TO_WEIGHT -> box(s.powerToWeight?.let { String.format(Locale.US, "%.2f", it) })
            RideSlotValue.AVG_POWER -> box(s.avgPower?.toString(), s.avgPower?.let { powerColor(it, s.powerZones) } ?: NEUTRAL)
            RideSlotValue.NP -> box(s.np?.toString(), s.np?.let { powerColor(it, s.powerZones) } ?: NEUTRAL)
            RideSlotValue.SPEED -> box(s.kmh?.let { oneDecimal(u.dist(it)) })
            RideSlotValue.AVG_SPEED -> box(s.avgKmh?.let { oneDecimal(u.dist(it)) })
            RideSlotValue.MAX_SPEED -> box(s.maxKmh?.let { oneDecimal(u.dist(it)) })
            RideSlotValue.DISTANCE -> box(s.distanceKm?.let { oneDecimal(u.dist(it)) })
            RideSlotValue.RIDE_TIME -> box(s.rideTimeSec?.let { duration(it) })
            RideSlotValue.CLIMB -> box(s.climbM?.let { u.elev(it).roundToInt().toString() })
            RideSlotValue.GEAR -> box(if (s.frontTeeth != null && s.rearTeeth != null) "${s.frontTeeth}/${s.rearTeeth}" else null)
            RideSlotValue.TEMPERATURE -> box(s.temperatureC?.let { oneDecimal(u.temp(it)) })
            RideSlotValue.CALORIES -> box(s.calories?.toString())
        }
    }

    /** Descent blue, flat turquoise, then yellow, pink and rose red as it gets steeper. */
    fun slopeColor(pct: Double): Int {
        val z = GaugePalette.ZONES
        return when {
            pct < -2.0 -> z[0]
            pct < 2.0 -> z[1]
            pct < 5.0 -> z[3]
            pct < 8.0 -> z[4]
            else -> z[5]
        }
    }

    /** Low cadence yellow, 70-99 rpm turquoise, 100+ purple; 0 (coasting) neutral. */
    fun cadenceColor(rpm: Int): Int {
        val z = GaugePalette.ZONES
        return when {
            rpm <= 0 -> NEUTRAL
            rpm < 70 -> z[3]
            rpm < 100 -> z[1]
            else -> z[6]
        }
    }

    /** Color of the power zone (same colors as the Power Meter). */
    fun powerColor(watts: Int, zones: List<PowerZone>): Int =
        if (zones.isEmpty()) NEUTRAL else GaugePalette.ZONES[PowerZones.zoneIndex(watts, zones).coerceIn(0, 6)]

    /** Color of the heart rate zone (same colors as the Heart Rate Meter). */
    fun hrColor(bpm: Int, zones: List<HrZone>): Int =
        if (zones.isEmpty()) NEUTRAL else GaugePalette.segmentColors(zones.size)[HeartRateZones.zoneIndex(bpm, zones)]

    /** "0:46", "2:21" (hours:minutes). */
    fun duration(sec: Long): String {
        val s = sec.coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", s / 3600, (s % 3600) / 60)
    }

    private fun oneDecimal(v: Double) = String.format(Locale.US, "%.1f", v)
}
