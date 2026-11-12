package io.github.michael3dprint.mybambuddy.domain.widget

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository

/**
 * One widget refresh: read the printers (read only), save a snapshot, redraw. When the server cannot be reached the
 * previous rows stay, marked as saved, so the widget is never blank.
 */
class WidgetRefresher(
    private val printers: PrinterRepository,
    private val store: WidgetSnapshotStore,
    private val renderer: WidgetRenderer,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** Refreshes and returns what is now shown. */
    suspend fun refresh(): WidgetSnapshot? {
        val snapshot = when (val result = printers.fetchPrinters()) {
            is ApiResult.Success -> WidgetSnapshotMapper.from(result.data, clock())
            is ApiResult.Error -> store.load()?.copy(stale = true)
        }
        snapshot?.let(store::save)
        renderer.render(snapshot)
        return snapshot
    }
}
