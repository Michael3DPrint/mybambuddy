package io.github.michael3dprint.mybambuddy.domain.widget

/**
 * What a screen reader says for one widget row (design 34): the printer, its state, the progress and the time left in words, for example
 * "X2D, printing, 62 percent, 1 hour 12 minutes left". The widget's own small texts are hidden from the reader so nothing is read twice.
 */
object WidgetSpeech {
    private val hoursMinutes = Regex("^(\\d+)h (\\d+)m$")
    private val minutes = Regex("^(\\d+)m$")

    /** The spoken summary of [row]. */
    fun row(row: WidgetRow): String = listOfNotNull(
        row.name,
        row.stateText.lowercase(),
        row.progress.takeIf { it >= 0 }?.let { "$it percent" },
        spokenTimeLeft(row.timeLeft)?.let { "$it left" },
    ).joinToString(", ")

    /** "1h 12m" as "1 hour 12 minutes" and "5m" as "5 minutes"; blank gives null. */
    fun spokenTimeLeft(timeLeft: String): String? {
        hoursMinutes.matchEntire(timeLeft)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            return "$h ${if (h == 1) "hour" else "hours"} $min ${if (min == 1) "minute" else "minutes"}"
        }
        minutes.matchEntire(timeLeft)?.let { m ->
            val min = m.groupValues[1].toInt()
            return "$min ${if (min == 1) "minute" else "minutes"}"
        }
        return timeLeft.takeIf { it.isNotBlank() }
    }
}
