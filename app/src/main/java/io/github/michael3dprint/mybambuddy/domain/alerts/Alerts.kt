package io.github.michael3dprint.mybambuddy.domain.alerts

import io.github.michael3dprint.mybambuddy.domain.model.PrinterState

/** The kinds of alert the app can raise; each can be switched off in Settings. */
enum class AlertType { PRINT_FINISHED, PRINT_FAILED, NEW_ERROR, PRINTER_OFFLINE, LOW_FILAMENT, MAINTENANCE_DUE }

/**
 * One thing worth telling the user. [key] identifies the event so it is never posted twice. [printerId] or [spoolId], when set, is what
 * tapping the notification opens: a low-filament alert carries the spool.
 */
data class Alert(
    val type: AlertType,
    val key: String,
    val title: String,
    val text: String,
    val printerId: Int? = null,
    val spoolId: Int? = null,
)

/** Which alert categories the user wants. */
data class AlertSettings(
    val enabled: Boolean = false,
    val printFinished: Boolean = true,
    val printFailed: Boolean = true,
    val newError: Boolean = true,
    val printerOffline: Boolean = false,
    val lowFilament: Boolean = true,
    val maintenanceDue: Boolean = true,
) {
    fun allows(type: AlertType): Boolean = enabled &&
        when (type) {
            AlertType.PRINT_FINISHED -> printFinished
            AlertType.PRINT_FAILED -> printFailed
            AlertType.NEW_ERROR -> newError
            AlertType.PRINTER_OFFLINE -> printerOffline
            AlertType.LOW_FILAMENT -> lowFilament
            AlertType.MAINTENANCE_DUE -> maintenanceDue
        }
}

/** What one printer looked like on this check: its state and the HMS error codes it reported. */
data class PrinterObservation(val id: Int, val name: String, val state: PrinterState, val errorCodes: Set<String>)

/** What one spool looked like on this check: its remaining share and the low-stock threshold it uses, if any. */
data class SpoolObservation(val id: Int, val title: String, val remainingPercent: Int?, val lowStockPct: Int?)

/** One upkeep item that was due on this check, by item and printer. */
data class DueObservation(val itemId: Int, val printerId: Int, val label: String)

/** What the app saw this time. */
data class Observation(
    val printers: List<PrinterObservation> = emptyList(),
    val spools: List<SpoolObservation> = emptyList(),
    val due: List<DueObservation> = emptyList(),
)

/** What the app remembers between checks (saved to a small JSON file). */
data class AlertMemory(
    val states: Map<String, String> = emptyMap(),
    val errorCodes: Map<String, Set<String>> = emptyMap(),
    val offlineStreak: Map<String, Int> = emptyMap(),
    val lowSpoolIds: Set<Int> = emptySet(),
    val dueItemIds: Set<Int> = emptySet(),
    val initialised: Boolean = false,
)

/** What a check decided: the alerts to post and the memory to keep for the next check. */
data class AlertResult(val alerts: List<Alert>, val memory: AlertMemory)
