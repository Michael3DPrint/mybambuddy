package io.github.michael3dprint.mybambuddy.ui.filament

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.domain.model.UsageEntry
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SlotWeightSyncViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val uuid = "B952275D8AAB4DD1853A1DD80A6EA7A2"
    private val tray = AmsTray(2, "PLA", "PLA Basic", "307FE2FF", 75, 2, hasRfidTag = true, trayUuid = uuid)
    private val spool =
        Spool(2, "Bambu Lab", "PLA", "Basic", "Blue", "307FE2", 1000, 1000, null, null, trayUuid = uuid, coreWeightGrams = 250)

    private class Spools(var canWeighNow: Boolean = true, var result: ApiResult<Unit> = ApiResult.Success(Unit)) : SpoolRepository {
        val writes = mutableListOf<Pair<Int, Double>>()

        override suspend fun getSpools(): ApiResult<List<Spool>> = error("unused")

        override suspend fun createSpool(draft: SpoolDraft): ApiResult<Spool> = error("unused")

        override suspend fun linkTag(spoolId: Int, tagUid: String): ApiResult<Unit> = error("unused")

        override suspend fun getUsage(spoolId: Int): ApiResult<List<UsageEntry>> = error("unused")

        override suspend fun canWeigh() = canWeighNow

        override suspend fun updateWeight(spoolId: Int, grossGrams: Double): ApiResult<Unit> {
            writes += spoolId to grossGrams
            return result
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `preparing waits for confirmation and writes nothing`() = runTest(dispatcher) {
        val spools = Spools()
        val vm = SlotWeightSyncViewModel(spools)
        vm.prepare(tray, listOf(spool), printerBusy = false)
        advanceUntilIdle()
        assertEquals(750, vm.pending.value?.newRemainingGrams)
        assertTrue(spools.writes.isEmpty())
    }

    @Test
    fun `confirming writes once with the gross weight and then clears`() = runTest(dispatcher) {
        val spools = Spools()
        val vm = SlotWeightSyncViewModel(spools)
        vm.prepare(tray, listOf(spool), false)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertEquals(listOf(2 to 1000.0), spools.writes)
        assertNull(vm.pending.value)
        vm.confirm()
        advanceUntilIdle()
        assertEquals(1, spools.writes.size)
    }

    @Test
    fun `cancel writes nothing`() = runTest(dispatcher) {
        val spools = Spools()
        val vm = SlotWeightSyncViewModel(spools)
        vm.prepare(tray, listOf(spool), false)
        advanceUntilIdle()
        vm.cancel()
        vm.confirm()
        advanceUntilIdle()
        assertTrue(spools.writes.isEmpty())
    }

    @Test
    fun `a refused plan shows its reason and nothing is pending or written`() = runTest(dispatcher) {
        val spools = Spools(canWeighNow = false)
        val vm = SlotWeightSyncViewModel(spools)
        val messages = mutableListOf<String>()
        val job = launch { vm.messages.collect { messages += it } }
        advanceUntilIdle()
        vm.prepare(tray, listOf(spool), false)
        advanceUntilIdle()
        job.cancel()
        assertNull(vm.pending.value)
        assertTrue(spools.writes.isEmpty())
        assertTrue(messages.single().contains("Spoolman"))
    }

    @Test
    fun `a failed write is reported and the inventory is not marked as changed`() = runTest(dispatcher) {
        val spools = Spools(result = ApiResult.Error(code = 500, message = "boom", errorType = ApiErrorType.SERVER_ERROR))
        val vm = SlotWeightSyncViewModel(spools)
        var changed = 0
        val job = launch { vm.changed.collect { changed++ } }
        advanceUntilIdle()
        vm.prepare(tray, listOf(spool), false)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        job.cancel()
        assertEquals(1, spools.writes.size)
        assertEquals(0, changed)
    }
}
