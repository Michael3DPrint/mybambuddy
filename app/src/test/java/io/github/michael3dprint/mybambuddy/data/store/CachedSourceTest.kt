package io.github.michael3dprint.mybambuddy.data.store

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.repo.CachingHistoryRepository
import io.github.michael3dprint.mybambuddy.data.repo.CachingPrinterRepository
import io.github.michael3dprint.mybambuddy.data.repo.CachingSpoolRepository
import io.github.michael3dprint.mybambuddy.data.repo.HistoryCache
import io.github.michael3dprint.mybambuddy.data.repo.PrintersCache
import io.github.michael3dprint.mybambuddy.data.repo.SpoolsCache
import io.github.michael3dprint.mybambuddy.domain.connectivity.DefaultConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.HistoryItem
import io.github.michael3dprint.mybambuddy.domain.model.HistoryStatus
import io.github.michael3dprint.mybambuddy.domain.model.PrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.domain.model.UsageEntry
import io.github.michael3dprint.mybambuddy.domain.repo.HistoryRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The shared caching rules (design 16): max age, throttled saves, offline marking, history page 1, and why alerts read live. */
class CachedSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var now = 10_000_000L
    private val moshi = ApiClient.createMoshi()
    private val connectivity = DefaultConnectivityStatus()
    private val down = ApiResult.Error(message = "down", errorType = ApiErrorType.NETWORK_ERROR)
    private val printer = Printer(id = "1", name = "X2D", model = "X2D", state = PrinterState.PRINTING)

    private inline fun <reified T : Any> storeFor(name: String, type: Class<T>): JsonCacheStore<T> {
        val envelope = com.squareup.moshi.Types.newParameterizedType(CacheEnvelope::class.java, type)
        return JsonCacheStore(File(folder.root, "$name.json"), moshi.adapter(envelope), 100_000, { now })
    }

    private class Printers(var list: ApiResult<List<Printer>>) : PrinterRepository {
        var reads = 0

        override fun getPrintersFlow(pollIntervalMs: Long): Flow<ApiResult<List<Printer>>> = emptyFlow()

        override suspend fun fetchPrinters(): ApiResult<List<Printer>> {
            reads++
            return list
        }

        override suspend fun fetchPrinter(printerId: String): ApiResult<Printer> =
            ApiResult.Error(message = "n/a", errorType = ApiErrorType.UNSUPPORTED)

        override suspend fun fetchPrinterStatus(printerId: String): ApiResult<PrintStatus> = ApiResult.Success(PrintStatus())
    }

    @Test
    fun `a decorator hides an unreachable server from its caller, so the alert check must read live`() = runBlocking {
        // This is the reason AppContainer hands AlertRunner the undecorated repositories (design 16, F1).
        val live = Printers(ApiResult.Success(listOf(printer)))
        val repo = CachingPrinterRepository(live, storeFor("p", PrintersCache::class.java), connectivity)
        repo.fetchPrinters()
        live.list = down
        assertTrue("the decorated read looks like a success", repo.fetchPrinters() is ApiResult.Success)
        assertTrue("the live read still tells the truth", live.fetchPrinters() is ApiResult.Error)
    }

    @Test
    fun `a copy older than the max age is ignored`() = runBlocking {
        val live = Printers(ApiResult.Success(listOf(printer)))
        val repo =
            CachingPrinterRepository(live, storeFor("p", PrintersCache::class.java), connectivity, maxAgeMs = 3_600_000L, clock = { now })
        repo.fetchPrinters()
        live.list = down
        now += 7_200_000L
        assertTrue(repo.fetchPrinters() is ApiResult.Error)
        assertTrue(connectivity.state.value.isOffline)
    }

    @Test
    fun `a copy inside the max age is served for spools while printers keep their own limit`() = runBlocking {
        val spool = Spool(5, "Bambu Lab", "PLA", "Basic", "Blue", "307FE2FF", 1000, 250, null, null)
        val inner = object : SpoolRepository by NoSpools {
            var result: ApiResult<List<Spool>> = ApiResult.Success(listOf(spool))

            override suspend fun getSpools() = result
        }
        val repo = CachingSpoolRepository(inner, storeFor("s", SpoolsCache::class.java), connectivity)
        repo.getSpools()
        inner.result = down
        now += 7_200_000L
        assertEquals(listOf(spool), (repo.getSpools() as ApiResult.Success).data)
    }

    @Test
    fun `every decorator marks the app online after a live success`() = runBlocking {
        connectivity.markOffline(1L)
        val inner = object : SpoolRepository by NoSpools {
            override suspend fun getSpools() = ApiResult.Success(emptyList<Spool>())
        }
        CachingSpoolRepository(inner, storeFor("s", SpoolsCache::class.java), connectivity).getSpools()
        assertFalse(connectivity.state.value.isOffline)
    }

    @Test
    fun `saves are throttled but the live data is always returned and marks online`() = runBlocking {
        val store = storeFor("p", PrintersCache::class.java)
        val live = Printers(ApiResult.Success(listOf(printer)))
        val repo = CachingPrinterRepository(live, store, connectivity, minWriteGapMs = 30_000L, clock = { now })
        repo.fetchPrinters()
        val first = store.read()!!.savedAt
        val changed = printer.copy(state = PrinterState.IDLE)
        live.list = ApiResult.Success(listOf(changed))
        now += 5_000L
        assertEquals(listOf(changed), (repo.fetchPrinters() as ApiResult.Success).data)
        assertEquals("inside the gap: not rewritten", first, store.read()!!.savedAt)
        now += 30_000L
        repo.fetchPrinters()
        assertEquals("after the gap: rewritten", now, store.read()!!.savedAt)
    }

    private class FakeHistory(var result: ApiResult<List<HistoryItem>>) : HistoryRepository {
        override suspend fun getHistory(limit: Int, offset: Int) = result
    }

    @Test
    fun `history serves page one from the copy and returns the error for later pages`() = runBlocking {
        val item = HistoryItem("1", "benchy", "X2D", HistoryStatus.SUCCESS, "today", "1 h")
        val inner = FakeHistory(ApiResult.Success(listOf(item)))
        val repo = CachingHistoryRepository(inner, storeFor("h", HistoryCache::class.java), connectivity)
        repo.getHistory(20, 0)
        inner.result = down
        assertEquals(listOf(item), (repo.getHistory(20, 0) as ApiResult.Success).data)
        assertTrue(repo.getHistory(20, 20) is ApiResult.Error)
    }

    private object NoSpools : SpoolRepository {
        override suspend fun getSpools(): ApiResult<List<Spool>> = ApiResult.Success(emptyList())

        override suspend fun createSpool(draft: SpoolDraft): ApiResult<Spool> =
            ApiResult.Error(message = "n/a", errorType = ApiErrorType.UNSUPPORTED)

        override suspend fun linkTag(spoolId: Int, tagUid: String): ApiResult<Unit> =
            ApiResult.Error(message = "n/a", errorType = ApiErrorType.UNSUPPORTED)

        override suspend fun getUsage(spoolId: Int): ApiResult<List<UsageEntry>> =
            ApiResult.Error(message = "n/a", errorType = ApiErrorType.UNSUPPORTED)

        override suspend fun canWeigh(): Boolean = false

        override suspend fun updateWeight(spoolId: Int, grossGrams: Double): ApiResult<Unit> =
            ApiResult.Error(message = "n/a", errorType = ApiErrorType.UNSUPPORTED)
    }
}
