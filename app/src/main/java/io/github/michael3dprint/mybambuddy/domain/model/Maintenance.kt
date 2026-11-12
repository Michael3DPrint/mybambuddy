package io.github.michael3dprint.mybambuddy.domain.model

/** How close an upkeep item is to being due. */
enum class DueState { OK, SOON, DUE }

/** One upkeep item for a printer, with how much of its interval is used and a label for what is left. */
data class MaintenanceItem(
    val id: Int,
    val printerId: Int,
    val name: String,
    val dueState: DueState,
    /** Share of the interval already used, 0..1 (never negative or above 1). */
    val progress: Float,
    /** e.g. "50 h left", "3 d overdue". */
    val remainingLabel: String,
    val lastDone: String?,
)

/** A printer's upkeep items, with counts of those due and those coming due. */
data class PrinterMaintenance(val printerId: Int, val printerName: String, val printHours: Double, val items: List<MaintenanceItem>) {
    /** How many items are due now. */
    val dueCount: Int get() = items.count { it.dueState == DueState.DUE }

    /** How many items are coming due. */
    val soonCount: Int get() = items.count { it.dueState == DueState.SOON }
}
