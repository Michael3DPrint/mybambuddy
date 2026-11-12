package io.github.michael3dprint.mybambuddy.domain.print

/** Words for the slicer's own estimate of a plate (read from the file, so always labelled as an estimate). */
object PrintEstimate {
    /** "2 h 14 m", "45 m" or "not in the file" for a missing or zero time. */
    fun time(seconds: Int?): String {
        if (seconds == null || seconds <= 0) return "not in the file"
        val minutes = (seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE
        val h = minutes / MINUTES_PER_HOUR
        val m = minutes % MINUTES_PER_HOUR
        return if (h > 0) "$h h $m m" else "$m m"
    }

    /** "2 h 14 m · 31 g (slicer estimate)"; a missing part says so instead of guessing. */
    fun line(seconds: Int?, grams: Double?): String {
        val weight = if (grams == null || grams <= 0.0) "weight not in the file" else "${Math.round(grams)} g"
        return "${time(seconds)} · $weight (slicer estimate)"
    }

    /** "1 h 15 m · 31 g · PLA" with any missing part left out, or null when nothing is known; used on queue cards. */
    fun summary(seconds: Int?, grams: Double?, type: String?): String? {
        val parts = listOfNotNull(
            seconds?.takeIf { it > 0 }?.let { time(it) },
            grams?.takeIf { it > 0.0 }?.let { "${Math.round(it)} g" },
            type?.takeIf { it.isNotBlank() },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private const val SECONDS_PER_MINUTE = 60
    private const val MINUTES_PER_HOUR = 60
}
