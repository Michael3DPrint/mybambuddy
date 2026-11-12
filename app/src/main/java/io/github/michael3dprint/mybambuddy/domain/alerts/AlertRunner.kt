package io.github.michael3dprint.mybambuddy.domain.alerts

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.DueState
import io.github.michael3dprint.mybambuddy.domain.repo.MaintenanceRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository

/** Remembers what was seen last time. */
interface AlertMemoryStore {
    /** The memory saved by the previous check. */
    fun load(): AlertMemory

    /** Saves the memory to compare against at the next check. */
    fun save(memory: AlertMemory)
}

/** Shows an alert to the user. */
interface AlertNotifier {
    /** @return true if it was shown (false when notifications are not allowed). */
    fun post(alert: Alert): Boolean

    /** Posts one summary notification saying how many more alerts were not shown individually. */
    fun postSummary(count: Int)
}

/** Where the saved alert choices (which alerts are on) come from. */
interface AlertSettingsSource {
    /** The current alert choices. */
    fun load(): AlertSettings
}

/**
 * One background check: look at printers, spools and maintenance, decide with [AlertRules], notify, remember.
 * It only reads from the server, and does nothing if every source failed (an unreachable server is not an alert).
 */
class AlertRunner(
    private val printers: PrinterRepository,
    private val spools: SpoolRepository,
    private val maintenance: MaintenanceRepository,
    private val memory: AlertMemoryStore,
    private val notifier: AlertNotifier,
    private val settings: AlertSettingsSource,
) {
    suspend fun run(): Int {
        val config = settings.load()
        if (!config.enabled) return 0
        val printerResult = printers.fetchPrinters()
        val spoolResult = spools.getSpools()
        val maintenanceResult = maintenance.getOverview()
        if (printerResult is ApiResult.Error && spoolResult is ApiResult.Error && maintenanceResult is ApiResult.Error) return 0

        val observation = Observation(
            printers = (printerResult as? ApiResult.Success)?.data.orEmpty().mapNotNull { p ->
                val id = p.id.toIntOrNull() ?: return@mapNotNull null
                PrinterObservation(id, p.name, p.state, p.hmsErrors.map { it.fullCode.ifBlank { it.code } }.toSet())
            },
            spools = (spoolResult as? ApiResult.Success)?.data.orEmpty().map {
                SpoolObservation(it.id, it.title, it.remainingPercent, it.lowStockPct)
            },
            due = (maintenanceResult as? ApiResult.Success)?.data.orEmpty().flatMap { printer ->
                printer.items.filter { it.dueState == DueState.DUE }
                    .map { DueObservation(it.id, printer.printerId, "${printer.printerName}: ${it.name}") }
            },
        )
        val previous = memory.load()
        val result = AlertRules.evaluate(previous, observation, config)
        val keep =
            mergeFailedSources(
                previous,
                result.memory,
                printerResult is ApiResult.Error,
                spoolResult is ApiResult.Error,
                maintenanceResult is ApiResult.Error,
            )
        memory.save(keep)

        val toShow = result.alerts.take(MAX_PER_RUN)
        val shown = toShow.count { notifier.post(it) }
        if (result.alerts.size > MAX_PER_RUN) notifier.postSummary(result.alerts.size - MAX_PER_RUN)
        return shown
    }

    private fun mergeFailedSources(
        old: AlertMemory,
        new: AlertMemory,
        printersFailed: Boolean,
        spoolsFailed: Boolean,
        maintenanceFailed: Boolean,
    ): AlertMemory = new.copy(
        // A source that failed keeps what we remembered about it, so nothing is lost or re-alerted when it returns.
        states = if (printersFailed) old.states else new.states,
        lowSpoolIds = if (spoolsFailed) old.lowSpoolIds else new.lowSpoolIds,
        dueItemIds = if (maintenanceFailed) old.dueItemIds else new.dueItemIds,
        errorCodes = if (printersFailed) old.errorCodes else new.errorCodes,
        offlineStreak = if (printersFailed) old.offlineStreak else new.offlineStreak,
    )

    private companion object {
        const val MAX_PER_RUN = 5
    }
}
