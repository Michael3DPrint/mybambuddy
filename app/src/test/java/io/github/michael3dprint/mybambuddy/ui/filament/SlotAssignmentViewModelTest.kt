package io.github.michael3dprint.mybambuddy.ui.filament

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.PrintActivity
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SlotAssignmentViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val unit = testAmsUnit
    private val spool = testSpool
    private val ams = FakeAms()
    private lateinit var vm: SlotAssignmentViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = SlotAssignmentViewModel(ams)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun collect(scope: kotlinx.coroutines.test.TestScope, into: MutableList<String>) =
        scope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { vm.messages.collect { into += it } }

    @Test
    fun `cancelling the confirmation sends nothing`() = runTest(dispatcher) {
        vm.requestLoad(spool, unit, 2, listOf(unit))
        vm.cancelConfirmation()
        vm.confirmLoad()
        advanceUntilIdle()
        assertTrue(ams.calls.isEmpty())
        assertEquals(null, vm.confirmation.value)
    }

    @Test
    fun `an invalid slot is refused with a message and never reaches confirmation`() = runTest(dispatcher) {
        val seen = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { seen += it } }
        vm.requestLoad(spool, unit, 4, listOf(unit))
        advanceUntilIdle()
        job.cancel()
        assertEquals(1, seen.size)
        assertEquals(null, vm.confirmation.value)
        assertTrue(ams.calls.isEmpty())
    }

    @Test
    fun `scanned spool can be loaded into a chosen slot`() = runTest(dispatcher) {
        ams.units = ApiResult.Success(listOf(unit))
        vm.beginLoad(spool)
        advanceUntilIdle()
        val pending = vm.pendingLoad.value!!
        assertEquals(1, pending.units.size)

        vm.requestLoad(pending.spool, pending.units.first(), 2, listOf(unit))
        advanceUntilIdle()
        assertTrue(ams.calls.isEmpty())
        assertEquals(null, vm.pendingLoad.value)

        vm.confirmLoad()
        advanceUntilIdle()
        assertEquals(listOf("load:5:1:0:2"), ams.calls)
    }

    @Test
    fun `loading with no AMS reports it and cancel clears the request`() = runTest(dispatcher) {
        ams.units = ApiResult.Success(emptyList())
        val seen = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { seen += it } }
        vm.beginLoad(spool)
        advanceUntilIdle()
        assertEquals(listOf("No AMS available to load into."), seen)
        vm.cancelLoad()
        job.cancel()
        assertEquals(null, vm.pendingLoad.value)
    }

    @Test
    fun `a busy printer is refused before any dialog or request`() = runTest(dispatcher) {
        val busy = unit.copy(capabilities = unit.capabilities.copy(activity = PrintActivity.BUSY))
        val seen = mutableListOf<String>()
        val job = collect(this, seen)
        vm.requestLoad(spool, busy, 2, listOf(unit))
        advanceUntilIdle()
        job.cancel()
        assertTrue(seen.single().contains("printing"))
        assertEquals(null, vm.confirmation.value)
        assertTrue(ams.calls.isEmpty())
    }

    @Test
    fun `an RFID spool in the slot is refused`() = runTest(dispatcher) {
        val rfid = unit.copy(trays = listOf(unit.trays.first().copy(hasRfidTag = true)))
        val seen = mutableListOf<String>()
        val job = collect(this, seen)
        vm.requestLoad(spool, rfid, 2, listOf(unit))
        advanceUntilIdle()
        job.cancel()
        assertTrue(seen.single().contains("RFID"))
        assertEquals(null, vm.confirmation.value)
    }

    @Test
    fun `developer mode off and unknown capabilities are both refused`() = runTest(dispatcher) {
        val off = unit.copy(capabilities = unit.capabilities.copy(developerMode = false))
        vm.requestLoad(spool, off, 2, listOf(unit))
        assertEquals(null, vm.confirmation.value)
        vm.requestLoad(spool, unit.copy(capabilities = PrinterCapabilities.UNKNOWN), 2, listOf(unit))
        assertEquals(null, vm.confirmation.value)
        advanceUntilIdle()
        assertTrue(ams.calls.isEmpty())
    }

    @Test
    fun `a pending configuration is reported and no read-back is attempted`() = runTest(dispatcher) {
        ams.pendingConfig = true
        ams.units = ApiResult.Success(listOf(unit))
        val seen = mutableListOf<String>()
        val job = collect(this, seen)
        vm.requestLoad(spool, unit, 2, listOf(unit))
        vm.confirmLoad()
        advanceUntilIdle()
        job.cancel()
        assertTrue(seen.single().startsWith("Saved. The slot looks empty"))
    }

    @Test
    fun `read-back confirms when the slot shows the assigned spool`() = runTest(dispatcher) {
        ams.units = ApiResult.Success(listOf(unit))
        val seen = mutableListOf<String>()
        val job = collect(this, seen)
        vm.requestLoad(spool, unit, 2, listOf(unit))
        vm.confirmLoad()
        advanceUntilIdle()
        job.cancel()
        assertEquals("Sent to the printer. Checking the slot…", seen.first())
        assertTrue(seen.last(), seen.last().startsWith("Printer confirmed"))
    }

    @Test
    fun `read-back says the printer may have refused when the slot still shows something else`() = runTest(dispatcher) {
        ams.units = ApiResult.Success(listOf(unit))
        val petg = spool.copy(material = "PETG")
        val seen = mutableListOf<String>()
        val job = collect(this, seen)
        vm.requestLoad(petg, unit, 2, listOf(unit))
        vm.confirmLoad()
        advanceUntilIdle()
        job.cancel()
        assertTrue(seen.last(), seen.last().startsWith("Saved, but the printer still shows PLA Basic"))
    }

    @Test
    fun `the late gate's reason reaches the user, not the generic text`() = runTest(dispatcher) {
        ams.loadError = ApiResult.Error(message = "The printer is printing (or paused mid-print).", errorType = ApiErrorType.UNSUPPORTED)
        val seen = mutableListOf<String>()
        val job = collect(this, seen)
        vm.requestLoad(spool, unit, 2, listOf(unit))
        vm.confirmLoad()
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf("The printer is printing (or paused mid-print)."), seen)
    }

    @Test
    fun `a read-back that never succeeds says it could not check, not that the printer refused`() = runTest(dispatcher) {
        ams.units = ApiResult.Error(message = "down", errorType = ApiErrorType.NETWORK_ERROR)
        val seen = mutableListOf<String>()
        val job = collect(this, seen)
        vm.requestLoad(spool, unit, 2, listOf(unit))
        vm.confirmLoad()
        advanceUntilIdle()
        job.cancel()
        assertTrue(seen.last(), seen.last().contains("couldn't be re-read"))
        assertTrue(seen.none { it.contains("may have refused") })
    }

    @Test
    fun `changed is emitted after a successful load and after unloading, not after a refusal`() = runTest(dispatcher) {
        ams.units = ApiResult.Success(listOf(unit))
        var changes = 0
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.changed.collect { changes++ } }
        vm.requestLoad(spool, unit, 2, listOf(unit))
        vm.confirmLoad()
        advanceUntilIdle()
        val afterLoad = changes
        assertTrue(afterLoad >= 1)

        vm.unload(unit, 2, spool.id)
        advanceUntilIdle()
        assertEquals(afterLoad + 1, changes)

        ams.loadError = ApiResult.Error(message = "no", errorType = ApiErrorType.UNSUPPORTED)
        vm.requestLoad(spool, unit, 2, listOf(unit))
        vm.confirmLoad()
        advanceUntilIdle()
        job.cancel()
        assertEquals(afterLoad + 1, changes)
    }
}
