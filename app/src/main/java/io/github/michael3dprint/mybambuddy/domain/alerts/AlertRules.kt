package io.github.michael3dprint.mybambuddy.domain.alerts

import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.policy.LowStockPolicy

/**
 * Compares what was seen before with what is seen now and decides which alerts to raise. Pure: no Android, no network.
 * The very first run only records the situation (no flood of alerts for things that were already true).
 */
object AlertRules {
    private const val OFFLINE_CHECKS_BEFORE_ALERT = 2

    /**
     * Compares the memory from the last check with what is seen now and returns the alerts to raise and the memory to keep. The
     * first run (empty memory) only records the situation.
     */
    fun evaluate(previous: AlertMemory, now: Observation, settings: AlertSettings): AlertResult {
        val alerts = mutableListOf<Alert>()
        val states = mutableMapOf<String, String>()
        val codes = mutableMapOf<String, Set<String>>()
        val streak = mutableMapOf<String, Int>()

        for (p in now.printers) {
            val id = p.id.toString()
            states[id] = p.state.name
            codes[id] = p.errorCodes
            val offlineCount = if (p.state == PrinterState.OFFLINE) (previous.offlineStreak[id] ?: 0) + 1 else 0
            streak[id] = offlineCount
            if (!previous.initialised) continue
            val before = previous.states[id]
            val wasPrinting = before == PrinterState.PRINTING.name || before == PrinterState.PAUSED.name
            if (wasPrinting && p.state == PrinterState.FINISHED) {
                alerts +=
                    Alert(
                        AlertType.PRINT_FINISHED,
                        "fin:$id:${previous.states[id]}",
                        "Print finished",
                        "${p.name} finished printing.",
                        p.id,
                    )
            }
            if (wasPrinting && (p.state == PrinterState.ERROR || p.state == PrinterState.FAILED)) {
                alerts += Alert(AlertType.PRINT_FAILED, "fail:$id", "Print failed", "${p.name} stopped with an error.", p.id)
            }
            val newCodes = p.errorCodes - (previous.errorCodes[id] ?: emptySet())
            if (newCodes.isNotEmpty()) {
                alerts +=
                    Alert(
                        AlertType.NEW_ERROR,
                        "err:$id:${newCodes.sorted().first()}",
                        "Printer error",
                        "${p.name} reported a new error.",
                        p.id,
                    )
            }
            if (offlineCount == OFFLINE_CHECKS_BEFORE_ALERT) {
                alerts += Alert(AlertType.PRINTER_OFFLINE, "off:$id", "Printer offline", "${p.name} is no longer reachable.", p.id)
            }
        }

        val lowNow = now.spools.filter { LowStockPolicy.isLow(it.remainingPercent, it.lowStockPct) }.map { it.id }.toSet()
        val recovered = now.spools.filter { LowStockPolicy.isRecovered(it.remainingPercent, it.lowStockPct) }.map { it.id }.toSet()
        // Remember spools that are still low or hovering just above the line, so a flapping value doesn't re-alert.
        val stillArmed = (previous.lowSpoolIds - recovered).filter { id -> now.spools.any { it.id == id } }.toSet()
        if (previous.initialised) {
            now.spools.filter { it.id in lowNow && it.id !in previous.lowSpoolIds }.forEach {
                alerts +=
                    Alert(
                        AlertType.LOW_FILAMENT,
                        "low:${it.id}",
                        "Filament running low",
                        "${it.title} is at ${it.remainingPercent} %.",
                        spoolId = it.id,
                    )
            }
        }

        val dueNow = now.due.map { it.itemId }.toSet()
        if (previous.initialised) {
            now.due.filter { it.itemId !in previous.dueItemIds }.forEach {
                alerts += Alert(AlertType.MAINTENANCE_DUE, "due:${it.itemId}", "Maintenance due", it.label, it.printerId)
            }
        }

        val memory = AlertMemory(states, codes, streak, stillArmed + lowNow, dueNow, initialised = true)
        return AlertResult(alerts.filter { settings.allows(it.type) }, memory)
    }
}
