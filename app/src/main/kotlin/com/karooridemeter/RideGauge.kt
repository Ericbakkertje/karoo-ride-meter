package com.karooridemeter

import java.util.Locale
import kotlin.math.roundToInt

/**
 * AXS derailleur battery as the Karoo reports it (SHIFTING_BATTERY stream): a level, not a percentage.
 * SDK BatteryStatus mapping: NEW > 95 %, GOOD 81-95, OK 46-80, LOW 16-45, CRITICAL <= 15.
 * [fill] is how full the battery icon is drawn.
 */
enum class DrivetrainBattery(val fill: Float, val color: Int) {
    NEW(1.0f, GaugePalette.ZONES[1]),
    GOOD(0.85f, GaugePalette.ZONES[1]),
    OK(0.6f, GaugePalette.ZONES[3]),
    LOW(0.3f, GaugePalette.ZONES[4]),
    CRITICAL(0.1f, GaugePalette.ZONES[5]),
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
 * SRAM AXS comes from the Karoo itself as a level; Shimano Di2 comes from the Ki2 extension as a percentage.
 */
data class ShiftBattery(
    /** "AXS" or "Di2". */
    val system: String,
    val level: DrivetrainBattery,
    /** Exact percentage when known (Di2 via Ki2), else null. */
    val percent: Int? = null,
) {
    /** How full the icon is drawn. */
    val fill: Float get() = percent?.let { it.coerceIn(0, 100) / 100f } ?: level.fill
    /** Text after the system name: "87%" or the level name. */
    val text: String get() = percent?.let { "$it%" } ?: level.name

    companion object {
        /** From the Karoo SHIFTING_BATTERY stream (SRAM AXS). */
        fun axs(raw: Double?): ShiftBattery? = DrivetrainBattery.fromRaw(raw)?.let { ShiftBattery("AXS", it) }

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

    @Synchronized
    fun update(axsRaw: Double?, di2Pct: Double?): ShiftBattery? {
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
        return ShiftBattery.axs(axsRaw)
    }

    companion object {
        const val FORGET_AFTER_MS = 2 * 60 * 1000L
    }
}

/** What the line under the speed shows. Chosen in Settings. */
enum class UnderSpeed(val settingsName: String) {
    DISTANCE("Distance (61.2 km)"),
    AVG_SPEED("Average speed (AVG 26.1)"),
    ;

    companion object {
        val DEFAULT = DISTANCE
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** What a Ride Meter box can show. Chosen per box in Settings; [label] is drawn in the box. */
enum class RideSlotValue(val label: String, val settingsName: String) {
    SLOPE("SLOPE %", "Slope %"),
    CLOCK("NOW", "Time of day"),
    CADENCE("CADENCE", "Cadence (rpm)"),
    HEART_RATE("HR", "Heart rate (bpm)"),
    POWER("POWER", "Power (W)"),
    POWER_3S("POWER 3s", "Power, 3-second average (W)"),
    POWER_TO_WEIGHT("W/KG", "Power to weight (W/kg)"),
    AVG_POWER("AVG W", "Average power (W)"),
    NP("NP", "Normalized power (W)"),
    AVG_SPEED("AVG km/h", "Average speed (km/h)"),
    MAX_SPEED("MAX km/h", "Max speed (km/h)"),
    DISTANCE("DIST km", "Distance (km)"),
    RIDE_TIME("RIDE TIME", "Ride time"),
    CLIMB("CLIMB m", "Elevation gain (m)"),
    GEAR("GEAR", "Gear (front / rear teeth)"),
    TEMPERATURE("TEMP °C", "Temperature (°C)"),
    CALORIES("KCAL", "Calories (kcal)"),
    ;

    companion object {
        val DEFAULTS = listOf(SLOPE, CLOCK, CADENCE)
        fun fromName(name: String?, fallback: RideSlotValue) = entries.firstOrNull { it.name == name } ?: fallback
    }
}

/** Live values for one Ride Meter frame, already in display units. Null = not available ("--"). */
data class RideState(
    val kmh: Double?,
    val scaleMaxKmh: Int,
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
    val speedText: String?,
    val unit: String,
    /** Speed position on the 270° arc, 0..1, or null when there is no speed. */
    val position: Float?,
    /** Top of the dial scale in km/h (30, 45, 60 or 90). */
    val scaleMaxKmh: Int,
    val gearText: String,
    /** Value under the speed: distance ("61.2 km") or average speed ("26.1"; km/h is shown above the speed). */
    val underSpeedText: String,
    /** Small label drawn above [underSpeedText] ("AVG"), or null for none (distance). */
    val underSpeedLabel: String? = null,
    /** The three boxes, top/left first. */
    val slots: List<RideSlot>,
    val shiftBattery: ShiftBattery?,
)

/** Turns raw ride values into a [RideModel]. Pure Kotlin, unit-tested on the Mac. */
object RideGauge {
    /** Neutral border for boxes without a value or without color meaning. */
    const val NEUTRAL = 0xFF475569.toInt()

    fun model(s: RideState): RideModel {
        val max = s.scaleMaxKmh.coerceAtLeast(RideSettings.SCALE_OPTIONS_KMH.first())
        return RideModel(
            speedText = s.kmh?.let { oneDecimal(it) },
            unit = "km/h",
            position = s.kmh?.let { (it.coerceIn(0.0, max.toDouble()) / max).toFloat() },
            scaleMaxKmh = max,
            gearText = "${s.frontTeeth ?: "--"} / ${s.rearTeeth ?: "--"}",
            underSpeedText = when (s.underSpeed) {
                UnderSpeed.DISTANCE -> "${s.distanceKm?.let { oneDecimal(it) } ?: "--"} km"
                UnderSpeed.AVG_SPEED -> s.avgKmh?.let { oneDecimal(it) } ?: "--"
            },
            underSpeedLabel = if (s.underSpeed == UnderSpeed.AVG_SPEED) "AVG" else null,
            slots = s.slots.take(3).map { slot(it, s) },
            shiftBattery = s.shiftBattery,
        )
    }

    fun slot(v: RideSlotValue, s: RideState): RideSlot {
        fun box(value: String?, color: Int = NEUTRAL) = RideSlot(v.label, value, if (value == null) NEUTRAL else color)
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
            RideSlotValue.AVG_SPEED -> box(s.avgKmh?.let { oneDecimal(it) })
            RideSlotValue.MAX_SPEED -> box(s.maxKmh?.let { oneDecimal(it) })
            RideSlotValue.DISTANCE -> box(s.distanceKm?.let { oneDecimal(it) })
            RideSlotValue.RIDE_TIME -> box(s.rideTimeSec?.let { duration(it) })
            RideSlotValue.CLIMB -> box(s.climbM?.roundToInt()?.toString())
            RideSlotValue.GEAR -> box(if (s.frontTeeth != null && s.rearTeeth != null) "${s.frontTeeth}/${s.rearTeeth}" else null)
            RideSlotValue.TEMPERATURE -> box(s.temperatureC?.let { oneDecimal(it) })
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
