package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.PrinterMaintenance

/** Upkeep that is due per printer. Defining the maintenance types stays in Bambuddy. */
interface MaintenanceRepository {
    /** The upkeep due for each printer. */
    suspend fun getOverview(): ApiResult<List<PrinterMaintenance>>

    /** Marks an item as done (resets its counter). A write: single attempt, never cached. */
    suspend fun markDone(itemId: Int): ApiResult<Unit>
}
