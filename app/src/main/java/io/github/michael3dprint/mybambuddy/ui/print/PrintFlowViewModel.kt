package io.github.michael3dprint.mybambuddy.ui.print

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.ArchiveRef
import io.github.michael3dprint.mybambuddy.domain.model.FileOrigin
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldAccess
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldModel
import io.github.michael3dprint.mybambuddy.domain.model.PrintFile
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.model.StagedPrint
import io.github.michael3dprint.mybambuddy.domain.policy.AmsTrayAddress
import io.github.michael3dprint.mybambuddy.domain.policy.StartCheck
import io.github.michael3dprint.mybambuddy.domain.policy.StartWatcher
import io.github.michael3dprint.mybambuddy.domain.print.AmsMapping
import io.github.michael3dprint.mybambuddy.domain.print.FilamentMatcher
import io.github.michael3dprint.mybambuddy.domain.print.PrintCheckInput
import io.github.michael3dprint.mybambuddy.domain.print.PrintSource
import io.github.michael3dprint.mybambuddy.domain.print.PrintValidation
import io.github.michael3dprint.mybambuddy.domain.print.SlotCandidates
import io.github.michael3dprint.mybambuddy.domain.repo.AmsRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrintControlRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrintFileRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterToolGate
import io.github.michael3dprint.mybambuddy.domain.repo.QueueRepository
import io.github.michael3dprint.mybambuddy.domain.slicing.NoSliceMemory
import io.github.michael3dprint.mybambuddy.domain.slicing.SliceMemoryStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The print-from-the-phone flow (design 35 and 38). Nothing starts by itself: a file is staged in the queue first, and starting is a
 * separate call the screen makes only after the user confirmed. Every server call is a single attempt.
 */
