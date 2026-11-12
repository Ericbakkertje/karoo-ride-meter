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
    private const val KEY_COLOR_STYLE = "color_style"
    private const val KEY_MINI_DIALS = "round_mini_dials"

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

    /** Ring colours: neon (default), bright, or classic. Before 1.3.1 this was only "bright in dark mode" (still read). */
    fun colorStyle(context: Context): ColorStyle {
        val p = prefs(context)
        return ColorStyle.entries.firstOrNull { it.name == p.getString(KEY_COLOR_STYLE, null) }
            ?: if (p.getBoolean(KEY_DARK_BRIGHT, false)) ColorStyle.BRIGHT else ColorStyle.NEON
    }

    fun setColorStyle(context: Context, style: ColorStyle) {
        prefs(context).edit().putString(KEY_COLOR_STYLE, style.name).putBoolean(KEY_DARK_BRIGHT, style == ColorStyle.BRIGHT).apply()
    }

    /** Round fields as small dials with a needle. Default: number in a coloured circle. */
    fun miniDials(context: Context): Boolean = prefs(context).getBoolean(KEY_MINI_DIALS, false)

    fun setMiniDials(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_MINI_DIALS, on).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Ring colours chosen in Settings. Light mode uses bright for [NEON] and [BRIGHT]. */
enum class ColorStyle { NEON, BRIGHT, CLASSIC }
