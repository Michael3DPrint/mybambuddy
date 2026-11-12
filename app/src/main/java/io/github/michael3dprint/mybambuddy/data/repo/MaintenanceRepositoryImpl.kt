package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.MaintenanceApi
import io.github.michael3dprint.mybambuddy.data.api.dto.MaintenanceItemDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterMaintenanceDto
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.data.api.safeUnitCall
import io.github.michael3dprint.mybambuddy.domain.model.DueState
import io.github.michael3dprint.mybambuddy.domain.model.MaintenanceItem
import io.github.michael3dprint.mybambuddy.domain.model.PrinterMaintenance
import io.github.michael3dprint.mybambuddy.domain.repo.MaintenanceRepository
import kotlin.math.abs
import kotlin.math.roundToInt

/** Reads the maintenance overview and marks items done through [MaintenanceApi]. */
class MaintenanceRepositoryImpl(private val api: MaintenanceApi) : MaintenanceRepository {
    override suspend fun getOverview(): ApiResult<List<PrinterMaintenance>> = when (val result = safeApiCall { api.getOverview() }) {
        is ApiResult.Success -> ApiResult.Success(result.data.mapNotNull { it.toDomain() })
        is ApiResult.Error -> result
    }

    override suspend fun markDone(itemId: Int): ApiResult<Unit> = safeUnitCall { api.perform(itemId) }
}

/** Maps a printer's upkeep row; a row without a printer id gives null. */
internal fun PrinterMaintenanceDto.toDomain(): PrinterMaintenance? {
    val id = printerId ?: return null
    return PrinterMaintenance(
        printerId = id,
        printerName = printerName ?: "Printer $id",
        printHours = totalPrintHours ?: 0.0,
        items = items.orEmpty().filter { it.enabled != false }.mapNotNull { it.toDomain(id) },
    )
}

/** Maps one upkeep item, reading hours or days according to its interval type; a row without an id gives null. */
internal fun MaintenanceItemDto.toDomain(fallbackPrinterId: Int): MaintenanceItem? {
    val itemId = id ?: return null
    val byDays = intervalType.equals("days", ignoreCase = true)
    val until = if (byDays) daysUntilDue else hoursUntilDue
    val unit = if (byDays) "d" else "h"
    val interval = if (byDays) intervalDays() else intervalHours
    val since = if (byDays) daysSince else hoursSince
    val state = when {
        isDue == true -> DueState.DUE
        isWarning == true -> DueState.SOON
        else -> DueState.OK
    }
    val progress = if (interval != null && interval > 0.0 && since != null) (since / interval).toFloat().coerceIn(0f, 1f) else 0f
    val label = when {
        until == null -> "—"
        until < 0 -> "${abs(until).roundToInt()} $unit overdue"
        else -> "${until.roundToInt()} $unit left"
    }
    return MaintenanceItem(
        id = itemId,
        printerId = printerId ?: fallbackPrinterId,
        name = name ?: "Maintenance",
        dueState = state,
        progress = progress,
        remainingLabel = label,
        lastDone = lastPerformedAt,
    )
}

/** The server stores a day interval in the same field name; it is only meaningful together with interval_type. */
private fun MaintenanceItemDto.intervalDays(): Double? = intervalHours
