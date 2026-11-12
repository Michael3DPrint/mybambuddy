package io.github.michael3dprint.mybambuddy.ui.maintenance

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.connectivity.DefaultConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.DueState
import io.github.michael3dprint.mybambuddy.domain.model.MaintenanceItem
import io.github.michael3dprint.mybambuddy.domain.model.PrinterMaintenance
import io.github.michael3dprint.mybambuddy.domain.repo.MaintenanceRepository
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
class MaintenanceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val overview = listOf(
        PrinterMaintenance(1, "X2D", 12.0, listOf(MaintenanceItem(7, 1, "Rods", DueState.DUE, 1f, "3 h overdue", null))),
    )

    private class Fake : MaintenanceRepository {
        var overview: ApiResult<List<PrinterMaintenance>> = ApiResult.Success(emptyList())
        var done: ApiResult<Unit> = ApiResult.Success(Unit)
        val doneCalls = mutableListOf<Int>()

        override suspend fun getOverview() = overview

        override suspend fun markDone(itemId: Int): ApiResult<Unit> {
            doneCalls += itemId
            return done
        }
    }

    private val fake = Fake()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `it loads the overview on creation`() = runTest(dispatcher) {
        fake.overview = ApiResult.Success(overview)
        val vm = MaintenanceViewModel(fake)
        advanceUntilIdle()
        assertEquals(MaintenanceState.Success(overview), vm.state.value)
    }

    @Test
    fun `a failed first load shows an error and a failed refresh keeps the data`() = runTest(dispatcher) {
        fake.overview = ApiResult.Error(message = "down", errorType = ApiErrorType.NETWORK_ERROR)
        val vm = MaintenanceViewModel(fake)
        advanceUntilIdle()
        assertTrue(vm.state.value is MaintenanceState.Error)

        fake.overview = ApiResult.Success(overview)
        vm.refresh()
        advanceUntilIdle()
        fake.overview = ApiResult.Error(message = "down", errorType = ApiErrorType.NETWORK_ERROR)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(MaintenanceState.Success(overview), vm.state.value)
    }

    @Test
    fun `marking done says so and refreshes`() = runTest(dispatcher) {
        fake.overview = ApiResult.Success(overview)
        val vm = MaintenanceViewModel(fake)
        val seen = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { seen += it } }
        vm.markDone(7, "Rods")
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf(7), fake.doneCalls)
        assertEquals(listOf("Rods marked as done"), seen)
    }

    @Test
    fun `a refusal from the server is shown as its own message`() = runTest(dispatcher) {
        fake.done = ApiResult.Error(message = "m", errorType = ApiErrorType.REJECTED, serverDetail = "Already done")
        val vm = MaintenanceViewModel(fake)
        val seen = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { seen += it } }
        vm.markDone(7, "Rods")
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf("Already done"), seen)
    }

    @Test
    fun `offline it refuses to mark anything done and sends nothing`() = runTest(dispatcher) {
        val offline = DefaultConnectivityStatus().also { it.markUnreachable() }
        val vm = MaintenanceViewModel(fake, offline)
        val seen = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { seen += it } }
        vm.markDone(7, "Rods")
        advanceUntilIdle()
        job.cancel()
        assertTrue(fake.doneCalls.isEmpty())
        assertTrue(seen.single(), seen.single().contains("offline"))
    }

    @Test
    fun `a printer-scoped view shows only that printer`() = runTest(dispatcher) {
        fake.overview = ApiResult.Success(overview + PrinterMaintenance(2, "P1S", 3.0, emptyList()))
        val vm = MaintenanceViewModel(fake, printerId = 2)
        advanceUntilIdle()
        assertEquals(listOf(2), (vm.state.value as MaintenanceState.Success).printers.map { it.printerId })
    }
}
