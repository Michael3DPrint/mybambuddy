package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.HistoryStatus

/** The filter chips of the Previous prints list (design 44). A print that is still running is never listed there. */
enum class PreviousPrintFilter(val label: String) {
    ALL("All"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    FAILED("Failed"),
    ;

    /** True when a print that ended with [status] belongs under this filter. */
    fun matches(status: HistoryStatus): Boolean = when (this) {
        ALL -> status != HistoryStatus.IN_PROGRESS
        COMPLETED -> status == HistoryStatus.SUCCESS
        CANCELLED -> status == HistoryStatus.CANCELLED
        FAILED -> status == HistoryStatus.FAILED
    }
}
