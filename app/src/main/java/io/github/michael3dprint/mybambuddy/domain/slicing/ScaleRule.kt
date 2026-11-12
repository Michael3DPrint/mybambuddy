package io.github.michael3dprint.mybambuddy.domain.slicing

/** The scale choice of the slice page (design 40): whole percent, one number for everything on the plate. */
object ScaleRule {
    /** The smallest scale in percent. */
    const val MIN_PERCENT = 10

    /** The largest scale in percent. */
    const val MAX_PERCENT = 400

    /** The quick choices shown as chips. */
    val QUICK = listOf(50, 75, 100, 125, 150, 200)

    /** A whole percent from [text] (a trailing percent sign is allowed), or null when it is not a number in range. */
    fun parse(text: String): Int? = text.trim().removeSuffix("%").trim().toIntOrNull()?.takeIf { it in MIN_PERCENT..MAX_PERCENT }

    /** The factor for [percent]. */
    fun factor(percent: Int): Double = percent / PERCENT_BASE

    /** The name of the scaled copy: "Benchy.gcode.3mf" at 150 % is "Benchy-scaled-150.3mf". */
    fun copyName(name: String, percent: Int): String {
        val stem = name.removeSuffix(".3mf").removeSuffix(".3MF").removeSuffix(".gcode").ifBlank { "model" }
        return "$stem-scaled-$percent.3mf"
    }

    private const val PERCENT_BASE = 100.0
}
