package io.github.michael3dprint.mybambuddy.domain.widget

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.PrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefresherTest {
    private class Printers(var result: ApiResult<List<Printer>>) : PrinterRepository {
        override fun getPrintersFlow(pollIntervalMs: Long): Flow<ApiResult<List<Printer>>> = emptyFlow()

        override suspend fun fetchPrinters() = result

        override suspend fun fetchPrinter(printerId: String): ApiResult<Printer> = error("not used")

        override suspend fun fetchPrinterStatus(printerId: String): ApiResult<PrintStatus> = error("not used")
    }

    private class Store(var saved: WidgetSnapshot? = null) : WidgetSnapshotStore {
        override fun load() = saved

        override fun save(snapshot: WidgetSnapshot) {
            saved = snapshot
        }
    }

    private class Renderer : WidgetRenderer {
        val drawn = mutableListOf<WidgetSnapshot?>()

        override fun render(snapshot: WidgetSnapshot?) {
            drawn += snapshot
        }
    }

    private val failure = ApiResult.Error(null, "down", ApiErrorType.NETWORK_ERROR)
    private val printer = Printer("1", "X2D", "X2D", PrinterState.IDLE)

    @Test
    fun `success saves a fresh snapshot and draws it`() = runTest {
        val store = Store()
        val renderer = Renderer()
        WidgetRefresher(Printers(ApiResult.Success(listOf(printer))), store, renderer) { 5000L }.refresh()
        assertEquals(WidgetSnapshot(listOf(WidgetRow("1", "X2D", "IDLE", -1, "")), 5000L, false), store.saved)
        assertEquals(store.saved, renderer.drawn.single())
    }

    @Test
    fun `failure keeps the old rows and the old time, marked as saved`() = runTest {
        val old = WidgetSnapshot(listOf(WidgetRow("1", "X2D", "PRINTING", 50, "1h 0m")), 1000L, false)
        val store = Store(old)
        val renderer = Renderer()
        WidgetRefresher(Printers(failure), store, renderer) { 9000L }.refresh()
        assertEquals(old.copy(stale = true), store.saved)
        assertTrue(renderer.drawn.single()!!.stale)
    }

    @Test
    fun `failure with nothing saved draws an empty widget and saves nothing`() = runTest {
        val store = Store()
        val renderer = Renderer()
        WidgetRefresher(Printers(failure), store, renderer).refresh()
        assertNull(store.saved)
        assertEquals(listOf<WidgetSnapshot?>(null), renderer.drawn)
    }
}
