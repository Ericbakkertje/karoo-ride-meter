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

