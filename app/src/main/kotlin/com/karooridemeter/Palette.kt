package com.karooridemeter

/** Colors shared by all meters (vivid neon palette on a dark blue card). Plain ARGB ints, no Android dependency. */
object GaugePalette {
    const val BACKGROUND = 0xFF0B1020.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val TEXT_SECONDARY = 0xFFE2E8F0.toInt()
    const val INDICATOR = 0xFFFFFFFF.toInt()

    /** Segment colors 1..7, fully opaque. Power uses them for Z1..Z7. */
    val ZONES = intArrayOf(
        0xFF0A84FF.toInt(), // 1 blue
        0xFF00F5E4.toInt(), // 2 turquoise
        0xFF00C8FF.toInt(), // 3 light blue
        0xFFFFE100.toInt(), // 4 yellow
        0xFFFF2E9A.toInt(), // 5 pink
        0xFFFF2D55.toInt(), // 6 rose red
        0xFFA855FF.toInt(), // 7 purple
    )

    /** Heart rate Z1..Z5 (same palette): blue, turquoise, yellow, pink, rose red. */
    val HR_ZONES: List<Int> = listOf(ZONES[0], ZONES[1], ZONES[3], ZONES[4], ZONES[5])

    /** Colors for [count] segments: the HR set for 5, otherwise spread evenly over the 7-color palette. */
    fun segmentColors(count: Int): List<Int> = when {
        count == 5 -> HR_ZONES
        count <= 1 -> listOf(ZONES[0])
        else -> (0 until count).map { i -> ZONES[Math.round(i * (ZONES.size - 1f) / (count - 1))] }
    }

    /** Share of the segment color in an unlit segment (rest is background). Opaque result, stays colorful. */
    const val INACTIVE_STRENGTH = 0.40f

    /** Unlit segment color: an opaque, darker shade of the segment color (no alpha transparency). */
    fun inactive(color: Int): Int = blend(color, BACKGROUND, INACTIVE_STRENGTH)

    private fun blend(fg: Int, bg: Int, a: Float): Int {
        fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
        fun mix(shift: Int) = (ch(fg, shift) * a + ch(bg, shift) * (1 - a)).toInt()
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}


/**
 * Colours for one Ride Meter frame. Dark (the default) follows [GaugePalette]; light is a light-grey card with
 * dark text. Light mode always uses the bright ring colours, which stay readable on a light background;
 * dark mode uses them only when chosen in Settings.
 */
data class RideTheme(val light: Boolean = false, val brightColors: Boolean = false, val classic: Boolean = false) {
    val bright: Boolean get() = light || brightColors

    val background: Int get() = if (light) 0xFFF4F6FA.toInt() else GaugePalette.BACKGROUND
    val text: Int get() = if (light) 0xFF0B1020.toInt() else GaugePalette.TEXT
    val textSecondary: Int get() = if (light) 0xFF3D4A5C.toInt() else GaugePalette.TEXT_SECONDARY
    val needle: Int get() = if (light) 0xFFE0221A.toInt() else 0xFFFF3B30.toInt()
    val tickLit: Int get() = if (light) 0xFF0B1020.toInt() else 0xFFFFFFFF.toInt()
    val tickDim: Int get() = if (light) 0xFFB4BECE.toInt() else 0xFF5B6B85.toInt()
    val ring: Int get() = if (light) 0xFFC5CEDC.toInt() else 0xFF33415C.toInt()

    /** A zone colour as drawn in this theme: the bright variant when [bright]; grey for "no value" in light mode. */
    fun color(c: Int): Int {
        if (c == RideGauge.NEUTRAL) return if (light) LIGHT_NEUTRAL else c
        val i = GaugePalette.ZONES.indexOf(c)
        if (i < 0) return c
        if (classic) return (if (light) CLASSIC_LIGHT_ZONES else CLASSIC_ZONES)[i]
        return if (bright) BRIGHT_ZONES[i] else c
    }

    companion object {
        val DARK = RideTheme()
        const val LIGHT_NEUTRAL = 0xFFB4BECE.toInt()

        /** Deeper, more saturated versions of [GaugePalette.ZONES] (same order). */
        val BRIGHT_ZONES = intArrayOf(
            0xFF0057E0.toInt(), // 1 blue
            0xFF00A896.toInt(), // 2 turquoise
            0xFF008FD0.toInt(), // 3 light blue
            0xFFF0A500.toInt(), // 4 amber
            0xFFE6007E.toInt(), // 5 magenta
            0xFFD9002B.toInt(), // 6 red
            0xFF7B2FF0.toInt(), // 7 purple
        )

        /** "Classic" colours (chosen in Settings): plain blue, green, yellow, orange, real red (same order as [GaugePalette.ZONES]). */
        val CLASSIC_ZONES = intArrayOf(
            0xFF2D7DFF.toInt(), // 1 blue
            0xFF00D84A.toInt(), // 2 green
            0xFFB4F000.toInt(), // 3 lime
            0xFFFFE000.toInt(), // 4 yellow
            0xFFFF7A00.toInt(), // 5 orange
            0xFFFF1A1A.toInt(), // 6 red
            0xFFB33CFF.toInt(), // 7 purple
        )

        /** [CLASSIC_ZONES] a bit deeper, for the light card. */
        val CLASSIC_LIGHT_ZONES = intArrayOf(
            0xFF0050D8.toInt(), // 1 blue
            0xFF00A03A.toInt(), // 2 green
            0xFF7DB800.toInt(), // 3 lime
            0xFFE0B000.toInt(), // 4 yellow
            0xFFF06000.toInt(), // 5 orange
            0xFFE00000.toInt(), // 6 red
            0xFF8A1FE0.toInt(), // 7 purple
        )
    }
}
