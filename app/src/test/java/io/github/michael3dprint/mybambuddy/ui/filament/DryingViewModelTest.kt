package io.github.michael3dprint.mybambuddy.ui.filament

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DryingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val unit = testAmsUnit
    private val ams = FakeAms()
    private lateinit var vm: DryingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = DryingViewModel(ams)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `drying uses the preset for the loaded material and sends the base material name`() = runTest(dispatcher) {
        val draft = vm.draftFor(unit)
        assertEquals(55, draft.tempC)
        vm.updateDraft(unit, DryingDraft("ABS/ASA", 80, 8))
        vm.startDrying(unit)
        advanceUntilIdle()
        assertEquals(listOf("dry:1:0:80:8:ABS"), ams.calls)
    }

    @Test
    fun `server refusal is reported as a message and nothing is retried`() = runTest(dispatcher) {
        ams.drying = ApiResult.Error(message = "busy", errorType = ApiErrorType.REJECTED, serverDetail = "AMS is busy")
        val seen = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { seen += it } }
        vm.startDrying(unit)
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf("AMS is busy"), seen)
        assertEquals(1, ams.calls.size)
    }

    @Test
    fun `a successful start emits changed and a refusal does not`() = runTest(dispatcher) {
        var changes = 0
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.changed.collect { changes++ } }
        vm.startDrying(unit)
        advanceUntilIdle()
        assertEquals(1, changes)

        ams.drying = ApiResult.Error(message = "busy", errorType = ApiErrorType.REJECTED)
        vm.startDrying(unit)
        advanceUntilIdle()
        job.cancel()
        assertEquals(1, changes)
    }

    @Test
    fun `stopping drying emits changed`() = runTest(dispatcher) {
        var changes = 0
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.changed.collect { changes++ } }
        vm.stopDrying(unit)
        advanceUntilIdle()
        job.cancel()
        assertEquals(1, changes)
        assertEquals(listOf("stop"), ams.calls)
    }
}
