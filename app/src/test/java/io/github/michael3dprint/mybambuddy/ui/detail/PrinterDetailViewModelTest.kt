package io.github.michael3dprint.mybambuddy.ui.detail

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.PrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterControlCommand
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.repo.PrintControlRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrinterDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakePrinterRepo: FakePrinterRepo
    private lateinit var fakeControlRepo: FakePrintControlRepo
    private lateinit var viewModel: PrinterDetailViewModel

    private val samplePrinter = Printer(
        id = "printer_01",
        name = "Bambu X1 Carbon",
        model = "X1C",
        state = PrinterState.PRINTING,
        progress = 45,
        remainingTimeSeconds = 2700,
        remainingTimeFormatted = "45m left",
        estimatedFinishTime = "1:00 PM",
        currentLayer = 90,
        totalLayers = 200,
        hasHmsWarning = false,
        hmsCount = 0,
        snapshotUrl = "http://localhost/snapshot.jpg",
        printName = "test_print.gcode",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePrinterRepo = FakePrinterRepo()
        fakeControlRepo = FakePrintControlRepo()

        fakePrinterRepo.printerResult = ApiResult.Success(samplePrinter)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads printer details on init and sets Success state`() = runTest {
        viewModel = PrinterDetailViewModel("printer_01", fakePrinterRepo, fakeControlRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PrinterDetailUiState.Success)
        val successState = state as PrinterDetailUiState.Success
        assertEquals("Bambu X1 Carbon", successState.printer.name)
        assertEquals(45, successState.printer.progress)
    }

    @Test
    fun `sets Error state on init when printer fetch fails`() = runTest {
        fakePrinterRepo.printerResult = ApiResult.Error(
            message = "Printer not found",
            errorType = ApiErrorType.NOT_FOUND,
        )

        viewModel = PrinterDetailViewModel("printer_01", fakePrinterRepo, fakeControlRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PrinterDetailUiState.Error)
        val errorState = state as PrinterDetailUiState.Error
        assertEquals("Resource Not Found", errorState.errorState.title)
    }

    @Test
    fun `controls do nothing while offline`() = runTest {
        val offline = io.github.michael3dprint.mybambuddy.domain.connectivity.DefaultConnectivityStatus().also { it.markOffline(1L) }
        viewModel = PrinterDetailViewModel("printer_01", fakePrinterRepo, fakeControlRepo, offline)
        testDispatcher.scheduler.advanceUntilIdle()
        val messages = mutableListOf<String>()
        val job = launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.userMessages.collect {
                messages +=
                    it
            }
        }

        viewModel.execute(PrinterControlCommand.Pause)
        testDispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(null, fakeControlRepo.lastPauseId)
        assertTrue(messages.single().contains("offline"))
    }

    @Test
    fun `snapshot is not polled, only refreshed on request`() = runTest {
        viewModel = PrinterDetailViewModel("printer_01", fakePrinterRepo, fakeControlRepo)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.startPolling()
        testDispatcher.scheduler.runCurrent()
        val first = (viewModel.uiState.value as PrinterDetailUiState.Success).snapshotTimestamp

        advanceTimeBy(4000L) // under the 5 s status poll: no automatic photo
        assertEquals(first, (viewModel.uiState.value as PrinterDetailUiState.Success).snapshotTimestamp)

        Thread.sleep(2)
        viewModel.refreshSnapshot()
        assertTrue((viewModel.uiState.value as PrinterDetailUiState.Success).snapshotTimestamp > first)

        viewModel.stopPolling()
    }

    @Test
    fun `status polling updates printer metrics every 5 seconds`() = runTest {
        viewModel = PrinterDetailViewModel("printer_01", fakePrinterRepo, fakeControlRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedPrinter = samplePrinter.copy(progress = 80, currentLayer = 160)
        fakePrinterRepo.printerResult = ApiResult.Success(updatedPrinter)

        viewModel.startPolling()
        advanceTimeBy(5050L)

        val successState = viewModel.uiState.value as PrinterDetailUiState.Success
        assertEquals(80, successState.printer.progress)
        assertEquals(160, successState.printer.currentLayer)

        viewModel.stopPolling()
    }

    @Test
    fun `pausePrint calls control repository and emits user message`() = runTest {
        viewModel = PrinterDetailViewModel("printer_01", fakePrinterRepo, fakeControlRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val messages = mutableListOf<String>()
        val job = launch { viewModel.userMessages.collect { messages.add(it) } }

        viewModel.execute(PrinterControlCommand.Pause)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("printer_01", fakeControlRepo.lastPauseId)
        assertTrue(messages.contains("Pause command sent"))

        job.cancel()
    }

    @Test
    fun `resumePrint calls control repository and emits user message`() = runTest {
        viewModel = PrinterDetailViewModel("printer_01", fakePrinterRepo, fakeControlRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val messages = mutableListOf<String>()
        val job = launch { viewModel.userMessages.collect { messages.add(it) } }

        viewModel.execute(PrinterControlCommand.Resume)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("printer_01", fakeControlRepo.lastResumeId)
        assertTrue(messages.contains("Resume command sent"))

        job.cancel()
    }

    @Test
    fun `cancelPrint calls control repository and emits user message`() = runTest {
        viewModel = PrinterDetailViewModel("printer_01", fakePrinterRepo, fakeControlRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val messages = mutableListOf<String>()
        val job = launch { viewModel.userMessages.collect { messages.add(it) } }

        viewModel.execute(PrinterControlCommand.Cancel)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("printer_01", fakeControlRepo.lastCancelId)
        assertTrue(messages.contains("Print canceled"))

        job.cancel()
    }

    @Test
    fun `setPrintSpeed calls control repository and emits speed message`() = runTest {
        viewModel = PrinterDetailViewModel("printer_01", fakePrinterRepo, fakeControlRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val messages = mutableListOf<String>()
        val job = launch { viewModel.userMessages.collect { messages.add(it) } }

        viewModel.execute(PrinterControlCommand.SetPrintSpeed(3))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("printer_01", fakeControlRepo.lastSpeedId)
        assertEquals(3, fakeControlRepo.lastSpeedMode)
        assertTrue(messages.contains("Print speed set to Sport (124%)"))

        job.cancel()
    }

    @Test
    fun `handles error from control repository gracefully`() = runTest {
        fakeControlRepo.shouldSucceed = false
        fakeControlRepo.errorMessage = "Endpoint unmapped (404)"

        viewModel = PrinterDetailViewModel("printer_01", fakePrinterRepo, fakeControlRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        val messages = mutableListOf<String>()
        val job = launch { viewModel.userMessages.collect { messages.add(it) } }

        viewModel.execute(PrinterControlCommand.Pause)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(messages.any { it.contains("Pause failed:") })

        job.cancel()
    }

    private class FakePrinterRepo : PrinterRepository {
        var printerResult: ApiResult<Printer> = ApiResult.Error(message = "Not initialized", errorType = ApiErrorType.UNKNOWN)

        override fun getPrintersFlow(pollIntervalMs: Long): Flow<ApiResult<List<Printer>>> = flowOf(ApiResult.Success(emptyList()))

        override suspend fun fetchPrinters(): ApiResult<List<Printer>> = ApiResult.Success(emptyList())

        override suspend fun fetchPrinter(printerId: String): ApiResult<Printer> = printerResult

        override suspend fun fetchPrinterStatus(printerId: String): ApiResult<PrintStatus> = ApiResult.Success(PrintStatus())
    }

    private class FakePrintControlRepo : PrintControlRepository {
        var shouldSucceed = true
        var errorMessage = "Operation failed"
        var lastPauseId: String? = null
        var lastResumeId: String? = null
        var lastCancelId: String? = null
        var lastSpeedId: String? = null
        var lastSpeedMode: Int? = null

        override suspend fun pausePrint(printerId: String): ApiResult<Unit> {
            lastPauseId = printerId
            return if (shouldSucceed) ApiResult.Success(Unit) else ApiResult.Error(message = errorMessage, errorType = ApiErrorType.UNKNOWN)
        }

        override suspend fun resumePrint(printerId: String): ApiResult<Unit> {
            lastResumeId = printerId
            return if (shouldSucceed) ApiResult.Success(Unit) else ApiResult.Error(message = errorMessage, errorType = ApiErrorType.UNKNOWN)
        }

        override suspend fun cancelPrint(printerId: String): ApiResult<Unit> {
            lastCancelId = printerId
            return if (shouldSucceed) ApiResult.Success(Unit) else ApiResult.Error(message = errorMessage, errorType = ApiErrorType.UNKNOWN)
        }

        override suspend fun refreshStatus(printerId: String): ApiResult<Unit> =
            if (shouldSucceed) ApiResult.Success(Unit) else ApiResult.Error(message = errorMessage, errorType = ApiErrorType.UNKNOWN)

        override suspend fun clearHms(printerId: String): ApiResult<Unit> =
            if (shouldSucceed) ApiResult.Success(Unit) else ApiResult.Error(message = errorMessage, errorType = ApiErrorType.UNKNOWN)

        override suspend fun clearPlate(printerId: String): ApiResult<Unit> =
            if (shouldSucceed) ApiResult.Success(Unit) else ApiResult.Error(message = errorMessage, errorType = ApiErrorType.UNKNOWN)

        override suspend fun setPrintSpeed(printerId: String, mode: Int): ApiResult<Unit> {
            lastSpeedId = printerId
            lastSpeedMode = mode
            return if (shouldSucceed) ApiResult.Success(Unit) else ApiResult.Error(message = errorMessage, errorType = ApiErrorType.UNKNOWN)
        }
    }
}
