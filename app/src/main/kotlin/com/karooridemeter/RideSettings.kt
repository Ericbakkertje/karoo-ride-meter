package com.karooridemeter

import android.content.Context

/** Ride Meter settings, stored in SharedPreferences (the Karoo SDK has no per-field settings). */
object RideSettings {
    private const val PREFS = "ride_meter_settings"
    private const val KEY_SCALE_MAX = "speed_scale_max_kmh"
    private const val KEY_SLOT = "ride_slot_"

    /** Dial scale: each option ends on a numbered tick (see RideRenderer.tickSteps). */
    const val DEFAULT_SCALE_MAX_KMH = 60
    val SCALE_OPTIONS_KMH = listOf(30, 45, 60, 90)

    fun scaleMaxKmh(context: Context): Int {
        val stored = prefs(context).getInt(KEY_SCALE_MAX, DEFAULT_SCALE_MAX_KMH)
        return if (stored in SCALE_OPTIONS_KMH) stored else DEFAULT_SCALE_MAX_KMH
    }

    fun setScaleMaxKmh(context: Context, kmh: Int) {
        require(kmh in SCALE_OPTIONS_KMH) { "Unsupported scale: $kmh" }
        prefs(context).edit().putInt(KEY_SCALE_MAX, kmh).apply()
    }

    /** What the three round fields show (top/left, middle, bottom/right). */
    fun rideSlots(context: Context): List<RideSlotValue> = RideSlotValue.DEFAULTS.mapIndexed { i, default ->
        RideSlotValue.fromName(prefs(context).getString(KEY_SLOT + i, null), default)
    }

    fun setRideSlot(context: Context, index: Int, value: RideSlotValue) {
        require(index in 0..2) { "Ride Meter has 3 fields, got $index" }
        prefs(context).edit().putString(KEY_SLOT + index, value.name).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
