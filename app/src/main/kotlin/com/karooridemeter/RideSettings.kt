package com.karooridemeter

import android.content.Context

/** Ride Meter settings, stored in SharedPreferences (the Karoo SDK has no per-field settings). */
object RideSettings {
    private const val PREFS = "ride_meter_settings"
    private const val KEY_SCALE_MAX = "speed_scale_max_kmh"
    private const val KEY_SLOT = "ride_slot_"
    private const val KEY_UNDER_SPEED = "under_speed"
    private const val KEY_STATUS_LEFT = "status_left"
    private const val KEY_STATUS_RIGHT = "status_right"
    private const val KEY_DARK_BRIGHT = "dark_bright_colors"

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

    /** Line under the speed: distance (default) or average speed. */
    fun underSpeed(context: Context): UnderSpeed = UnderSpeed.fromName(prefs(context).getString(KEY_UNDER_SPEED, null))

    fun setUnderSpeed(context: Context, value: UnderSpeed) {
        prefs(context).edit().putString(KEY_UNDER_SPEED, value.name).apply()
    }

    /** What the spots above the dial show (top-left, top-right). */
    fun statusLeft(context: Context): StatusValue =
        StatusValue.fromName(prefs(context).getString(KEY_STATUS_LEFT, null), StatusValue.DEFAULT_LEFT)

    fun statusRight(context: Context): StatusValue =
        StatusValue.fromName(prefs(context).getString(KEY_STATUS_RIGHT, null), StatusValue.DEFAULT_RIGHT)

    fun setStatusLeft(context: Context, value: StatusValue) {
        prefs(context).edit().putString(KEY_STATUS_LEFT, value.name).apply()
    }

    fun setStatusRight(context: Context, value: StatusValue) {
        prefs(context).edit().putString(KEY_STATUS_RIGHT, value.name).apply()
    }

    /** Bright ring colours in dark mode (light mode always uses them). Default: the normal neon colours. */
    fun darkBrightColors(context: Context): Boolean = prefs(context).getBoolean(KEY_DARK_BRIGHT, false)

    fun setDarkBrightColors(context: Context, bright: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK_BRIGHT, bright).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
