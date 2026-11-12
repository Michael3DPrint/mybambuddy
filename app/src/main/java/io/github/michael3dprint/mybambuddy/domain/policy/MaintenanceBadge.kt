package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.PrinterMaintenance

/** The small label on a printer card when upkeep is due. Overdue items win over "soon"; nothing due gives no badge at all. */
object MaintenanceBadge {
    /** The badge text: the number due, else the number coming due, else null. */
    fun label(maintenance: PrinterMaintenance?): String? = when {
        maintenance == null -> null
        maintenance.dueCount > 0 -> "${maintenance.dueCount} due"
        maintenance.soonCount > 0 -> "${maintenance.soonCount} soon"
        else -> null
    }
}
