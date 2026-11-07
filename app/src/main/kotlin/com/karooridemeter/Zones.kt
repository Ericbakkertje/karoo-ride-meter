package com.karooridemeter

import kotlin.math.roundToInt

/** One power zone in watts, as reported by the Karoo user profile (min/max inclusive). */
data class PowerZone(val min: Int, val max: Int)

/**
 * Seven-zone power model for the gauge. Pure Kotlin so it can be unit-tested on the Mac.
 *
 * The gauge has seven equal segments (one per zone). A power value is placed inside its
 * zone's segment proportionally to where it sits between that zone's lower bound and
 * the next zone's lower bound.
 */
object PowerZones {
    const val ZONE_COUNT = 7

    /** Only used if the Karoo profile reports neither power zones nor an FTP. */
    const val FALLBACK_FTP = 200

    /** Lower bound of each zone in percent of FTP (Coggan). */
    private val COGGAN_LOWER_PCT = listOf(0, 56, 76, 91, 106, 121, 151)

    fun fromFtp(ftp: Int): List<PowerZone> {
        val f = if (ftp > 0) ftp else FALLBACK_FTP
        val mins = COGGAN_LOWER_PCT.map { (it * f + 50) / 100 } // integer rounding, half up
        return mins.mapIndexed { i, min ->
            PowerZone(min, if (i < ZONE_COUNT - 1) mins[i + 1] - 1 else f * 5 / 2)
        }
    }

    /** Use the Karoo profile's zones when it has exactly seven; otherwise derive them from FTP. */
    fun resolve(profileZones: List<PowerZone>, ftp: Int): List<PowerZone> =
        if (profileZones.size == ZONE_COUNT && profileZones.zipWithNext().all { (a, b) -> b.min > a.min }) {
            profileZones
        } else {
            fromFtp(ftp)
        }

    /** 0-based zone index for [watts]. */
    fun zoneIndex(watts: Int, zones: List<PowerZone>): Int =
        zones.indexOfLast { watts >= it.min }.coerceAtLeast(0)

    /** Watts at which the Z7 segment is drawn full. */
    fun z7Cap(zones: List<PowerZone>): Int {
        val z7 = zones.last()
        return if (z7.max > z7.min && z7.max <= z7.min * 3) z7.max else (z7.min * 1.5).roundToInt()
    }

    /** How far through its own zone [watts] is, 0..1. */
    fun fractionInZone(watts: Int, zones: List<PowerZone>): Float {
        val i = zoneIndex(watts, zones)
        val lo = zones[i].min
        val hi = if (i < zones.size - 1) zones[i + 1].min else z7Cap(zones)
        if (hi <= lo) return 1f
        return ((watts - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
    }

    /** Position on the whole gauge, 0 (left end) .. 1 (right end). */
    fun gaugeFraction(watts: Int, zones: List<PowerZone>): Float =
        (zoneIndex(watts, zones) + fractionInZone(watts, zones)) / zones.size
}

/** One heart rate zone in bpm, as reported by the Karoo user profile (min/max inclusive). */
data class HrZone(val min: Int, val max: Int)

/**
 * Heart rate zones for the gauge. Pure Kotlin so it can be unit-tested on the Mac.
 * One equal-size segment per zone; a value is placed proportionally inside its zone.
 */
object HeartRateZones {
    /** Only used if the Karoo profile reports neither HR zones nor a max HR. */
    const val FALLBACK_MAX_HR = 190

    /** Lower bound of each zone in percent of max HR (common 5-zone model). */
    private val DEFAULT_LOWER_PCT = listOf(0, 60, 70, 80, 90)

    fun fromMaxHr(maxHr: Int): List<HrZone> {
        val m = if (maxHr > 0) maxHr else FALLBACK_MAX_HR
        val mins = DEFAULT_LOWER_PCT.map { (it * m + 50) / 100 }
        return mins.mapIndexed { i, min -> HrZone(min, if (i < mins.size - 1) mins[i + 1] - 1 else m) }
    }

    /** Use the Karoo profile's zones when there are 3..7 increasing ones; otherwise derive them from max HR. */
    fun resolve(profileZones: List<HrZone>, maxHr: Int): List<HrZone> =
        if (profileZones.size in 3..7 && profileZones.zipWithNext().all { (a, b) -> b.min > a.min }) {
            profileZones
        } else {
            fromMaxHr(maxHr)
        }

    fun zoneIndex(bpm: Int, zones: List<HrZone>): Int = zones.indexOfLast { bpm >= it.min }.coerceAtLeast(0)

    /** Where the last zone's segment is full: its own max if sensible, else max HR, else +20 bpm. */
    fun topCap(zones: List<HrZone>, maxHr: Int): Int {
        val last = zones.last()
        return when {
            last.max in (last.min + 1)..250 -> last.max
            maxHr > last.min -> maxHr
            else -> last.min + 20
        }
    }

    fun fractionInZone(bpm: Int, zones: List<HrZone>, maxHr: Int): Float {
        val i = zoneIndex(bpm, zones)
        val lo = zones[i].min
        val hi = if (i < zones.size - 1) zones[i + 1].min else topCap(zones, maxHr)
        if (hi <= lo) return 1f
        return ((bpm - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
    }
}

