package io.github.michael3dprint.mybambuddy.data.store

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.repo.CachingPrinterRepository
import io.github.michael3dprint.mybambuddy.data.repo.PrintersCache
import io.github.michael3dprint.mybambuddy.domain.connectivity.DefaultConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.PrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_000_000L
    private val moshi = ApiClient.createMoshi()
    private lateinit var store: JsonCacheStore<PrintersCache>
    private val connectivity = DefaultConnectivityStatus()
    private val printer = Printer(id = "1", name = "X2D", model = "X2D", state = PrinterState.IDLE)

    private class FakePrinters(var list: ApiResult<List<Printer>>) : PrinterRepository {
        override fun getPrintersFlow(pollIntervalMs: Long): Flow<ApiResult<List<Printer>>> = emptyFlow()

        override suspend fun fetchPrinters() = list

        override suspend fun fetchPrinter(printerId: String): ApiResult<Printer> = when (val l = list) {
            is ApiResult.Success -> l.data.firstOrNull { it.id == printerId }?.let { ApiResult.Success(it) }
                ?: ApiResult.Error(message = "nope", errorType = ApiErrorType.NOT_FOUND)
            is ApiResult.Error -> l
        }

        override suspend fun fetchPrinterStatus(printerId: String): ApiResult<PrintStatus> = ApiResult.Success(PrintStatus())
    }

    @Before
    fun setUp() {
        val envelope = com.squareup.moshi.Types.newParameterizedType(CacheEnvelope::class.java, PrintersCache::class.java)
        store = JsonCacheStore(
            file = java.io.File(folder.root, "printers.json"),
            adapter = moshi.adapter(envelope),
            maxBytes = 50_000,
            clock = { now },
        )
    }

    @Test
    fun `round trip keeps the data and the save time`() {
        store.write(PrintersCache(listOf(printer)))
        val cached = store.read()!!
        assertEquals(listOf(printer), cached.value.printers)
        assertEquals(now, cached.savedAt)
        assertFalse(java.io.File(folder.root, "printers.json.tmp").exists())
    }

    @Test
    fun `old corrupt oversized and foreign files are ignored`() {
        store.write(PrintersCache(listOf(printer)))
        now += JsonCacheStore.DEFAULT_MAX_AGE_MS + 1
        assertNull(store.read())

        now = 1_000_000L
        java.io.File(folder.root, "printers.json").writeText("{not json")
        assertNull(store.read())

        java.io.File(folder.root, "printers.json").writeText("""{"version":99,"savedAt":$now,"data":{"printers":[]}}""")
        assertNull(store.read())

        java.io.File(folder.root, "printers.json").writeText("x".repeat(60_000))
        assertNull(store.read())
    }

    @Test
    fun `a write that is too large is skipped and keeps the previous copy`() {
        store.write(PrintersCache(listOf(printer)))
        store.write(PrintersCache(List(2_000) { printer.copy(id = "$it", name = "n".repeat(50)) }))
        assertEquals(1, store.read()!!.value.printers.size)
    }

    @Test
    fun `live data is saved and marks the app online`() = runBlocking {
        val repo = CachingPrinterRepository(FakePrinters(ApiResult.Success(listOf(printer))), store, connectivity)
        assertTrue(repo.fetchPrinters() is ApiResult.Success)
        assertFalse(connectivity.state.value.isOffline)
        assertEquals(1, store.read()!!.value.printers.size)
    }

    @Test
    fun `server unreachable serves the saved copy and marks offline`() = runBlocking {
        val inner = FakePrinters(ApiResult.Success(listOf(printer)))
        val repo = CachingPrinterRepository(inner, store, connectivity)
        repo.fetchPrinters()
        inner.list = ApiResult.Error(message = "down", errorType = ApiErrorType.NETWORK_ERROR)
        now += 60_000
        val result = repo.fetchPrinters() as ApiResult.Success
        assertEquals(listOf(printer), result.data)
        assertTrue(connectivity.state.value.isOffline)
        assertEquals(1_000_000L, connectivity.state.value.dataFrom)
        assertEquals(printer, (repo.fetchPrinter("1") as ApiResult.Success).data)
    }

    @Test
    fun `an auth error is never hidden behind the cache`() = runBlocking {
        val inner = FakePrinters(ApiResult.Success(listOf(printer)))
        val repo = CachingPrinterRepository(inner, store, connectivity)
        repo.fetchPrinters()
        inner.list = ApiResult.Error(message = "bad key", errorType = ApiErrorType.UNAUTHORIZED)
        assertTrue(repo.fetchPrinters() is ApiResult.Error)
        assertFalse(connectivity.state.value.isOffline)
    }

    @Test
    fun `no saved copy means the error is shown and the app is marked unreachable`() = runBlocking {
        val repo =
            CachingPrinterRepository(
                FakePrinters(ApiResult.Error(message = "down", errorType = ApiErrorType.NETWORK_ERROR)),
                store,
                connectivity,
            )
        assertTrue(repo.fetchPrinters() is ApiResult.Error)
        // Design 16 F2: the banner and the disabled controls must not depend on a saved copy existing.
        assertTrue(connectivity.state.value.isOffline)
        assertNull(connectivity.state.value.dataFrom)
    }
}
