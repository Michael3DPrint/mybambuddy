package io.github.michael3dprint.mybambuddy.ui.queue

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.PrinterReadiness
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.model.QueueStatus
import io.github.michael3dprint.mybambuddy.domain.policy.JobChange
import io.github.michael3dprint.mybambuddy.domain.policy.ReadinessEvaluator
import io.github.michael3dprint.mybambuddy.domain.repo.QueueRepository
import io.github.michael3dprint.mybambuddy.domain.repo.ReadinessCheck
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val item = QueueItem(4, 1, "X2D", "benchy", QueueStatus.PENDING, 1, null, false, libraryFileId = 9)

    private val queue = object : QueueRepository {
        override suspend fun getQueue() = ApiResult.Success(listOf(item))

        override suspend fun reorder(order: List<Pair<Int, Int>>): ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun start(itemId: Int, printerId: Int?, skipFilamentCheck: Boolean): ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun picture(item: QueueItem): ApiResult<ByteArray> = if (item.id ==
            4
        ) {
            ApiResult.Success(byteArrayOf(1, 2))
        } else {
            ApiResult.Error(message = "none", errorType = ApiErrorType.NOT_FOUND)
        }

        override suspend fun update(itemId: Int, change: JobChange): ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun cancel(itemId: Int): ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun remove(itemId: Int): ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun resumeQueue(printerId: Int): ApiResult<Unit> = ApiResult.Success(Unit)
    }

    private fun readiness(result: ApiResult<List<PrinterReadiness>>) = object : ReadinessCheck {
        override suspend fun checkAll() = result

        override suspend fun check(printerId: String, printerName: String): ApiResult<PrinterReadiness> =
            ApiResult.Error(message = "unused", errorType = ApiErrorType.UNKNOWN)
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `start is blocked for a printer in limited mode, with the reason`() = runTest(dispatcher) {
        val limited = ReadinessEvaluator.evaluate("1", "X2D", PrinterCapabilities(connected = true, developerMode = false))
        val vm = QueueViewModel(queue, readiness = readiness(ApiResult.Success(listOf(limited))))
        advanceUntilIdle()
        assertTrue(vm.startBlocked.value.getValue(1).contains("Developer LAN mode"))
    }

    @Test
    fun `nothing is blocked in full mode or when the check fails`() = runTest(dispatcher) {
        val full = ReadinessEvaluator.evaluate("1", "X2D", PrinterCapabilities(connected = true, developerMode = true))
        val ok = QueueViewModel(queue, readiness = readiness(ApiResult.Success(listOf(full))))
        val failed = QueueViewModel(queue, readiness = readiness(ApiResult.Error(message = "x", errorType = ApiErrorType.NETWORK_ERROR)))
        val none = QueueViewModel(queue)
        advanceUntilIdle()
        assertEquals(emptyMap<Int, String>(), ok.startBlocked.value)
        assertEquals(emptyMap<Int, String>(), failed.startBlocked.value)
        assertEquals(emptyMap<Int, String>(), none.startBlocked.value)
    }

    @Test
    fun `a printer-scoped queue shows only that printer's jobs`() = runTest(dispatcher) {
        val two = QueueItem(5, 2, "P1S", "other", QueueStatus.PENDING, 1, null, false)
        val both = object : QueueRepository by queue {
            override suspend fun getQueue() = ApiResult.Success(listOf(item, two))
        }
        val scoped = QueueViewModel(both, printerId = 2)
        val all = QueueViewModel(both)
        advanceUntilIdle()
        assertEquals(listOf(5), (scoped.state.value as QueueState.Success).items.map { it.id })
        assertEquals(listOf(4, 5), (all.state.value as QueueState.Success).items.map { it.id })
    }

    @Test
    fun `pictures are loaded after the list and a job without one is simply absent`() = runTest(dispatcher) {
        val vm = QueueViewModel(queue)
        advanceUntilIdle()
        assertTrue(vm.state.value is QueueState.Success)
        assertEquals(setOf(4), vm.pictures.value.keys)
        assertEquals(2, vm.pictures.value[4]?.size)
    }

    @Test
    fun `Start now says what became of the job instead of just started`() = runTest(dispatcher) {
        val reason = "X2D refused to store the file — check its SD card"
        val scripted = io.github.michael3dprint.mybambuddy.testutil.ScriptedQueue(listOf(listOf(item.copy(waitingReason = reason))))
        val vm = QueueViewModel(scripted)
        val messages = mutableListOf<String>()
        val collector = backgroundScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) { vm.messages.collect { messages += it } }
        advanceUntilIdle()
        vm.start(item)
        advanceUntilIdle()
        assertTrue(messages.first().startsWith("Bambuddy accepted the start of benchy"))
        assertTrue(messages.last().startsWith("It won't print yet."))
        assertTrue(messages.last().contains("SD card"))
    }

    @Test
    fun `finished jobs can be cleared and a waiting or printing job is never touched`() = runTest(dispatcher) {
        val removed = mutableListOf<Int>()
        val q = object : QueueRepository by queue {
            override suspend fun remove(itemId: Int): ApiResult<Unit> {
                removed += itemId
                return if (itemId == 6) ApiResult.Error(message = "x", errorType = ApiErrorType.REJECTED) else ApiResult.Success(Unit)
            }
        }
        val vm = QueueViewModel(q)
        val jobs = listOf(
            item.copy(id = 5, status = QueueStatus.CANCELLED),
            item.copy(id = 6, status = QueueStatus.FAILED),
            item.copy(id = 7, status = QueueStatus.PENDING),
            item.copy(id = 8, status = QueueStatus.PRINTING),
        )
        val messages = mutableListOf<String>()
        backgroundScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) { vm.messages.collect { messages += it } }
        advanceUntilIdle()
        vm.clearFinished(jobs)
        advanceUntilIdle()
        assertEquals(listOf(5, 6), removed)
        assertEquals("Cleared 1 finished job(s); 1 could not be removed.", messages.last())
        assertTrue(item.copy(status = QueueStatus.COMPLETED).isFinished && !item.copy(status = QueueStatus.PRINTING).isFinished)
    }

    @Test
    fun `a filament refusal asks before printing anyway and the second start skips the check once`() = runTest(dispatcher) {
        val scripted = io.github.michael3dprint.mybambuddy.testutil.ScriptedQueue(listOf(listOf(item))).apply {
            startResult = ApiResult.Error(
                code = 409,
                message = "x",
                errorType = ApiErrorType.REJECTED,
                serverDetail = "Not enough filament: filament 1 (PLA) needs about 31 g, the slot has about 12 g.",
                serverCode = "insufficient_filament",
            )
        }
        val vm = QueueViewModel(scripted)
        val prompts = mutableListOf<ShortfallPrompt>()
        val messages = mutableListOf<String>()
        backgroundScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) { vm.shortfalls.collect { prompts += it } }
        backgroundScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) { vm.messages.collect { messages += it } }
        advanceUntilIdle()
        vm.start(item)
        advanceUntilIdle()
        assertEquals(1, prompts.size)
        assertTrue(prompts.single().text.contains("needs about 31 g"))
        assertEquals(false, scripted.lastSkip)
        assertTrue(messages.none { it.startsWith("Bambuddy accepted") })
        scripted.startResult = ApiResult.Success(Unit)
        vm.start(item, skipFilamentCheck = true)
        advanceUntilIdle()
        assertEquals(true, scripted.lastSkip)
        assertEquals(1, prompts.size)
    }

    @Test
    fun `a conflict that is not about filament is shown as the server's reason, not blamed on the printer`() = runTest(dispatcher) {
        val scripted = io.github.michael3dprint.mybambuddy.testutil.ScriptedQueue(listOf(listOf(item))).apply {
            startResult =
                ApiResult.Error(code = 409, message = "x", errorType = ApiErrorType.REJECTED, serverDetail = "Job is no longer pending")
        }
        val vm = QueueViewModel(scripted)
        val prompts = mutableListOf<ShortfallPrompt>()
        val messages = mutableListOf<String>()
        backgroundScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) { vm.shortfalls.collect { prompts += it } }
        backgroundScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) { vm.messages.collect { messages += it } }
        advanceUntilIdle()
        vm.start(item)
        advanceUntilIdle()
        assertTrue(prompts.isEmpty())
        assertEquals("Job is no longer pending", messages.last())
    }
}
