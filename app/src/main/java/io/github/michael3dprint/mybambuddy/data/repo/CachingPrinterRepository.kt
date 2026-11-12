package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.isUnreachable
import io.github.michael3dprint.mybambuddy.data.store.JsonCacheStore
import io.github.michael3dprint.mybambuddy.domain.connectivity.ConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.PrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The saved copy of the printer list. */
data class PrintersCache(val printers: List<Printer> = emptyList())

/**
 * Decorator: serves the live data and saves it; when the server can't be reached it serves the saved copy
 * and flips [ConnectivityStatus] to offline. Does not touch the real repository (Open/Closed).
 * Saved copies older than [maxAgeMs] are ignored (printer state older than that is misleading), and saves are at most
 * [minWriteGapMs] apart because the list is re-emitted on every poll tick.
 */
class CachingPrinterRepository(
    private val inner: PrinterRepository,
    store: JsonCacheStore<PrintersCache>,
    private val connectivity: ConnectivityStatus,
    maxAgeMs: Long = JsonCacheStore.DEFAULT_MAX_AGE_MS,
    minWriteGapMs: Long = 0L,
    clock: () -> Long = { System.currentTimeMillis() },
) : PrinterRepository {
    private val source = CachedSource<List<Printer>, PrintersCache>(
        store,
        connectivity,
        wrap = { PrintersCache(it) },
        unwrap = { it.printers },
        maxAgeMs = maxAgeMs,
        minWriteGapMs = minWriteGapMs,
        clock = clock,
    )

    override fun getPrintersFlow(pollIntervalMs: Long): Flow<ApiResult<List<Printer>>> =
        inner.getPrintersFlow(pollIntervalMs).map { source.serve(it) }

    override suspend fun fetchPrinters(): ApiResult<List<Printer>> = source.serve(inner.fetchPrinters())

    override suspend fun fetchPrinter(printerId: String): ApiResult<Printer> {
        val result = inner.fetchPrinter(printerId)
        if (result is ApiResult.Success) return result
        val saved = source.saved()?.takeIf { it.value.any { p -> p.id == printerId } }
        return if (saved != null && result is ApiResult.Error && result.isUnreachable()) {
            connectivity.markOffline(saved.savedAt)
            ApiResult.Success(saved.value.first { it.id == printerId })
        } else {
            result
        }
    }

    // Status of a single printer is a control-adjacent read and is never served from the cache: a saved "IDLE" must not
    // let a slot change or command through while the printer is actually busy.
    override suspend fun fetchPrinterStatus(printerId: String): ApiResult<PrintStatus> = inner.fetchPrinterStatus(printerId)

    internal companion object {
        /** Kept for callers that predate [isUnreachable]. */
        fun isUnreachable(error: ApiResult.Error): Boolean = error.isUnreachable()
    }
}
