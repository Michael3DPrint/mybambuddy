package io.github.michael3dprint.mybambuddy.ui.filament

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.DryPhase
import io.github.michael3dprint.mybambuddy.domain.model.PrintActivity
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.PrinterControlCommand
import io.github.michael3dprint.mybambuddy.domain.repo.PrintControlRepository
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
class AmsFilamentViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class Control(var result: ApiResult<Unit> = ApiResult.Success(Unit)) : PrintControlRepository {
        val sent = mutableListOf<Pair<String, PrinterControlCommand>>()

        override suspend fun executeCommand(printerId: String, command: PrinterControlCommand): ApiResult<Unit> {
            sent += printerId to command
            return result
        }

        override suspend fun pausePrint(printerId: String) = error("unused")

        override suspend fun resumePrint(printerId: String) = error("unused")

        override suspend fun cancelPrint(printerId: String) = error("unused")

        override suspend fun refreshStatus(printerId: String) = error("unused")

        override suspend fun clearHms(printerId: String) = error("unused")

        override suspend fun clearPlate(printerId: String) = error("unused")

        override suspend fun setPrintSpeed(printerId: String, mode: Int) = error("unused")
    }

    private val ready = PrinterCapabilities(connected = true, developerMode = true, hasAms = true, activity = PrintActivity.IDLE)

    private fun unit(caps: PrinterCapabilities = ready, amsId: Int = 1, ht: Boolean = false) =
        AmsUnit(1, "X2D", amsId, ht, null, null, false, DryPhase.OFF, 0, null, emptyList(), caps)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a request waits for confirmation and sends nothing`() = runTest(dispatcher) {
        val control = Control()
        val vm = AmsFilamentViewModel(control)
        vm.request(FeedAction.LOAD, unit(), trayId = 2)
        advanceUntilIdle()
        assertEquals(FeedAction.LOAD, vm.pending.value?.action)
        assertEquals(6, vm.pending.value?.trayNumber)
        assertTrue(control.sent.isEmpty())
    }

    @Test
    fun `confirming sends exactly one command to the right printer and tray, then clears`() = runTest(dispatcher) {
        val control = Control()
        val vm = AmsFilamentViewModel(control)
        vm.request(FeedAction.UNLOAD, unit(), trayId = 2)
        vm.confirm()
        advanceUntilIdle()
        assertEquals(listOf("1" to PrinterControlCommand.AmsUnloadFilament(6)), control.sent)
        assertNull(vm.pending.value)
        vm.confirm()
        advanceUntilIdle()
        assertEquals(1, control.sent.size)
    }

    @Test
    fun `cancel sends nothing`() = runTest(dispatcher) {
        val control = Control()
        val vm = AmsFilamentViewModel(control)
        vm.request(FeedAction.LOAD, unit(), trayId = 0)
        vm.cancel()
        vm.confirm()
        advanceUntilIdle()
        assertTrue(control.sent.isEmpty())
        assertNull(vm.pending.value)
    }

    @Test
    fun `a busy, offline or developer-mode-off printer is refused with its reason and nothing is pending`() = runTest(dispatcher) {
        val control = Control()
        val vm = AmsFilamentViewModel(control)
        val reasons = mutableListOf<String>()
        val job = launch { vm.messages.collect { reasons += it } }
        advanceUntilIdle()
        listOf(
            ready.copy(activity = PrintActivity.BUSY),
            ready.copy(connected = false),
            ready.copy(developerMode = false),
        ).forEach { vm.request(FeedAction.LOAD, unit(it), trayId = 0) }
        advanceUntilIdle()
        job.cancel()
        assertNull(vm.pending.value)
        assertTrue(control.sent.isEmpty())
        assertEquals(3, reasons.size)
        assertTrue(reasons[0].contains("printing"))
    }

    @Test
    fun `a slot that cannot be addressed is refused`() = runTest(dispatcher) {
        val vm = AmsFilamentViewModel(Control())
        vm.request(FeedAction.LOAD, unit(amsId = 9), trayId = 0)
        advanceUntilIdle()
        assertNull(vm.pending.value)
    }

    @Test
    fun `a failure from the server is reported and the slots are not refreshed`() = runTest(dispatcher) {
        val control =
            Control(ApiResult.Error(code = 409, message = "No hotend is loaded from that slot", errorType = ApiErrorType.REJECTED))
        val vm = AmsFilamentViewModel(control)
        var changed = 0
        val job = launch { vm.changed.collect { changed++ } }
        advanceUntilIdle()
        vm.request(FeedAction.UNLOAD, unit(), trayId = 0)
        vm.confirm()
        advanceUntilIdle()
        job.cancel()
        assertEquals(1, control.sent.size)
        assertEquals(0, changed)
    }
}
