package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.store.JsonCacheStore
import io.github.michael3dprint.mybambuddy.domain.connectivity.ConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.PrinterMaintenance
import io.github.michael3dprint.mybambuddy.domain.repo.MaintenanceRepository

/** The saved copy of the maintenance overview. */
data class MaintenanceCache(val printers: List<PrinterMaintenance> = emptyList())

/** Saves the overview and serves it when the server can't be reached. Marking done is never cached or replayed. */
class CachingMaintenanceRepository(
    private val inner: MaintenanceRepository,
    store: JsonCacheStore<MaintenanceCache>,
    connectivity: ConnectivityStatus,
    maxAgeMs: Long = JsonCacheStore.DEFAULT_MAX_AGE_MS,
) : MaintenanceRepository by inner {
    private val source = CachedSource<List<PrinterMaintenance>, MaintenanceCache>(
        store,
        connectivity,
        { MaintenanceCache(it) },
        { it.printers },
        maxAgeMs,
    )

    override suspend fun getOverview(): ApiResult<List<PrinterMaintenance>> = source.serve(inner.getOverview())
}
