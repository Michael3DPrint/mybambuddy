package io.github.michael3dprint.mybambuddy.domain.widget

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.PrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetFeedingPrinterRepositoryTest {
    private class Printers(var result: ApiResult<List<Printer>>, var flow: List<ApiResult<List<Printer>>> = emptyList()) :
        PrinterRepository {
        override fun getPrintersFlow(pollIntervalMs: Long): Flow<ApiResult<List<Printer>>> = flowOf(*flow.toTypedArray())

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

    private val idle = Printer("1", "X2D", "X2D", PrinterState.IDLE)
    private val failure = ApiResult.Error(null, "down", ApiErrorType.NETWORK_ERROR)
    private var now = 1_000L

    private fun repo(inner: Printers, store: Store, renderer: Renderer) = WidgetFeedingPrinterRepository(inner, store, renderer) { now }

    @Test
    fun `a successful read saves a fresh snapshot and redraws the widget`() = runTest {
        val store = Store()
        val renderer = Renderer()
        repo(Printers(ApiResult.Success(listOf(idle))), store, renderer).fetchPrinters()
        assertEquals(WidgetSnapshot(listOf(WidgetRow("1", "X2D", "IDLE", -1, "")), 1_000L, false), store.saved)
        assertEquals(1, renderer.drawn.size)
    }

    @Test
    fun `a failed read leaves the saved snapshot alone and draws nothing`() = runTest {
        val old = WidgetSnapshot(listOf(WidgetRow("1", "X2D", "IDLE", -1, "")), 500L, false)
        val store = Store(old)
        val renderer = Renderer()
        repo(Printers(failure), store, renderer).fetchPrinters()
        assertEquals(old, store.saved)
        assertEquals(0, renderer.drawn.size)
    }

    @Test
    fun `the same rows within a minute are not written or drawn again`() = runTest {
        val store = Store()
        val renderer = Renderer()
        val repo = repo(Printers(ApiResult.Success(listOf(idle))), store, renderer)
        repo.fetchPrinters()
        now += 5_000L
        repo.fetchPrinters()
        assertEquals(1, renderer.drawn.size)
        now += 70_000L
        repo.fetchPrinters()
        assertEquals(2, renderer.drawn.size)
    }

    @Test
    fun `changed rows are drawn at once, and an old saved snapshot is replaced`() = runTest {
        val store = Store(WidgetSnapshot(listOf(WidgetRow("1", "X2D", "IDLE", -1, "")), 900L, stale = true))
        val renderer = Renderer()
        val printing = idle.copy(state = PrinterState.PRINTING)
        repo(Printers(ApiResult.Success(listOf(idle))), store, renderer).fetchPrinters()
        assertEquals(false, store.saved?.stale)
        repo(Printers(ApiResult.Success(listOf(printing))), store, renderer).fetchPrinters()
        assertEquals("PRINTING", store.saved?.rows?.single()?.stateText)
        assertEquals(2, renderer.drawn.size)
    }

    @Test
    fun `the polling flow feeds the widget too and passes every result through`() = runTest {
        val store = Store()
        val renderer = Renderer()
        val inner = Printers(failure, flow = listOf(failure, ApiResult.Success(listOf(idle))))
        val seen = repo(inner, store, renderer).getPrintersFlow(5000L).toList()
        assertEquals(2, seen.size)
        assertEquals(1, renderer.drawn.size)
    }
}
