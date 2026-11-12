package io.github.michael3dprint.mybambuddy.domain.policy

import java.time.LocalDate

/** A first and last day (inclusive); null means no limit on that side. */
data class DateRange(val from: LocalDate?, val to: LocalDate?)

/** The periods the statistics card offers. */
enum class StatsPeriod(val label: String) {
    THIS_MONTH("This month"),
    LAST_30_DAYS("Last 30 days"),
    ALL_TIME("All time"),
    ;

    /** The days this period covers, counted from [today]. */
    fun range(today: LocalDate): DateRange = when (this) {
        THIS_MONTH -> DateRange(today.withDayOfMonth(1), today)
        LAST_30_DAYS -> DateRange(today.minusDays(LAST_DAYS - 1), today)
        ALL_TIME -> DateRange(null, null)
    }

    private companion object {
        const val LAST_DAYS = 30L
    }
}
