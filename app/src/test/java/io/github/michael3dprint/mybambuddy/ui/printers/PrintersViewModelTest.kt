package io.github.michael3dprint.mybambuddy.ui.printers

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.ErrorKind
import io.github.michael3dprint.mybambuddy.domain.model.PrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrintersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakePrinterRepository
    private lateinit var viewModel: PrintersViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakePrinterRepository()
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            viewModel.stopPolling()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading before flow emits`() = runTest {
        fakeRepository.printersFlow = emptyFlow()
        viewModel = PrintersViewModel(fakeRepository)

        assertEquals(PrintersUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `updates uiState to Success when repository emits printers`() = runTest {
        val testPrinters = listOf(
            Printer(
                id = "p1",
                name = "Bambu X1C",
                model = "X1C",
                state = PrinterState.PRINTING,
                progress = 50,
                remainingTimeSeconds = 1800,
                remainingTimeFormatted = "30m left",
                currentLayer = 50,
                totalLayers = 100,
                hasHmsWarning = false,
                hmsCount = 0,
                snapshotUrl = "",
            ),
        )

        fakeRepository.printersFlow = flowOf(ApiResult.Success(testPrinters))
        viewModel = PrintersViewModel(fakeRepository)
        viewModel.startPolling()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PrintersUiState.Success)
        val successState = state as PrintersUiState.Success
        assertEquals(1, successState.printers.size)
        assertEquals("Bambu X1C", successState.printers.first().name)
    }

    @Test
    fun `updates uiState to Error with Tailscale message when network fails`() = runTest {
        fakeRepository.printersFlow = flowOf(
            ApiResult.Error(
                message = "Connection timeout",
                errorType = ApiErrorType.NETWORK_ERROR,
            ),
        )

        viewModel = PrintersViewModel(fakeRepository)
        viewModel.startPolling()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PrintersUiState.Error)
        val errorState = state as PrintersUiState.Error
        assertEquals(ErrorKind.NETWORK_UNREACHABLE, errorState.errorState.kind)
        assertTrue(errorState.errorState.message.contains("Tailscale"))
    }

    @Test
    fun `refresh triggers manual fetch and updates state`() = runTest {
        fakeRepository.printersFlow = flowOf(ApiResult.Success(emptyList()))
        fakeRepository.fetchPrintersResult = ApiResult.Success(
            listOf(
                Printer(
                    id = "p2",
                    name = "Bambu P1P",
                    model = "P1P",
                    state = PrinterState.IDLE,
                    progress = 0,
                    remainingTimeSeconds = 0,
                    remainingTimeFormatted = "0m left",
                    currentLayer = 0,
                    totalLayers = 0,
                    hasHmsWarning = false,
                    hmsCount = 0,
                    snapshotUrl = "",
                ),
            ),
        )

        viewModel = PrintersViewModel(fakeRepository)
        viewModel.startPolling()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PrintersUiState.Success)
        val successState = state as PrintersUiState.Success
        assertEquals("Bambu P1P", successState.printers.first().name)
    }

    private fun pollThenFail(errorType: ApiErrorType): PrintersUiState.Success {
        val printer = Printer(id = "p1", name = "Keep Me", model = "X2D")
        fakeRepository.printersFlow = kotlinx.coroutines.flow.flow {
            emit(ApiResult.Success(listOf(printer)))
            emit(ApiResult.Error(message = "down", errorType = errorType))
        }
        viewModel = PrintersViewModel(fakeRepository)
        viewModel.startPolling()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state is PrintersUiState.Success)
        return state as PrintersUiState.Success
    }

    @Test
    fun `a real server answer after success keeps last data and sets staleError`() = runTest {
        val state = pollThenFail(ApiErrorType.UNAUTHORIZED)
        assertEquals("Keep Me", state.printers.first().name)
        assertTrue(state.staleError != null)
    }

    @Test
    fun `an unreachable server keeps last data and adds no second banner`() = runTest {
        val state = pollThenFail(ApiErrorType.NETWORK_ERROR)
        assertEquals("Keep Me", state.printers.first().name)
        assertEquals(null, state.staleError)
    }

    @Test
    fun `the state carries one readiness report per printer`() = runTest {
        fakeRepository.printersFlow =
            flowOf(ApiResult.Success(listOf(Printer(id = "p1", name = "A", model = "X2D"), Printer(id = "p2", name = "B", model = "X2D"))))
        viewModel = PrintersViewModel(fakeRepository)
        viewModel.startPolling()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as PrintersUiState.Success
        assertEquals(listOf("p1", "p2"), state.readiness.map { it.printerId })
    }

    @Test
    fun `printers with upkeep due get a badge and others do not`() = runTest {
        val due = io.github.michael3dprint.mybambuddy.domain.model.MaintenanceItem(
            1,
            1,
            "Rods",
            io.github.michael3dprint.mybambuddy.domain.model.DueState.DUE,
            1f,
            "",
            null,
        )
        val ok = io.github.michael3dprint.mybambuddy.domain.model.MaintenanceItem(
            2,
            2,
            "Rods",
            io.github.michael3dprint.mybambuddy.domain.model.DueState.OK,
            0f,
            "",
            null,
        )
        val overview = listOf(
            io.github.michael3dprint.mybambuddy.domain.model.PrinterMaintenance(1, "A", 0.0, listOf(due)),
            io.github.michael3dprint.mybambuddy.domain.model.PrinterMaintenance(2, "B", 0.0, listOf(ok)),
        )
        val fake = object : io.github.michael3dprint.mybambuddy.domain.repo.MaintenanceRepository {
            override suspend fun getOverview() = ApiResult.Success(overview)

            override suspend fun markDone(itemId: Int) = ApiResult.Success(Unit)
        }
        viewModel = PrintersViewModel(fakeRepository, maintenance = fake)
        viewModel.startPolling()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf("1" to "1 due"), viewModel.maintenanceBadges.value)
    }

    @Test
    fun `polling uses the interval saved in settings`() = runTest {
        viewModel = PrintersViewModel(fakeRepository, pollIntervalMs = { 15_000L })
        viewModel.startPolling()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(15_000L, fakeRepository.requestedPollMs)
    }

    @Test
    fun `polling defaults to five seconds`() = runTest {
        viewModel = PrintersViewModel(fakeRepository)
        viewModel.startPolling()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(5_000L, fakeRepository.requestedPollMs)
    }

    private class FakePrinterRepository : PrinterRepository {
        var requestedPollMs: Long? = null
        var printersFlow: Flow<ApiResult<List<Printer>>> = flowOf(ApiResult.Success(emptyList()))
        var fetchPrintersResult: ApiResult<List<Printer>> = ApiResult.Success(emptyList())

        override fun getPrintersFlow(pollIntervalMs: Long): Flow<ApiResult<List<Printer>>> {
            requestedPollMs = pollIntervalMs
            return printersFlow
        }

        override suspend fun fetchPrinters(): ApiResult<List<Printer>> = fetchPrintersResult

        override suspend fun fetchPrinter(printerId: String): ApiResult<Printer> {
            val sample = Printer(
                id = printerId,
                name = "Printer $printerId",
                model = "X1C",
                state = PrinterState.PRINTING,
                progress = 50,
                remainingTimeSeconds = 1800,
                remainingTimeFormatted = "30m left",
                currentLayer = 50,
                totalLayers = 100,
                hasHmsWarning = false,
                hmsCount = 0,
                snapshotUrl = "",
            )
            return ApiResult.Success(sample)
        }

        override suspend fun fetchPrinterStatus(printerId: String): ApiResult<PrintStatus> = ApiResult.Success(PrintStatus())
    }
}
