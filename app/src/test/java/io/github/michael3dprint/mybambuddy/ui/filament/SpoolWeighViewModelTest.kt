package io.github.michael3dprint.mybambuddy.ui.filament

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.connectivity.DefaultConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.domain.model.UsageEntry
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpoolWeighViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val spool = Spool(5, "Bambu Lab", "PLA", "Basic", "Blue", "307FE2", 1000, 370, null, null)

    private class Spools(var weigh: Boolean = true, var result: ApiResult<Unit> = ApiResult.Success(Unit)) : SpoolRepository {
        val sent = mutableListOf<Pair<Int, Double>>()

        override suspend fun canWeigh() = weigh

        override suspend fun updateWeight(spoolId: Int, grossGrams: Double): ApiResult<Unit> {
            sent += spoolId to grossGrams
            return result
        }

        override suspend fun getSpools(): ApiResult<List<Spool>> = ApiResult.Success(emptyList())

        override suspend fun createSpool(draft: SpoolDraft): ApiResult<Spool> = error("not used")

        override suspend fun linkTag(spoolId: Int, tagUid: String): ApiResult<Unit> = error("not used")

        override suspend fun getUsage(spoolId: Int): ApiResult<List<UsageEntry>> = ApiResult.Success(emptyList())
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the button shows only when weighing is available`() = runTest(dispatcher) {
        val spools = Spools(weigh = false)
        val vm = SpoolWeighViewModel(spools)
        vm.checkAvailable()
        advanceUntilIdle()
        assertFalse(vm.available.value)
        spools.weigh = true
        vm.checkAvailable()
        advanceUntilIdle()
        assertTrue(vm.available.value)
    }

    @Test
    fun `a reading is sent once, reported and the list is told to reload`() = runTest(dispatcher) {
        val spools = Spools()
        val vm = SpoolWeighViewModel(spools)
        val seen = mutableListOf<String>()
        var updates = 0
        val a = launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { seen += it } }
        val b = launch(UnconfinedTestDispatcher(testScheduler)) { vm.updated.collect { updates++ } }
        vm.weigh(spool, 812.0)
        advanceUntilIdle()
        a.cancel()
        b.cancel()
        assertEquals(listOf(5 to 812.0), spools.sent)
        assertEquals(listOf("Bambu Lab PLA Basic updated from the scale"), seen)
        assertEquals(1, updates)
    }

    @Test
    fun `a refusal shows the server reason and does not reload`() = runTest(dispatcher) {
        val spools =
            Spools(result = ApiResult.Error(message = "m", errorType = ApiErrorType.SERVER_ERROR, serverDetail = "Spoolman unreachable"))
        val vm = SpoolWeighViewModel(spools)
        val seen = mutableListOf<String>()
        var updates = 0
        val a = launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { seen += it } }
        val b = launch(UnconfinedTestDispatcher(testScheduler)) { vm.updated.collect { updates++ } }
        vm.weigh(spool, 812.0)
        advanceUntilIdle()
        a.cancel()
        b.cancel()
        assertEquals(listOf("Spoolman unreachable"), seen)
        assertEquals(0, updates)
    }

    @Test
    fun `offline nothing is sent`() = runTest(dispatcher) {
        val spools = Spools()
        val offline = DefaultConnectivityStatus().also { it.markUnreachable() }
        val vm = SpoolWeighViewModel(spools, offline)
        vm.weigh(spool, 812.0)
        advanceUntilIdle()
        assertTrue(spools.sent.isEmpty())
    }
}