class PrintFlowViewModel(
    private val files: PrintFileRepository,
    private val printers: PrinterRepository,
    private val ams: AmsRepository,
    private val gate: PrinterToolGate,
    private val queue: QueueRepository,
    private val control: PrintControlRepository,
    memory: SliceMemoryStore = NoSliceMemory,
) : ViewModel() {
    private val _state = MutableStateFlow(PrintFlowState())
    val state: StateFlow<PrintFlowState> = _state.asStateFlow()
    private var job: Job? = null

    /** The slice dialog for a file that has no G-code (design 39); its result comes back through [select] as a sliced file. */
    val slice = SliceFlow(files, viewModelScope, memory) { select(PrintSelection.Sliced(it)) }
    private var units: List<io.github.michael3dprint.mybambuddy.domain.model.AmsUnit> = emptyList()
    private var startRefusal: String? = null

    /** Starts over at the source choice. [printerId] pre-selects a printer (the overview's Print button); null lets the review ask. */
    fun begin(printerId: String?, again: ArchiveRef? = null) {
        job?.cancel()
        _state.value = PrintFlowState(printerId = printerId)
        viewModelScope.launch {
            val list = (printers.fetchPrinters() as? ApiResult.Success)?.data.orEmpty()
            _state.value = _state.value.copy(printers = list, printerId = printerId ?: list.singleOrNull()?.id)
            if (again != null) openAgain(again)
        }
    }

    /** Reads a previous print's archive as the file under review (Run again); nothing is uploaded and nothing of the archive is ever deleted. */
    private fun openAgain(again: ArchiveRef) = work("Reading ${again.name}") {
        when (val result = files.openArchive(again.id, again.name)) {
            is ApiResult.Success -> review(result.data)
            is ApiResult.Error -> back(PrintStep.SOURCE, result.toUiErrorState(aboutPrinter = false).message)
        }
    }

    /** Uploads the picked file, then goes to the review. */
    fun onFilePicked(source: PrintSource) = work("Uploading ${source.name}", progress = 0f) {
        when (val result = files.upload(source) { p -> _state.value = _state.value.copy(progress = p) }) {
            is ApiResult.Success -> review(result.data)
            is ApiResult.Error -> back(PrintStep.SOURCE, result.toUiErrorState(aboutPrinter = false).message)
        }
    }

    /** Checks that Bambuddy can download from MakerWorld, then looks the link up. */
    fun lookUpLink(text: String) = work("Looking up the model") {
        when (val access = files.makerWorldAccess()) {
            is ApiResult.Error -> return@work back(PrintStep.LINK, access.toUiErrorState(aboutPrinter = false).message)
            is ApiResult.Success -> if (access.data != MakerWorldAccess.READY) {
                _state.value = _state.value.copy(step = PrintStep.LINK, access = access.data, message = null)
                return@work
            }
        }
        when (val result = files.resolveMakerWorld(text)) {
            is ApiResult.Success -> _state.value = _state.value.copy(
                step = PrintStep.LINK,
                access = MakerWorldAccess.READY,
                model = result.data,
                images = emptyMap(),
                message = if (result.data.instances.isEmpty()) "MakerWorld listed no plate configurations for this model." else null,
            ).also { loadCovers(result.data) }
            is ApiResult.Error -> back(PrintStep.LINK, result.toUiErrorState(aboutPrinter = false).message)
        }
    }

    /** Has Bambuddy download the chosen plate configuration, then goes to the review. */
    fun importInstance(profileId: Int?) {
        val model = _state.value.model ?: return
        work("Importing from MakerWorld") {
            when (val result = files.importMakerWorld(model, profileId)) {
                is ApiResult.Success -> review(result.data)
                is ApiResult.Error -> back(PrintStep.LINK, result.toUiErrorState(aboutPrinter = false).message)
            }
        }
    }

    /** Applies a printer, plate or slot choice from the review and re-checks. */
    fun select(selection: PrintSelection) {
        when (selection) {
            is PrintSelection.Printer -> viewModelScope.launch {
                _state.value = _state.value.copy(printerId = selection.id)
                loadSlots()
                propose()
                refreshRefusal()
                recheck()
            }
            is PrintSelection.Plate -> {
                _state.value = _state.value.copy(plateIndex = selection.index, preview = null)
                propose()
                recheck()
                loadPreview()
            }
            PrintSelection.PlateCleared -> viewModelScope.launch {
                val id = _state.value.printerId ?: return@launch
                when (val r = control.clearPlate(id)) {
                    is ApiResult.Success -> {
                        val list = (printers.fetchPrinters() as? ApiResult.Success)?.data ?: _state.value.printers
                        _state.value = _state.value.copy(printers = list, message = null)
                        recheck()
                    }
                    is ApiResult.Error -> _state.value = _state.value.copy(message = r.toUiErrorState(aboutPrinter = false).message)
                }
            }
            is PrintSelection.Sliced -> viewModelScope.launch { review(selection.file) }
            is PrintSelection.Slot -> {
                _state.value = _state.value.copy(chosen = _state.value.chosen + (selection.filamentSlot to selection.slot))
                recheck()
            }
        }
    }

    /** Creates a staged queue item that waits until the user starts it from the queue. */
    fun addToQueue() = stageThen(start = false, skip = false)

    /** Stages and starts after the user confirmed. [skipFilamentCheck] is true only after a shortfall was shown and accepted. */
    fun start(skipFilamentCheck: Boolean) = stageThen(start = true, skip = skipFilamentCheck)

    /** Backs out: stops any transfer, deletes the file this flow created if nothing was queued from it, and closes. */
    fun cancel() {
        job?.cancel()
        val current = _state.value
        val orphan = current.file?.takeIf { it.createdHere && current.stagedItemId == null }
        slice.close()
        _state.value = PrintFlowState(closed = true)
        // Only files this flow made: the one under review, and the file it was sliced from when that was imported or uploaded here.
        if (orphan != null) viewModelScope.launch { (listOf(orphan.libraryId) + orphan.alsoCreated).forEach { files.discard(it) } }
    }

    private fun stageThen(start: Boolean, skip: Boolean) {
        val s = _state.value
        val file = s.file ?: return
        val plate = s.plate ?: return
        val printer = s.printer?.id?.toIntOrNull() ?: return
        if (!(if (start) s.canStart else s.canAdd)) return
        work(if (start) "Starting the print" else "Adding to the queue") {
            val itemId = s.stagedItemId ?: run {
                val mapping = AmsMapping.build(s.chosen.mapNotNull { (slot, c) -> c?.let { slot to it.tray } }.toMap())
                when (
                    val staged = files.stage(
                        StagedPrint(
                            file.libraryId,
                            printer,
                            plate.index,
                            mapping,
                            file.createdHere,
                            fromArchive =
                            file.origin == FileOrigin.ARCHIVE,
                        ),
                    )
                ) {
                    is ApiResult.Success -> staged.data.also { _state.value = _state.value.copy(stagedItemId = it) }
                    is ApiResult.Error -> return@work back(PrintStep.REVIEW, staged.toUiErrorState(aboutPrinter = false).message)
                }
            }
            if (!start) return@work finish("Added to the queue. It waits there until you start it.")
            when (val result = files.start(itemId, printer, skip)) {
                is ApiResult.Success -> {
                    _state.value =
                        _state.value.copy(
                            step = PrintStep.DONE,
                            watching = true,
                            problem = false,
                            shortfall = null,
                            message = "Bambuddy accepted the start. Checking that the printer takes it…",
                        )
                    val verdict = StartWatcher(queue).watch(itemId)
                    _state.value = _state.value.copy(
                        watching = false,
                        problem = StartCheck.isProblem(verdict),
                        message = StartCheck.words(verdict, s.printer?.name ?: "the printer"),
                    )
                }
                is ApiResult.Error -> if (result.code == SHORTFALL_CODE && !skip) {
                    _state.value =
                        _state.value.copy(
                            step = PrintStep.REVIEW,
                            shortfall = result.serverDetail ?: "Bambuddy reports a filament problem.",
                        )
                } else {
                    back(PrintStep.REVIEW, "${result.toUiErrorState().message} The job is in the queue, waiting.")
                }
            }
        }
    }

    private fun finish(text: String) {
        _state.value = _state.value.copy(step = PrintStep.DONE, message = text, shortfall = null)
    }

    private fun work(label: String, progress: Float? = null, block: suspend () -> Unit) {
        job?.cancel()
        _state.value =
            _state.value.copy(step = PrintStep.WORKING, workingLabel = label, progress = progress, message = null, shortfall = null)
        job = viewModelScope.launch { block() }
    }

    private fun back(step: PrintStep, message: String) {
        _state.value = _state.value.copy(step = step, message = message, progress = null)
    }

    private suspend fun review(file: PrintFile) {
        _state.value = _state.value.copy(file = file, plateIndex = file.plates.firstOrNull()?.index, stagedItemId = null, message = null)
        loadSlots()
        propose()
        refreshRefusal()
        _state.value = _state.value.copy(step = PrintStep.REVIEW, progress = null, preview = null)
        recheck()
        loadPreview()
    }

    /** Fetches the cover pictures (through Bambuddy) so the user can see the model before it is imported; a missing one is simply not shown. */
    private fun loadCovers(model: MakerWorldModel) {
        val urls = (listOfNotNull(model.coverUrl) + model.instances.mapNotNull { it.coverUrl }).distinct().take(MAX_COVERS)
        viewModelScope.launch {
            for (url in urls) {
                val bytes = (files.makerWorldImage(url) as? ApiResult.Success)?.data ?: continue
                if (_state.value.model?.modelId ==
                    model.modelId
                ) {
                    _state.value = _state.value.copy(images = _state.value.images + (url to bytes))
                }
            }
        }
    }

    private fun loadPreview() {
        val s = _state.value
        val file = s.file ?: return
        val plate = s.plate ?: return
        viewModelScope.launch {
            val result = if (file.origin == FileOrigin.ARCHIVE) {
                files.archivePreview(file.libraryId, plate.index, plate.hasPreview)
            } else {
                files.preview(file.libraryId, plate.index, plate.hasPreview)
            }
            val bytes = (result as? ApiResult.Success)?.data
            if (_state.value.file?.libraryId == file.libraryId && _state.value.plateIndex == plate.index) {
                _state.value = _state.value.copy(preview = bytes)
            }
        }
    }

    private suspend fun loadSlots() {
        units = (ams.getAms() as? ApiResult.Success)?.data.orEmpty()
        val id = _state.value.printerId?.toIntOrNull()
        _state.value = _state.value.copy(slots = SlotCandidates.of(units, id), units = units.filter { it.printerId == id })
    }

    private fun propose() {
        val s = _state.value
        _state.value = s.copy(chosen = FilamentMatcher.propose(s.plate?.filaments.orEmpty(), s.slots))
    }

    private suspend fun refreshRefusal() {
        val id = _state.value.printerId ?: run {
            startRefusal = null
            return
        }
        startRefusal = gate.refusal(id, PrinterTool.START_QUEUED_PRINT, _state.value.printer?.name.orEmpty())
    }

    private fun recheck() {
        val s = _state.value
        val file = s.file ?: return
        val printer = s.printer
        val findings = if (printer == null) {
            emptyList()
        } else {
            val trays = units.filter { it.printerId == printer.id.toIntOrNull() && it.isDrying }.flatMap { u ->
                u.trays.mapNotNull { AmsTrayAddress.forFeed(u.amsId, u.isHt, it.trayId) }
            }.toSet()
            PrintValidation.evaluate(
                PrintCheckInput(
                    file = file,
                    printerModel = printer.model,
                    printerState = printer.state,
                    startRefusal = startRefusal,
                    hasPrinterErrors = printer.hasHmsWarning,
                    awaitingPlateClear = printer.awaitingPlateClear,
                    required = s.plate?.filaments.orEmpty(),
                    chosen = s.chosen,
                    dryingTrays = trays,
                ),
            )
        }
        _state.value = s.copy(findings = findings)
    }

    /** Builds the view model with its repositories. */
    class Factory(
        private val files: PrintFileRepository,
        private val printers: PrinterRepository,
        private val ams: AmsRepository,
        private val gate: PrinterToolGate,
        private val queue: QueueRepository,
        private val control: PrintControlRepository,
        private val memory: SliceMemoryStore,
    ) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PrintFlowViewModel(files, printers, ams, gate, queue, control, memory) as T
    }

    private companion object {
        const val SHORTFALL_CODE = 409
        const val MAX_COVERS = 12
    }
}
