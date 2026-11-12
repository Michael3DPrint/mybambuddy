package io.github.michael3dprint.mybambuddy.ui.readiness

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticCheck
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticOverall
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticReport
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticStatus
import io.github.michael3dprint.mybambuddy.domain.repo.DiagnosticsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class DiagnosticsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val report = DiagnosticReport(DiagnosticOverall.OK, listOf(DiagnosticCheck("port_mqtt", DiagnosticStatus.PASS)))

    private class Fake : DiagnosticsRepository {
        var gate: CompletableDeferred<ApiResult<DiagnosticReport>> = CompletableDeferred()
        var calls = 0

        override suspend fun run(printerId: Int): ApiResult<DiagnosticReport> {
            calls++
            return gate.await()
        }
    }

    private val fake = Fake()
    private lateinit var vm: DiagnosticsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = DiagnosticsViewModel(fake)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `nothing runs until asked`() = runTest(dispatcher) {
        advanceUntilIdle()
        assertEquals(0, fake.calls)
        assertTrue(vm.states.value.isEmpty())
    }

    @Test
    fun `a run goes from running to done`() = runTest(dispatcher) {
        vm.run("1")
        advanceUntilIdle()
        assertEquals(DiagnosticsState.Running, vm.states.value["1"])
        fake.gate.complete(ApiResult.Success(report))
        advanceUntilIdle()
        assertEquals(DiagnosticsState.Done(report), vm.states.value["1"])
    }

    @Test
    fun `tapping again while it runs does not start a second run`() = runTest(dispatcher) {
        vm.run("1")
        vm.run("1")
        advanceUntilIdle()
        assertEquals(1, fake.calls)
    }

    @Test
    fun `a failure shows a message that does not blame the printer`() = runTest(dispatcher) {
        fake.gate.complete(ApiResult.Error(message = "m", errorType = ApiErrorType.REJECTED))
        vm.run("1")
        advanceUntilIdle()
        val failed = vm.states.value["1"] as DiagnosticsState.Failed
        assertTrue(failed.message, !failed.message.contains("printer", ignoreCase = true))
    }

    @Test
    fun `a printer id that is not a number is ignored`() = runTest(dispatcher) {
        vm.run("abc")
        advanceUntilIdle()
        assertEquals(0, fake.calls)
    }

    @Test
    fun `each printer has its own state`() = runTest(dispatcher) {
        fake.gate.complete(ApiResult.Success(report))
        vm.run("1")
        vm.run("2")
        advanceUntilIdle()
        assertEquals(setOf("1", "2"), vm.states.value.keys)
    }
}
