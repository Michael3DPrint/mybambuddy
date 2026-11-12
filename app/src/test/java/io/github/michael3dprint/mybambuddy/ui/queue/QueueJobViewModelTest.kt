package io.github.michael3dprint.mybambuddy.ui.queue

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.JobOptions
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldAccess
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldModel
import io.github.michael3dprint.mybambuddy.domain.model.PlateInfo
import io.github.michael3dprint.mybambuddy.domain.model.PrintFile
import io.github.michael3dprint.mybambuddy.domain.model.PrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.model.ProjectSlot
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.model.QueueStatus
import io.github.michael3dprint.mybambuddy.domain.model.RequiredFilament
import io.github.michael3dprint.mybambuddy.domain.model.SliceJobState
import io.github.michael3dprint.mybambuddy.domain.model.SliceRequest
import io.github.michael3dprint.mybambuddy.domain.model.SlicerPreset
import io.github.michael3dprint.mybambuddy.domain.model.SlicerPresets
import io.github.michael3dprint.mybambuddy.domain.model.StagedPrint
import io.github.michael3dprint.mybambuddy.domain.model.TriState
import io.github.michael3dprint.mybambuddy.domain.policy.JobChange
import io.github.michael3dprint.mybambuddy.domain.print.PrintSource
import io.github.michael3dprint.mybambuddy.domain.repo.PrintFileRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import io.github.michael3dprint.mybambuddy.domain.repo.QueueRepository
import io.github.michael3dprint.mybambuddy.ui.filament.FakeAms
import io.github.michael3dprint.mybambuddy.ui.filament.testAmsUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueJobViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val plate = PlateInfo(1, null, 600, 5.0, listOf(RequiredFilament(1, "PLA", "307FE2", 5.0)))
    private val file = PrintFile(4, "benchy.gcode.3mf", "X2D", listOf(plate), createdHere = false)
    private val newFile = PrintFile(9, "benchy-new.gcode.3mf", "X2D", listOf(plate), createdHere = true, alsoCreated = emptyList())
    private val waiting =
        QueueItem(7, 1, "X2D", "benchy", QueueStatus.PENDING, 3, null, false, libraryFileId = 4, plateId = 1, amsMapping = listOf(2))

    private class FakeQueue(var item: QueueItem) : QueueRepository {
        val calls = mutableListOf<String>()
        val changes = mutableListOf<JobChange>()
        var updateResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var removeResult: ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun getQueue() = ApiResult.Success(listOf(item))

        override suspend fun reorder(order: List<Pair<Int, Int>>): ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun start(itemId: Int, printerId: Int?, skipFilamentCheck: Boolean): ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun picture(item: QueueItem): ApiResult<ByteArray> = ApiResult.Success(byteArrayOf(1))

        override suspend fun update(itemId: Int, change: JobChange): ApiResult<Unit> {
            calls += "update:$itemId"
            changes += change
            return updateResult
        }

        override suspend fun cancel(itemId: Int): ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun remove(itemId: Int): ApiResult<Unit> {
            calls += "remove:$itemId"
            return removeResult
        }

        override suspend fun resumeQueue(printerId: Int): ApiResult<Unit> = ApiResult.Success(Unit)
    }

    private inner class FakeFiles : PrintFileRepository {
        val calls = mutableListOf<String>()
        val staged = mutableListOf<StagedPrint>()
        var stageResult: ApiResult<Int> = ApiResult.Success(50)
        var sliceDone: SliceJobState = SliceJobState.Done(9, "benchy-new.gcode.3mf")

        override suspend fun open(libraryId: Int, name: String, createdHere: Boolean, alsoCreated: List<Int>): ApiResult<PrintFile> =
            ApiResult.Success(if (libraryId == 9) newFile.copy(alsoCreated = alsoCreated) else file)

        override suspend fun stage(print: StagedPrint): ApiResult<Int> {
            calls += "stage"
            staged += print
            return stageResult
        }

        override suspend fun discard(libraryId: Int): ApiResult<Unit> {
            calls += "discard:$libraryId"
            return ApiResult.Success(Unit)
        }

        override suspend fun slicerPresets() = ApiResult.Success(
            SlicerPresets(
                printers = listOf(SlicerPreset("cloud", "GM045", "Bambu Lab X2D 0.4 nozzle", null, emptyList())),
                processes = listOf(SlicerPreset("cloud", "GP1", "0.20mm Standard @BBL X2D", null, listOf("Bambu Lab X2D 0.4 nozzle"))),
                filaments = listOf(SlicerPreset("standard", "Bambu PLA Basic @BBL X2D", "Bambu PLA Basic @BBL X2D", "PLA", emptyList())),
            ),
        )

        override suspend fun projectSlots(libraryId: Int, plate: Int): ApiResult<List<ProjectSlot>> =
            ApiResult.Success(listOf(ProjectSlot(1, "PLA", "307FE2", true)))

        override suspend fun startSlice(libraryId: Int, request: SliceRequest): ApiResult<Int> {
            calls += "slice:$libraryId"
            return ApiResult.Success(3)
        }

        override suspend fun sliceJob(jobId: Int): ApiResult<SliceJobState> = ApiResult.Success(sliceDone)

        override suspend fun upload(source: PrintSource, onProgress: (Float) -> Unit): ApiResult<PrintFile> = TODO()

        override suspend fun makerWorldAccess(): ApiResult<MakerWorldAccess> = TODO()

        override suspend fun resolveMakerWorld(link: String): ApiResult<MakerWorldModel> = TODO()

        override suspend fun makerWorldImage(url: String): ApiResult<ByteArray> = TODO()

        override suspend fun importMakerWorld(model: MakerWorldModel, profileId: Int?): ApiResult<PrintFile> = TODO()

        override suspend fun start(itemId: Int, printerId: Int, skipFilamentCheck: Boolean): ApiResult<Unit> = TODO()

        override suspend fun preview(libraryId: Int, plate: Int, hasPlatePicture: Boolean): ApiResult<ByteArray> =
            ApiResult.Error(message = "none", errorType = ApiErrorType.NOT_FOUND)

        override suspend fun scaledCopy(source: PrintFile, percent: Int): ApiResult<PrintFile> = TODO()

        override suspend fun openArchive(archiveId: Int, name: String): ApiResult<PrintFile> = TODO()

        override suspend fun archivePreview(archiveId: Int, plate: Int, hasPlatePicture: Boolean): ApiResult<ByteArray> = TODO()
    }

    private class FakePrinters : PrinterRepository {
        override fun getPrintersFlow(pollIntervalMs: Long): Flow<ApiResult<List<Printer>>> = emptyFlow()

        override suspend fun fetchPrinters(): ApiResult<List<Printer>> =
            ApiResult.Success(listOf(Printer("1", "X2D", "X2D", PrinterState.IDLE)))

        override suspend fun fetchPrinter(printerId: String): ApiResult<Printer> =
            ApiResult.Error(message = "x", errorType = ApiErrorType.UNKNOWN)

        override suspend fun fetchPrinterStatus(printerId: String): ApiResult<PrintStatus> =
            ApiResult.Error(message = "x", errorType = ApiErrorType.UNKNOWN)
    }

    private val ams = FakeAms().apply { units = ApiResult.Success(listOf(testAmsUnit)) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(queue: FakeQueue, files: FakeFiles) = QueueJobViewModel(queue, files, FakePrinters(), ams)

    @Test
    fun `opening a waiting job reads it fresh and starts with nothing changed`() = runTest(dispatcher) {
        val queue = FakeQueue(waiting.copy(position = 5))
        val vm = vm(queue, FakeFiles())
        vm.open(waiting)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(5, s.job?.position)
        assertTrue(s.editable)
        assertTrue(s.change.isEmpty)
        assertFalse(s.canSave)
        assertEquals(2, s.chosen[1]?.tray)
        assertNotNull(s.preview)
    }

    @Test
    fun `a job that is not waiting is read-only and cannot be edited or saved`() = runTest(dispatcher) {
        val queue = FakeQueue(waiting.copy(status = QueueStatus.PRINTING))
        val vm = vm(queue, FakeFiles())
        vm.open(waiting)
        advanceUntilIdle()
        vm.edit(JobChoice.Options(JobOptions(timelapse = true)))
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.state.value.editable)
        assertTrue(vm.state.value.change.isEmpty)
        assertTrue(queue.calls.isEmpty())
    }

    @Test
    fun `a changed option is saved once as only that field`() = runTest(dispatcher) {
        val queue = FakeQueue(waiting)
        val vm = vm(queue, FakeFiles())
        vm.open(waiting)
        advanceUntilIdle()
        vm.edit(JobChoice.Options(JobOptions(bedLevelling = TriState.OFF)))
        assertEquals(JobChange(bedLevelling = TriState.OFF), vm.state.value.change)
        assertTrue(vm.state.value.canSave)
        vm.save()
        advanceUntilIdle()
        assertEquals(listOf("update:7"), queue.calls)
        assertEquals(JobChange(bedLevelling = TriState.OFF), queue.changes.single())
        assertEquals("Saved.", vm.state.value.message)
        assertTrue(vm.state.value.changedQueue)
    }

    @Test
    fun `choosing the slot that is already used changes nothing`() = runTest(dispatcher) {
        val queue = FakeQueue(waiting)
        val vm = vm(queue, FakeFiles())
        vm.open(waiting)
        advanceUntilIdle()
        vm.edit(JobChoice.Slot(1, vm.state.value.slots.first { it.tray == 2 }))
        assertTrue(vm.state.value.change.isEmpty)
    }

    @Test
    fun `a 409 on save says the job is no longer waiting and reloads`() = runTest(dispatcher) {
        val queue = FakeQueue(waiting).apply {
            updateResult = ApiResult.Error(code = 409, message = "x", errorType = ApiErrorType.REJECTED)
        }
        val vm = vm(queue, FakeFiles())
        vm.open(waiting)
        advanceUntilIdle()
        vm.edit(JobChoice.Options(JobOptions(timelapse = true)))
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.message!!.contains("no longer waiting"))
    }

    private suspend fun kotlinx.coroutines.test.TestScope.sliced(queue: FakeQueue, files: FakeFiles): QueueJobViewModel {
        val vm = vm(queue, files)
        vm.open(waiting)
        advanceUntilIdle()
        vm.reslice()
        advanceUntilIdle()
        vm.slice.choose(
            io.github.michael3dprint.mybambuddy.ui.print.SliceChoice.Bed(io.github.michael3dprint.mybambuddy.domain.model.BedType.COOL),
        )
        vm.slice.start()
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `a finished re-slice waits for the owner to confirm the replacement`() = runTest(dispatcher) {
        val files = FakeFiles()
        val vm = sliced(FakeQueue(waiting), files)
        assertEquals(9, vm.state.value.replacement?.libraryId)
        assertTrue(files.staged.isEmpty())
        assertEquals(listOf("slice:4"), files.calls.filter { it.startsWith("slice") })
    }

    @Test
    fun `replacing stages the new job at the old place first and only then removes the old one`() = runTest(dispatcher) {
        val queue = FakeQueue(waiting)
        val files = FakeFiles()
        val vm = sliced(queue, files)
        vm.edit(JobChoice.Options(JobOptions(timelapse = true)))
        vm.replace()
        advanceUntilIdle()
        val staged = files.staged.single()
        assertEquals(9, staged.libraryFileId)
        assertEquals(3, staged.insertPosition)
        assertEquals(listOf(2), staged.amsMapping)
        assertTrue(staged.options!!.timelapse)
        assertTrue(staged.cleanup)
        assertEquals(listOf("remove:7"), queue.calls)
        assertTrue(vm.state.value.closed)
    }

    @Test
    fun `when the new job cannot be added nothing is removed`() = runTest(dispatcher) {
        val queue = FakeQueue(waiting)
        val files = FakeFiles().apply {
            stageResult =
                ApiResult.Error(code = 400, message = "x", errorType = ApiErrorType.REJECTED, serverDetail = "Printer busy")
        }
        val vm = sliced(queue, files)
        vm.replace()
        advanceUntilIdle()
        assertTrue(queue.calls.none { it.startsWith("remove") })
        assertEquals("Printer busy", vm.state.value.message)
        assertFalse(vm.state.value.closed)
    }

    @Test
    fun `when the old job cannot be removed the message says both jobs are in the queue`() = runTest(dispatcher) {
        val queue = FakeQueue(waiting).apply {
            removeResult =
                ApiResult.Error(code = 409, message = "x", errorType = ApiErrorType.REJECTED, serverDetail = "Already started")
        }
        val vm = sliced(queue, FakeFiles())
        vm.replace()
        advanceUntilIdle()
        assertTrue(vm.state.value.message!!.contains("Both are in the queue"))
        assertTrue(vm.state.value.changedQueue)
    }

    @Test
    fun `keeping the old job deletes the file this page made and changes nothing else`() = runTest(dispatcher) {
        val queue = FakeQueue(waiting)
        val files = FakeFiles()
        val vm = sliced(queue, files)
        vm.dismissReplacement()
        advanceUntilIdle()
        assertNull(vm.state.value.replacement)
        assertTrue(files.calls.contains("discard:9"))
        assertTrue(queue.calls.isEmpty())
    }

    @Test
    fun `a job that is gone from the queue says so`() = runTest(dispatcher) {
        val queue = FakeQueue(waiting.copy(id = 99))
        val vm = vm(queue, FakeFiles())
        vm.open(waiting)
        advanceUntilIdle()
        assertEquals("This job is no longer in the queue.", vm.state.value.error)
    }
}
