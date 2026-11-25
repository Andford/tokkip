// Fit's rings in neon: each one a gradient from its start to its end colour,
// the same on the phone, its widgets and the watch. Steps are aqua into
// electric blue, minutes lime into green, calories hot pink into orange.
// (On a light background the same hues, a shade deeper.)

package mx.loperstack.tokkip.fit

object Neon {
    const val STEPS = 0xFF00E5FF.toInt()
    const val STEPS_TO = 0xFF3D6BFF.toInt()
    const val MINUTES = 0xFFD7FF3A.toInt()
    const val MINUTES_TO = 0xFF00E676.toInt()
    const val KCAL = 0xFFFF3D8B.toInt()
    const val KCAL_TO = 0xFFFF9A3D.toInt()

    const val STEPS_LIGHT = 0xFF00B4E0.toInt()
    const val STEPS_TO_LIGHT = 0xFF2F55F5.toInt()
    const val MINUTES_LIGHT = 0xFF8FD400.toInt()
    const val MINUTES_TO_LIGHT = 0xFF00B85C.toInt()
    const val KCAL_LIGHT = 0xFFFF2D75.toInt()
    const val KCAL_TO_LIGHT = 0xFFFF7A00.toInt()

    /** The night sky behind everything. */
    const val NIGHT = 0xFF05070E.toInt()
}
