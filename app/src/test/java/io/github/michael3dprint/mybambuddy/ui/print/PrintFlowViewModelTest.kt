package io.github.michael3dprint.mybambuddy.ui.print

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.ArchiveRef
import io.github.michael3dprint.mybambuddy.domain.model.BedType
import io.github.michael3dprint.mybambuddy.domain.model.FileOrigin
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldAccess
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldInstance
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldModel
import io.github.michael3dprint.mybambuddy.domain.model.PlateInfo
import io.github.michael3dprint.mybambuddy.domain.model.PrintFile
import io.github.michael3dprint.mybambuddy.domain.model.PrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.model.ProjectSlot
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.model.QueueStatus
import io.github.michael3dprint.mybambuddy.domain.model.RequiredFilament
import io.github.michael3dprint.mybambuddy.domain.model.SliceJobState
import io.github.michael3dprint.mybambuddy.domain.model.SliceOptions
import io.github.michael3dprint.mybambuddy.domain.model.SliceRequest
import io.github.michael3dprint.mybambuddy.domain.model.SlicerPreset
import io.github.michael3dprint.mybambuddy.domain.model.SlicerPresets
import io.github.michael3dprint.mybambuddy.domain.model.StagedPrint
import io.github.michael3dprint.mybambuddy.domain.print.PrintSource
import io.github.michael3dprint.mybambuddy.domain.repo.PrintControlRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrintFileRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterToolGate
import io.github.michael3dprint.mybambuddy.domain.slicing.NoSliceMemory
import io.github.michael3dprint.mybambuddy.domain.slicing.SliceMemory
import io.github.michael3dprint.mybambuddy.domain.slicing.SliceMemoryStore
import io.github.michael3dprint.mybambuddy.testutil.AllowAllToolGate
import io.github.michael3dprint.mybambuddy.testutil.InMemorySliceMemory
import io.github.michael3dprint.mybambuddy.testutil.ScriptedQueue
import io.github.michael3dprint.mybambuddy.ui.filament.FakeAms
import io.github.michael3dprint.mybambuddy.ui.filament.testAmsUnit
import java.io.ByteArrayInputStream
import java.io.InputStream
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
class PrintFlowViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class Source(override val name: String = "a.gcode.3mf") : PrintSource {
        override val size: Long = 4
        override fun open(): InputStream = ByteArrayInputStream(ByteArray(4))
    }

    private val goodFile = PrintFile(
        7,
        "a.gcode.3mf",
        "Bambu Lab X2D 0.4 nozzle",
        listOf(PlateInfo(1, "Plate", 8040, 31.0, listOf(RequiredFilament(1, "PLA", "307FE2", 31.0)))),
        createdHere = true,
    )

    private inner class FakeFiles : PrintFileRepository {
        var uploaded: ApiResult<PrintFile> = ApiResult.Success(goodFile)
        var access: ApiResult<MakerWorldAccess> = ApiResult.Success(MakerWorldAccess.READY)
        var startResult: ApiResult<Unit> = ApiResult.Success(Unit)
        val calls = mutableListOf<String>()
        val previews = mutableListOf<String>()
        val covers = mutableListOf<String>()
        val staged = mutableListOf<StagedPrint>()

        override suspend fun upload(source: PrintSource, onProgress: (Float) -> Unit): ApiResult<PrintFile> {
            calls += "upload"
            onProgress(0.5f)
            return uploaded
        }

        override suspend fun makerWorldAccess() = access

        override suspend fun makerWorldImage(url: String): ApiResult<ByteArray> {
            covers += url
            return ApiResult.Success(byteArrayOf(5, 5))
        }

        override suspend fun resolveMakerWorld(link: String): ApiResult<MakerWorldModel> {
            calls += "resolve"
            return ApiResult.Success(
                MakerWorldModel(
                    5,
                    "Benchy",
                    listOf(MakerWorldInstance(9, "0.2mm", "https://cdn/p9.png")),
                    emptyList(),
                    "https://cdn/cover.png",
                ),
            )
        }

        override suspend fun importMakerWorld(model: MakerWorldModel, profileId: Int?): ApiResult<PrintFile> {
            calls += "import:$profileId"
            return ApiResult.Success(goodFile)
        }

        override suspend fun stage(print: StagedPrint): ApiResult<Int> {
            calls += "stage"
            staged += print
            return ApiResult.Success(42)
        }

        override suspend fun start(itemId: Int, printerId: Int, skipFilamentCheck: Boolean): ApiResult<Unit> {
            calls += "start:$itemId:$skipFilamentCheck"
            return startResult
        }

        override suspend fun discard(libraryId: Int): ApiResult<Unit> {
            calls += "discard:$libraryId"
            return ApiResult.Success(Unit)
        }

        var sliceStates: MutableList<SliceJobState> = mutableListOf(SliceJobState.Done(8, "a.gcode.3mf"))
        var slicedFile: PrintFile = goodFile.copy(libraryId = 8)
        var slicePresets: SlicerPresets = x2dPresets

        override suspend fun open(libraryId: Int, name: String, createdHere: Boolean, alsoCreated: List<Int>): ApiResult<PrintFile> {
            calls += "open:$libraryId"
            return ApiResult.Success(slicedFile.copy(alsoCreated = alsoCreated))
        }

        override suspend fun slicerPresets(): ApiResult<SlicerPresets> = ApiResult.Success(slicePresets)

        override suspend fun preview(libraryId: Int, plate: Int, hasPlatePicture: Boolean): ApiResult<ByteArray> {
            previews += "preview:$libraryId:$plate:$hasPlatePicture"
            return ApiResult.Success(byteArrayOf(1, 2, 3))
        }

        var archive: PrintFile = goodFile.copy(libraryId = 3, createdHere = false, origin = FileOrigin.ARCHIVE)

        override suspend fun openArchive(archiveId: Int, name: String): ApiResult<PrintFile> {
            calls += "archive:$archiveId"
            return ApiResult.Success(archive)
        }

        override suspend fun archivePreview(archiveId: Int, plate: Int, hasPlatePicture: Boolean): ApiResult<ByteArray> {
            previews += "archive-preview:$archiveId:$plate:$hasPlatePicture"
            return ApiResult.Success(byteArrayOf(7))
        }

        override suspend fun projectSlots(libraryId: Int, plate: Int): ApiResult<List<ProjectSlot>> =
            ApiResult.Success(listOf(ProjectSlot(1, "PLA", "FF0000", true)))

        var scaleResult: ApiResult<PrintFile> = ApiResult.Success(goodFile.copy(libraryId = 11, createdHere = true))

        override suspend fun scaledCopy(source: PrintFile, percent: Int): ApiResult<PrintFile> {
            calls += "scale:${source.libraryId}:$percent"
            return scaleResult
        }

        override suspend fun startSlice(libraryId: Int, request: SliceRequest): ApiResult<Int> {
            calls += "slice:$libraryId:${request.filaments.size}:${request.colours.single()}"
            return ApiResult.Success(3)
        }

        override suspend fun sliceJob(jobId: Int): ApiResult<SliceJobState> {
            calls += "job:$jobId"
            return ApiResult.Success(if (sliceStates.size > 1) sliceStates.removeAt(0) else sliceStates.first())
        }
    }

    private class FakePrinters(private val state: PrinterState = PrinterState.IDLE, var awaiting: Boolean = false) : PrinterRepository {
        override fun getPrintersFlow(pollIntervalMs: Long): Flow<ApiResult<List<Printer>>> = emptyFlow()

        override suspend fun fetchPrinters(): ApiResult<List<Printer>> =
            ApiResult.Success(listOf(Printer("1", "X2D", "X2D", state, awaitingPlateClear = awaiting)))

        override suspend fun fetchPrinter(printerId: String): ApiResult<Printer> =
            ApiResult.Error(message = "unused", errorType = ApiErrorType.UNKNOWN)

        override suspend fun fetchPrinterStatus(printerId: String): ApiResult<PrintStatus> =
            ApiResult.Error(message = "unused", errorType = ApiErrorType.UNKNOWN)
    }

    private val x2dPresets = SlicerPresets(
        printers = listOf(SlicerPreset("cloud", "GM045", "Bambu Lab X2D 0.4 nozzle", null, emptyList())),
        processes = listOf(
            SlicerPreset("cloud", "GP1", "0.20mm Standard @BBL X2D", null, listOf("Bambu Lab X2D 0.4 nozzle")),
            SlicerPreset("cloud", "GP2", "0.16mm Standard @BBL X2D", null, listOf("Bambu Lab X2D 0.4 nozzle")),
        ),
        filaments = listOf(
            SlicerPreset("standard", "Bambu PLA Matte @BBL X2D", "Bambu PLA Matte @BBL X2D", "PLA", emptyList()),
            SlicerPreset("standard", "Bambu PLA Basic @BBL X2D", "Bambu PLA Basic @BBL X2D", "PLA", emptyList()),
        ),
    )

    private val ams = FakeAms().apply { units = ApiResult.Success(listOf(testAmsUnit)) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val printingJob = QueueItem(42, 1, "X2D", "a", QueueStatus.PRINTING, 1, null, false)
    private var memory: SliceMemoryStore = NoSliceMemory
    private val clearedPlates = mutableListOf<String>()
    private var control: PrintControlRepository = object : PrintControlRepository by NoControl {
        override suspend fun clearPlate(printerId: String): ApiResult<Unit> {
            clearedPlates += printerId
            return ApiResult.Success(Unit)
        }
    }
    private var queue = ScriptedQueue(listOf(listOf(printingJob)))

    private fun vm(files: PrintFileRepository, gate: PrinterToolGate = AllowAllToolGate, printers: PrinterRepository = FakePrinters()) =
        PrintFlowViewModel(files, printers, ams, gate, queue, control, memory)

    private fun deny(text: String) = object : PrinterToolGate {
        override suspend fun refusal(printerId: String, tool: PrinterTool, printerName: String) = text
    }

    @Test
    fun `a picked file is uploaded and reviewed with the slot proposed`() = runTest(dispatcher) {
        val files = FakeFiles()
        val vm = vm(files)
        vm.begin("1")
        advanceUntilIdle()
        vm.onFilePicked(Source())
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(PrintStep.REVIEW, s.step)
        assertEquals(1, s.plate?.index)
        assertEquals(2, s.chosen[1]?.tray)
        assertTrue(s.findings.none { it.level == io.github.michael3dprint.mybambuddy.domain.print.FindingLevel.BLOCKER })
        assertTrue(s.canAdd && s.canStart)
        assertEquals(listOf("upload"), files.calls)
    }

    @Test
    fun `a refused upload returns to the source step with the reason`() = runTest(dispatcher) {
        val files = FakeFiles().apply {
            uploaded =
                ApiResult.Error(code = 400, message = "bad", errorType = ApiErrorType.REJECTED, serverDetail = "Not a valid 3MF file")
        }
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source())
        advanceUntilIdle()
        assertEquals(PrintStep.SOURCE, vm.state.value.step)
        assertEquals("Not a valid 3MF file", vm.state.value.message)
        assertNull(vm.state.value.file)
    }

    @Test
    fun `adding to the queue stages a waiting item and never starts it`() = runTest(dispatcher) {
        val files = FakeFiles()
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source())
        advanceUntilIdle()
        vm.addToQueue()
        advanceUntilIdle()
        assertEquals(PrintStep.DONE, vm.state.value.step)
        assertEquals(listOf("upload", "stage"), files.calls)
        assertEquals(StagedPrint(7, 1, 1, listOf(2), cleanup = true), files.staged.single())
    }

    @Test
    fun `start stages then starts once`() = runTest(dispatcher) {
        val files = FakeFiles()
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source())
        advanceUntilIdle()
        vm.start(skipFilamentCheck = false)
        advanceUntilIdle()
        assertEquals(listOf("upload", "stage", "start:42:false"), files.calls)
        assertEquals(PrintStep.DONE, vm.state.value.step)
        assertEquals("Printing has started on X2D.", vm.state.value.message)
        assertFalse(vm.state.value.problem)
    }

    @Test
    fun `a filament shortfall asks first and only prints anyway after a second explicit start`() = runTest(dispatcher) {
        val files = FakeFiles().apply {
            startResult =
                ApiResult.Error(code = 409, message = "x", errorType = ApiErrorType.REJECTED, serverDetail = "Slot 1 is short by 12 g")
        }
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source())
        advanceUntilIdle()
        vm.start(skipFilamentCheck = false)
        advanceUntilIdle()
        assertEquals("Slot 1 is short by 12 g", vm.state.value.shortfall)
        assertEquals(PrintStep.REVIEW, vm.state.value.step)
        files.startResult = ApiResult.Success(Unit)
        vm.start(skipFilamentCheck = true)
        advanceUntilIdle()
        // The same staged item is reused: staged once, started twice (the second time past the check).
        assertEquals(listOf("upload", "stage", "start:42:false", "start:42:true"), files.calls)
        assertNull(vm.state.value.shortfall)
    }

    @Test
    fun `a printer that cannot start blocks Start but not Add to queue`() = runTest(dispatcher) {
        val files = FakeFiles()
        val vm = vm(files, gate = deny("Developer LAN mode is off."))
        vm.begin("1")
        vm.onFilePicked(Source())
        advanceUntilIdle()
        assertTrue(vm.state.value.canAdd)
        assertFalse(vm.state.value.canStart)
        vm.start(skipFilamentCheck = false)
        advanceUntilIdle()
        assertTrue(files.calls.none { it.startsWith("start") || it == "stage" })
    }

    @Test
    fun `an unsliced file blocks everything`() = runTest(dispatcher) {
        val files = FakeFiles().apply {
            uploaded = ApiResult.Success(goodFile.copy(plates = listOf(PlateInfo(1, null, null, null, emptyList()))))
        }
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source())
        advanceUntilIdle()
        assertFalse(vm.state.value.canAdd)
        vm.addToQueue()
        advanceUntilIdle()
        assertTrue(files.staged.isEmpty())
    }

    @Test
    fun `backing out deletes the file this flow created, but not once it is queued`() = runTest(dispatcher) {
        val files = FakeFiles()
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source())
        advanceUntilIdle()
        vm.cancel()
        advanceUntilIdle()
        assertTrue(vm.state.value.closed)
        assertEquals("discard:7", files.calls.last())

        val queued = FakeFiles()
        val vm2 = vm(queued)
        vm2.begin("1")
        vm2.onFilePicked(Source())
        advanceUntilIdle()
        vm2.addToQueue()
        advanceUntilIdle()
        vm2.cancel()
        advanceUntilIdle()
        assertTrue(queued.calls.none { it.startsWith("discard") })
    }

    @Test
    fun `makerworld stops with a message when the server is not signed in`() = runTest(dispatcher) {
        val files = FakeFiles().apply { access = ApiResult.Success(MakerWorldAccess.NOT_SIGNED_IN) }
        val vm = vm(files)
        vm.begin("1")
        vm.lookUpLink("https://makerworld.com/en/models/5")
        advanceUntilIdle()
        assertEquals(MakerWorldAccess.NOT_SIGNED_IN, vm.state.value.access)
        assertEquals(PrintStep.LINK, vm.state.value.step)
        assertTrue(files.calls.isEmpty())
    }

    @Test
    fun `makerworld lookup then import goes to the review`() = runTest(dispatcher) {
        val files = FakeFiles()
        val vm = vm(files)
        vm.begin("1")
        vm.lookUpLink("https://makerworld.com/en/models/5")
        advanceUntilIdle()
        assertNotNull(vm.state.value.model)
        vm.importInstance(9)
        advanceUntilIdle()
        assertEquals(PrintStep.REVIEW, vm.state.value.step)
        assertEquals(listOf("resolve", "import:9"), files.calls)
    }

    private val unsliced get() = goodFile.copy(plates = listOf(PlateInfo(1, null, null, null, emptyList())))

    @Test
    fun `slicing an unsliced file sends one request, polls, and puts the result into the review`() = runTest(dispatcher) {
        val files = FakeFiles().apply {
            uploaded = ApiResult.Success(unsliced)
            sliceStates = mutableListOf(SliceJobState.Running(40), SliceJobState.Done(8, "a.gcode.3mf"))
        }
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source("a.3mf"))
        advanceUntilIdle()
        assertFalse(vm.state.value.canAdd)
        vm.slice.open(vm.state.value.file!!, "X2D", vm.state.value.slots)
        advanceUntilIdle()
        assertFalse(vm.slice.state.value.canSlice)
        vm.slice.choose(SliceChoice.Bed(BedType.TEXTURED_PEI))
        assertTrue(vm.slice.state.value.canSlice)
        vm.slice.start()
        advanceUntilIdle()
        assertEquals(1, files.calls.count { it.startsWith("slice:") })
        assertTrue(files.calls.contains("slice:7:1:#307FE2"))
        assertEquals(2, files.calls.count { it.startsWith("job:") })
        assertEquals(PrintStep.REVIEW, vm.state.value.step)
        assertEquals(8, vm.state.value.file?.libraryId)
        assertEquals(listOf(7), vm.state.value.file?.alsoCreated)
        assertTrue(vm.state.value.canAdd)
        assertEquals(SlicePhase.CLOSED, vm.slice.state.value.phase)
    }

    @Test
    fun `a failed slice shows the server's reason and stays ready to try again`() = runTest(dispatcher) {
        val files = FakeFiles().apply {
            uploaded = ApiResult.Success(unsliced)
            sliceStates = mutableListOf(SliceJobState.Failed("Slicer unreachable"))
        }
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source("a.3mf"))
        advanceUntilIdle()
        vm.slice.open(vm.state.value.file!!, "X2D", vm.state.value.slots)
        advanceUntilIdle()
        vm.slice.choose(SliceChoice.Bed(BedType.TEXTURED_PEI))
        vm.slice.start()
        advanceUntilIdle()
        assertEquals(SlicePhase.READY, vm.slice.state.value.phase)
        assertEquals("Slicer unreachable", vm.slice.state.value.message)
        assertEquals(PrintStep.REVIEW, vm.state.value.step)
        assertEquals(7, vm.state.value.file?.libraryId)
    }

    @Test
    fun `no printer preset for the model says so and nothing can be sliced`() = runTest(dispatcher) {
        val files = FakeFiles().apply {
            uploaded = ApiResult.Success(unsliced)
            slicePresets = x2dPresets.copy(printers = emptyList())
        }
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source("a.3mf"))
        advanceUntilIdle()
        vm.slice.open(vm.state.value.file!!, "X2D", vm.state.value.slots)
        advanceUntilIdle()
        assertFalse(vm.slice.state.value.canSlice)
        assertEquals(SlicePhase.FAILED, vm.slice.state.value.phase)
        assertTrue(vm.slice.state.value.message!!.contains("no printer preset"))
        vm.slice.choose(SliceChoice.Bed(BedType.TEXTURED_PEI))
        vm.slice.start()
        advanceUntilIdle()
        assertTrue(files.calls.none { it.startsWith("slice:") })
    }

    @Test
    fun `backing out of a sliced file deletes it and the file it was sliced from`() = runTest(dispatcher) {
        val files = FakeFiles().apply { uploaded = ApiResult.Success(unsliced) }
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source("a.3mf"))
        advanceUntilIdle()
        vm.slice.open(vm.state.value.file!!, "X2D", vm.state.value.slots)
        advanceUntilIdle()
        vm.slice.choose(SliceChoice.Bed(BedType.TEXTURED_PEI))
        vm.slice.start()
        advanceUntilIdle()
        vm.cancel()
        advanceUntilIdle()
        assertEquals(listOf("discard:8", "discard:7"), files.calls.filter { it.startsWith("discard") })
    }

    @Test
    fun `when the file's filaments cannot be read the dialog shows only the reason`() = runTest(dispatcher) {
        val base = FakeFiles().apply { uploaded = ApiResult.Success(unsliced) }
        val files = object : PrintFileRepository by base {
            override suspend fun projectSlots(libraryId: Int, plate: Int): ApiResult<List<ProjectSlot>> =
                ApiResult.Error(message = "timeout", errorType = ApiErrorType.NETWORK_ERROR)
        }
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source("a.3mf"))
        advanceUntilIdle()
        vm.slice.open(vm.state.value.file!!, "X2D", vm.state.value.slots)
        advanceUntilIdle()
        assertEquals(SlicePhase.FAILED, vm.slice.state.value.phase)
        assertTrue(vm.slice.state.value.message!!.contains("Unable to connect"))
        assertFalse(vm.slice.state.value.canSlice)
    }

    @Test
    fun `the slice defaults to the loaded AMS slot, its product line preset and its colour`() = runTest(dispatcher) {
        val files = FakeFiles().apply { uploaded = ApiResult.Success(unsliced) }
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source("a.3mf"))
        advanceUntilIdle()
        vm.slice.open(vm.state.value.file!!, "X2D", vm.state.value.slots)
        advanceUntilIdle()
        val s = vm.slice.state.value
        assertEquals(2, s.ams[1]?.tray)
        assertEquals("Bambu PLA Basic @BBL X2D", s.chosen[1]?.name)
        assertEquals(s.chosen[1], s.recommended(s.slots.single()))
        vm.slice.choose(SliceChoice.Bed(BedType.TEXTURED_PEI))
        vm.slice.start()
        advanceUntilIdle()
        // The file asks for red (FF0000) but the loaded slot is blue, so the slot's colour is what is sent.
        assertTrue(files.calls.contains("slice:7:1:#307FE2"))
    }

    @Test
    fun `with no loaded slot of the type the file's own colour is sent`() = runTest(dispatcher) {
        val files = FakeFiles().apply { uploaded = ApiResult.Success(unsliced) }
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source("a.3mf"))
        advanceUntilIdle()
        vm.slice.open(vm.state.value.file!!, "X2D", emptyList())
        advanceUntilIdle()
        assertNull(vm.slice.state.value.ams[1])
        vm.slice.choose(SliceChoice.Bed(BedType.TEXTURED_PEI))
        vm.slice.start()
        advanceUntilIdle()
        assertTrue(files.calls.contains("slice:7:1:#FF0000"))
    }

    @Test
    fun `a slice cannot start until the build plate is chosen, and the chosen plate is kept`() = runTest(dispatcher) {
        val files = FakeFiles().apply { uploaded = ApiResult.Success(unsliced) }
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source("a.3mf"))
        advanceUntilIdle()
        vm.slice.open(vm.state.value.file!!, "X2D", vm.state.value.slots)
        advanceUntilIdle()
        assertNull(vm.slice.state.value.bed)
        assertFalse(vm.slice.state.value.canSlice)
        vm.slice.start()
        advanceUntilIdle()
        assertTrue(files.calls.none { it.startsWith("slice:") })
        vm.slice.choose(SliceChoice.Bed(BedType.ENGINEERING))
        assertTrue(vm.slice.state.value.canSlice)
        assertEquals(BedType.ENGINEERING, vm.slice.state.value.bed)
    }

    @Test
    fun `the review loads the plate preview`() = runTest(dispatcher) {
        val files = FakeFiles()
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source())
        advanceUntilIdle()
        assertTrue(files.previews.contains("preview:7:1:false"))
        assertEquals(3, vm.state.value.preview?.size)
    }

    @Test
    fun `makerworld pictures are fetched after the lookup and before anything is imported`() = runTest(dispatcher) {
        val files = FakeFiles()
        val vm = vm(files)
        vm.begin("1")
        vm.lookUpLink("https://makerworld.com/en/models/5")
        advanceUntilIdle()
        assertEquals(listOf("https://cdn/cover.png", "https://cdn/p9.png"), files.covers)
        assertEquals(2, vm.state.value.images.size)
        assertTrue(files.calls.none { it.startsWith("import") })
    }

    private suspend fun kotlinx.coroutines.test.TestScope.openSlice(
        files: FakeFiles,
        fileModel: String? = "Bambu Lab X2D 0.4 nozzle",
    ): PrintFlowViewModel {
        files.uploaded = ApiResult.Success(unsliced.copy(slicedForModel = fileModel))
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source("a.3mf"))
        advanceUntilIdle()
        vm.slice.open(vm.state.value.file!!, "X2D", vm.state.value.slots)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `the page says in words why Slice is off`() = runTest(dispatcher) {
        val vm = openSlice(FakeFiles())
        assertEquals("Choose which build plate is on the printer (Plate tab).", vm.slice.state.value.blocker)
        vm.slice.choose(SliceChoice.Bed(BedType.COOL))
        assertNull(vm.slice.state.value.blocker)
        assertTrue(vm.slice.state.value.canSlice)
    }

    @Test
    fun `the additional options are off by default and are sent when switched on`() = runTest(dispatcher) {
        val files = FakeFiles()
        val vm = openSlice(files)
        assertEquals(SliceOptions(), vm.slice.state.value.options)
        vm.slice.choose(SliceChoice.Bed(BedType.COOL))
        vm.slice.choose(SliceChoice.Option(orient = true))
        vm.slice.choose(SliceChoice.Option(arrange = true))
        vm.slice.choose(SliceChoice.Option(embedded = true))
        assertEquals(SliceOptions(autoOrient = true, autoArrange = true, useEmbeddedSettings = true), vm.slice.state.value.request?.options)
        assertEquals(3, vm.slice.state.value.options.changed)
    }

    @Test
    fun `the designer's settings are only used when the file targets this printer`() = runTest(dispatcher) {
        val other = openSlice(FakeFiles(), fileModel = "Bambu Lab P1S 0.4 nozzle")
        other.slice.choose(SliceChoice.Bed(BedType.COOL))
        other.slice.choose(SliceChoice.Option(embedded = true))
        assertFalse(other.slice.state.value.embeddedAvailable)
        assertFalse(other.slice.state.value.request!!.options.useEmbeddedSettings)
        val unknown = openSlice(FakeFiles(), fileModel = null)
        assertFalse(unknown.slice.state.value.embeddedAvailable)
        val same = openSlice(FakeFiles())
        assertTrue(same.slice.state.value.embeddedAvailable)
    }

    @Test
    fun `a tab that still needs something says so in words`() = runTest(dispatcher) {
        val vm = openSlice(FakeFiles())
        val s = vm.slice.state.value
        assertEquals("Plate (needed)", SliceTabs.label(SliceTab.PLATE, s))
        assertEquals("Filament", SliceTabs.label(SliceTab.FILAMENT, s))
        assertEquals("Process", SliceTabs.label(SliceTab.PROCESS, s))
        assertEquals("Options", SliceTabs.label(SliceTab.OPTIONS, s))
        vm.slice.choose(SliceChoice.Bed(BedType.COOL))
        vm.slice.choose(SliceChoice.Option(orient = true))
        val after = vm.slice.state.value
        assertEquals("Plate", SliceTabs.label(SliceTab.PLATE, after))
        assertEquals("Options (1 changed)", SliceTabs.label(SliceTab.OPTIONS, after))
        assertEquals("Filament (needed)", SliceTabs.label(SliceTab.FILAMENT, after.copy(chosen = emptyMap())))
        assertEquals("Process (needed)", SliceTabs.label(SliceTab.PROCESS, after.copy(process = null)))
    }

    @Test
    fun `when the printer refuses the file the flow says it will not print and gives the reason`() = runTest(dispatcher) {
        val reason = "X2D refused to store the file — check its SD card or USB drive. Bambuddy will retry automatically"
        queue = ScriptedQueue(listOf(listOf(printingJob.copy(status = QueueStatus.PENDING, waitingReason = reason))))
        val files = FakeFiles()
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source())
        advanceUntilIdle()
        vm.start(skipFilamentCheck = false)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(PrintStep.DONE, s.step)
        assertTrue(s.problem)
        assertFalse(s.watching)
        assertTrue(s.message!!.startsWith("It won't print yet."))
        assertTrue(s.message!!.contains("SD card"))
    }

    @Test
    fun `a plate waiting to be cleared blocks Start and the review can mark it cleared`() = runTest(dispatcher) {
        val printers = FakePrinters(awaiting = true)
        control = object : PrintControlRepository by NoControl {
            override suspend fun clearPlate(printerId: String): ApiResult<Unit> {
                clearedPlates += printerId
                printers.awaiting = false
                return ApiResult.Success(Unit)
            }
        }
        val vm = vm(FakeFiles(), printers = printers)
        vm.begin("1")
        vm.onFilePicked(Source())
        advanceUntilIdle()
        assertTrue(vm.state.value.canAdd)
        assertFalse(vm.state.value.canStart)
        vm.select(PrintSelection.PlateCleared)
        advanceUntilIdle()
        assertEquals(listOf("1"), clearedPlates)
        assertTrue(vm.state.value.canStart)
    }

    @Test
    fun `an accepted slice is remembered and the next one opens with it`() = runTest(dispatcher) {
        val store = InMemorySliceMemory()
        memory = store
        val files = FakeFiles()
        val first = openSlice(files)
        first.slice.choose(SliceChoice.Bed(BedType.ENGINEERING))
        first.slice.choose(SliceChoice.Process(files.slicePresets.processes[1]))
        first.slice.choose(SliceChoice.Option(arrange = true, embedded = true))
        first.slice.start()
        advanceUntilIdle()
        assertEquals(1, store.saves)
        assertEquals("Engineering Plate", store.memory.bed)
        assertEquals("GP2", store.memory.processByModel["x2d"])
        assertTrue(store.memory.autoArrange && store.memory.useEmbeddedSettings)

        val second = openSlice(FakeFiles())
        val s = second.slice.state.value
        assertEquals(BedType.ENGINEERING, s.bed)
        assertTrue(s.bedFromMemory)
        assertEquals("GP2", s.process?.id)
        assertTrue(s.options.autoArrange && s.options.useEmbeddedSettings)
        assertTrue(s.canSlice)
        second.slice.choose(SliceChoice.Bed(BedType.COOL))
        assertFalse(second.slice.state.value.bedFromMemory)
    }

    @Test
    fun `a slice that bambuddy refuses is not remembered`() = runTest(dispatcher) {
        val store = InMemorySliceMemory()
        memory = store
        val base = FakeFiles().apply { uploaded = ApiResult.Success(unsliced) }
        val files = object : PrintFileRepository by base {
            override suspend fun startSlice(libraryId: Int, request: SliceRequest): ApiResult<Int> =
                ApiResult.Error(code = 400, message = "x", errorType = ApiErrorType.REJECTED, serverDetail = "no slicer")
        }
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source("a.3mf"))
        advanceUntilIdle()
        vm.slice.open(vm.state.value.file!!, "X2D", vm.state.value.slots)
        advanceUntilIdle()
        vm.slice.choose(SliceChoice.Bed(BedType.COOL))
        vm.slice.start()
        advanceUntilIdle()
        assertEquals(0, store.saves)
    }

    @Test
    fun `a remembered preset that no longer exists is ignored`() = runTest(dispatcher) {
        memory = InMemorySliceMemory(SliceMemory(bed = "Cool Plate", processByModel = mapOf("x2d" to "GONE")))
        val s = openSlice(FakeFiles()).slice.state.value
        assertEquals("GP1", s.process?.id)
        assertEquals(BedType.COOL, s.bed)
    }

    @Test
    fun `the loaded spool decides the filament preset and the remembered one is used without a loaded match`() = runTest(dispatcher) {
        memory = InMemorySliceMemory(SliceMemory(filamentByType = mapOf("PLA" to "Bambu PLA Matte @BBL X2D")))
        val withSpool = openSlice(FakeFiles())
        assertEquals("Bambu PLA Basic @BBL X2D", withSpool.slice.state.value.chosen[1]?.name)

        val files = FakeFiles().apply { uploaded = ApiResult.Success(unsliced) }
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source("a.3mf"))
        advanceUntilIdle()
        vm.slice.open(vm.state.value.file!!, "X2D", emptyList())
        advanceUntilIdle()
        assertEquals("Bambu PLA Matte @BBL X2D", vm.slice.state.value.chosen[1]?.name)
    }

    @Test
    fun `running a previous print again opens it for review from its archive, never uploading`() = runTest(dispatcher) {
        val files = FakeFiles()
        val vm = vm(files)
        vm.begin("1", ArchiveRef(3, "Ornament"))
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(PrintStep.REVIEW, s.step)
        assertEquals(FileOrigin.ARCHIVE, s.file?.origin)
        assertEquals(listOf("archive:3"), files.calls)
        assertTrue(files.previews.contains("archive-preview:3:1:false"))
        assertTrue(s.canAdd && s.canStart)
    }

    @Test
    fun `an archive print is staged by its archive id and backing out deletes nothing`() = runTest(dispatcher) {
        val files = FakeFiles()
        val vm = vm(files)
        vm.begin("1", ArchiveRef(3, "Ornament"))
        advanceUntilIdle()
        vm.addToQueue()
        advanceUntilIdle()
        val staged = files.staged.single()
        assertEquals(3, staged.libraryFileId)
        assertTrue(staged.fromArchive)
        assertFalse(staged.cleanup)

        val other = FakeFiles()
        val vm2 = vm(other)
        vm2.begin("1", ArchiveRef(3, "Ornament"))
        advanceUntilIdle()
        vm2.cancel()
        advanceUntilIdle()
        assertTrue(other.calls.none { it.startsWith("discard") })
    }

    @Test
    fun `an archive that cannot be read says why and stays on the first screen`() = runTest(dispatcher) {
        val base = FakeFiles()
        val files = object : PrintFileRepository by base {
            override suspend fun openArchive(archiveId: Int, name: String): ApiResult<PrintFile> =
                ApiResult.Error(code = 404, message = "x", errorType = ApiErrorType.NOT_FOUND, serverDetail = "Archive not found")
        }
        val vm = vm(files)
        vm.begin("1", ArchiveRef(99, "Gone"))
        advanceUntilIdle()
        assertEquals(PrintStep.SOURCE, vm.state.value.step)
        assertTrue(vm.state.value.message!!.isNotBlank())
    }

    @Test
    fun `a scaled slice makes the copy first, slices the copy, then deletes the copy`() = runTest(dispatcher) {
        val files = FakeFiles()
        val vm = openSlice(files)
        vm.slice.choose(SliceChoice.Bed(BedType.COOL))
        vm.slice.choose(SliceChoice.Scale(150))
        vm.slice.start()
        advanceUntilIdle()
        val order = files.calls.filter { it.startsWith("scale") || it.startsWith("slice") || it.startsWith("discard") }
        assertEquals(listOf("scale:7:150", "slice:11:1:#307FE2", "discard:11"), order)
        assertEquals(PrintStep.REVIEW, vm.state.value.step)
        // The sliced file's cleanup list holds the source the flow made (7) but not the copy, which is already gone.
        assertEquals(listOf(7), vm.state.value.file?.alsoCreated)
    }

    @Test
    fun `at one hundred percent no copy is made and the file itself is sliced`() = runTest(dispatcher) {
        val files = FakeFiles()
        val vm = openSlice(files)
        vm.slice.choose(SliceChoice.Bed(BedType.COOL))
        vm.slice.start()
        advanceUntilIdle()
        assertTrue(files.calls.none { it.startsWith("scale") })
        assertTrue(files.calls.contains("slice:7:1:#307FE2"))
    }

    @Test
    fun `a file that cannot be scaled shows why, slices nothing and stays ready`() = runTest(dispatcher) {
        val files = FakeFiles().apply {
            scaleResult =
                ApiResult.Error(
                    message = "x",
                    errorType = ApiErrorType.UNSUPPORTED,
                    serverDetail = "The model's geometry could not be found.",
                )
        }
        val vm = openSlice(files)
        vm.slice.choose(SliceChoice.Bed(BedType.COOL))
        vm.slice.choose(SliceChoice.Scale(200))
        vm.slice.start()
        advanceUntilIdle()
        assertEquals(SlicePhase.READY, vm.slice.state.value.phase)
        assertEquals("The model's geometry could not be found.", vm.slice.state.value.message)
        assertTrue(files.calls.none { it.startsWith("slice") })
    }

    @Test
    fun `a scale that is not a number blocks the slice and says which tab`() = runTest(dispatcher) {
        val vm = openSlice(FakeFiles())
        vm.slice.choose(SliceChoice.Bed(BedType.COOL))
        vm.slice.choose(SliceChoice.Scale(null))
        val s = vm.slice.state.value
        assertFalse(s.canSlice)
        assertTrue(s.blocker!!.contains("Options tab"))
        assertEquals("Options (needed)", SliceTabs.label(SliceTab.OPTIONS, s))
        vm.slice.choose(SliceChoice.Scale(150))
        assertEquals("Options (scale 150 %)", SliceTabs.label(SliceTab.OPTIONS, vm.slice.state.value))
        assertTrue(vm.slice.state.value.canSlice)
    }

    @Test
    fun `the scale is not remembered for the next slice`() = runTest(dispatcher) {
        val store = InMemorySliceMemory()
        memory = store
        val first = openSlice(FakeFiles())
        first.slice.choose(SliceChoice.Bed(BedType.COOL))
        first.slice.choose(SliceChoice.Scale(150))
        first.slice.start()
        advanceUntilIdle()
        assertEquals(100, openSlice(FakeFiles()).slice.state.value.scalePercent)
    }

    @Test
    fun `when the slice is refused after the copy was made the copy is deleted`() = runTest(dispatcher) {
        val base = FakeFiles().apply { uploaded = ApiResult.Success(unsliced) }
        val files = object : PrintFileRepository by base {
            override suspend fun startSlice(libraryId: Int, request: SliceRequest): ApiResult<Int> =
                ApiResult.Error(code = 400, message = "x", errorType = ApiErrorType.REJECTED, serverDetail = "no slicer")
        }
        val vm = vm(files)
        vm.begin("1")
        vm.onFilePicked(Source("a.3mf"))
        advanceUntilIdle()
        vm.slice.open(vm.state.value.file!!, "X2D", vm.state.value.slots)
        advanceUntilIdle()
        vm.slice.choose(SliceChoice.Bed(BedType.COOL))
        vm.slice.choose(SliceChoice.Scale(120))
        vm.slice.start()
        advanceUntilIdle()
        assertTrue(base.calls.contains("scale:7:120"))
        assertTrue(base.calls.contains("discard:11"))
    }
}

/** A control repository whose every command is refused, for tests that override only the one they use. */
private object NoControl : PrintControlRepository {
    private fun no(): ApiResult<Unit> = ApiResult.Error(message = "not used", errorType = ApiErrorType.UNSUPPORTED)

    override suspend fun pausePrint(printerId: String) = no()

    override suspend fun resumePrint(printerId: String) = no()

    override suspend fun cancelPrint(printerId: String) = no()

    override suspend fun refreshStatus(printerId: String) = no()

    override suspend fun clearHms(printerId: String) = no()

    override suspend fun clearPlate(printerId: String) = no()

    override suspend fun setPrintSpeed(printerId: String, mode: Int) = no()
}
