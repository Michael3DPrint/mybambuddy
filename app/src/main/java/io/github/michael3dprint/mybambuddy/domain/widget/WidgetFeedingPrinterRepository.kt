package io.github.michael3dprint.mybambuddy.domain.widget

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Wraps the app's live printer reads so that every successful read also refreshes the widget's saved snapshot and redraws it (design 24). The widget then shows what the app last saw without a request of its own. It sits under the app's cache layer, so only a
 * real server answer counts: saved data served while the server is unreachable is never written as fresh. A read with the same rows
 * as the saved snapshot, less than [MIN_REDRAW_MS] after it, changes nothing.
 */
class WidgetFeedingPrinterRepository(
    private val inner: PrinterRepository,
    private val store: WidgetSnapshotStore,
    private val renderer: WidgetRenderer,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PrinterRepository by inner {
    override fun getPrintersFlow(pollIntervalMs: Long): Flow<ApiResult<List<Printer>>> =
        inner.getPrintersFlow(pollIntervalMs).onEach(::feed)

    override suspend fun fetchPrinters(): ApiResult<List<Printer>> = inner.fetchPrinters().also(::feed)

    private fun feed(result: ApiResult<List<Printer>>) {
        val printers = (result as? ApiResult.Success)?.data ?: return
        val fresh = WidgetSnapshotMapper.from(printers, clock())
        val saved = store.load()
        val unchanged = saved != null && !saved.stale && saved.rows == fresh.rows && fresh.takenAtMs - saved.takenAtMs < MIN_REDRAW_MS
        if (unchanged) return
        store.save(fresh)
        renderer.render(fresh)
    }

    private companion object {
        const val MIN_REDRAW_MS = 60_000L
    }
}
