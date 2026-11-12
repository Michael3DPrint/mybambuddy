package io.github.michael3dprint.mybambuddy.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.JobOptions
import io.github.michael3dprint.mybambuddy.domain.model.PlateInfo
import io.github.michael3dprint.mybambuddy.domain.model.PrintFile
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.model.StagedPrint
import io.github.michael3dprint.mybambuddy.domain.policy.JobChange
import io.github.michael3dprint.mybambuddy.domain.policy.JobEdit
import io.github.michael3dprint.mybambuddy.domain.policy.JobSettings
import io.github.michael3dprint.mybambuddy.domain.print.Finding
import io.github.michael3dprint.mybambuddy.domain.print.PrintCheckInput
import io.github.michael3dprint.mybambuddy.domain.print.PrintValidation
import io.github.michael3dprint.mybambuddy.domain.print.SlotCandidate
import io.github.michael3dprint.mybambuddy.domain.print.SlotCandidates
import io.github.michael3dprint.mybambuddy.domain.repo.AmsRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrintFileRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import io.github.michael3dprint.mybambuddy.domain.repo.QueueRepository
import io.github.michael3dprint.mybambuddy.domain.slicing.NoSliceMemory
import io.github.michael3dprint.mybambuddy.domain.slicing.SliceMemoryStore
import io.github.michael3dprint.mybambuddy.ui.print.SliceFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A choice made on the job page. */
sealed interface JobChoice {
    /** A different printer. */
    data class Printer(val id: Int) : JobChoice

    /** A different plate of a multi-plate file. */
    data class Plate(val index: Int) : JobChoice

    /** The slot that supplies project filament [filamentSlot]. */
    data class Slot(val filamentSlot: Int, val slot: SlotCandidate) : JobChoice

    /** New print options. */
    data class Options(val options: JobOptions) : JobChoice
}

/** Everything the job page shows. A job that is not waiting is read-only. */
data class QueueJobState(
    val loading: Boolean = true,
    val error: String? = null,
    val job: QueueItem? = null,
    val file: PrintFile? = null,
    val printers: List<Printer> = emptyList(),
    val units: List<AmsUnit> = emptyList(),
    val slots: List<SlotCandidate> = emptyList(),
    val edited: JobSettings? = null,
    val chosen: Map<Int, SlotCandidate?> = emptyMap(),
    val findings: List<Finding> = emptyList(),
    val preview: ByteArray? = null,
    val busy: String? = null,
    val message: String? = null,
    /** A sliced file waiting for the user to confirm that it replaces the job. */
    val replacement: PrintFile? = null,
    /** True once something changed on the server, so the queue list should be reloaded when the page closes. */
    val changedQueue: Boolean = false,
    val closed: Boolean = false,
) {
    /** Only a waiting job can be edited or re-sliced. */
    val editable: Boolean get() = job?.isPending == true

    /** The plate being shown: the job's, else the file's first. */
    val plate: PlateInfo? get() = file?.plates?.let { plates -> plates.firstOrNull { it.index == edited?.plateId } ?: plates.firstOrNull() }

    /** What differs from the server's copy of the job; empty when nothing was changed. */
    val change: JobChange get() = job?.let { j -> edited?.let { JobEdit.diff(JobEdit.settingsOf(j), it) } } ?: JobChange()

    /** True when something changed and nothing blocks saving it. */
    val canSave: Boolean get() = editable && !change.isEmpty && busy == null && PrintValidation.canAdd(findings)
}

/**
 * The page of one queue job (design 42): its details, edits that are saved as one change with only the fields that differ, and a re-slice that
 * replaces the job. Every server call is a single attempt; nothing here starts a print.
 */
class QueueJobViewModel(
    private val queue: QueueRepository,
    private val files: PrintFileRepository,
    private val printers: PrinterRepository,
    private val ams: AmsRepository,
    memory: SliceMemoryStore = NoSliceMemory,
) : ViewModel() {
    private var allUnits: List<AmsUnit> = emptyList()
    private val _state = MutableStateFlow(QueueJobState())
    val state: StateFlow<QueueJobState> = _state.asStateFlow()

    /** The slice dialog for re-slicing the job's file (design 40); its result comes back as [QueueJobState.replacement]. */
    val slice = SliceFlow(files, viewModelScope, memory) { _state.value = _state.value.copy(replacement = it) }

    /** Opens [item]: reads it again from the server, then its file, printers, AMS and picture. */
    fun open(item: QueueItem) = load(item, message = null, changedQueue = false)

    /** Reads the job and everything around it again, keeping a result message (and whether the queue changed) across the reload. */
    private fun load(item: QueueItem, message: String?, changedQueue: Boolean) {
        _state.value = QueueJobState(job = item, message = message, changedQueue = changedQueue)
        viewModelScope.launch {
            val fresh = (queue.getQueue() as? ApiResult.Success)?.data?.firstOrNull { it.id == item.id }
                ?: return@launch fail("This job is no longer in the queue.")
            val list = (printers.fetchPrinters() as? ApiResult.Success)?.data.orEmpty()
            val units = (ams.getAms() as? ApiResult.Success)?.data.orEmpty()
            allUnits = units
            val file = fresh.libraryFileId?.let {
                (files.open(it, fresh.name, createdHere = false, alsoCreated = emptyList()) as? ApiResult.Success)?.data
            }
            val settings = JobEdit.settingsOf(fresh)
            val slots = SlotCandidates.of(units, settings.printerId)
            val plate = file?.plates?.let { p -> p.firstOrNull { it.index == settings.plateId } ?: p.firstOrNull() }
            _state.value = QueueJobState(
                loading = false,
                job = fresh,
                file = file,
                printers = list,
                units = units.filter { it.printerId == settings.printerId },
                slots = slots,
                edited = settings,
                chosen = JobEdit.chosenFrom(settings.amsMapping, plate?.filaments.orEmpty(), slots),
                message = message,
                changedQueue = changedQueue,
            )
            recheck()
            val bytes = (queue.picture(fresh) as? ApiResult.Success)?.data
            _state.value = _state.value.copy(preview = bytes)
        }
    }

    /** Applies a choice on the page and re-checks it. The mapping is rebuilt only when a slot or the printer changed, so it never differs by accident. */
    fun edit(choice: JobChoice) {
        val s = _state.value
        val edited = s.edited ?: return
        if (!s.editable) return
        _state.value = when (choice) {
            is JobChoice.Printer -> s.copy(
                edited = edited.copy(printerId = choice.id, amsMapping = emptyList()),
                slots = SlotCandidates.of(allUnits, choice.id),
                units = allUnits.filter { it.printerId == choice.id },
                chosen = emptyMap(),
            )
            is JobChoice.Plate -> s.copy(edited = edited.copy(plateId = choice.index))
            is JobChoice.Slot -> {
                val chosen = s.chosen + (choice.filamentSlot to choice.slot)
                s.copy(chosen = chosen, edited = edited.copy(amsMapping = JobEdit.mappingOf(chosen)))
            }
            is JobChoice.Options -> s.copy(edited = edited.copy(options = choice.options))
        }
        recheck()
    }

    /** Saves the changes as one request with only the fields that differ. */
    fun save() {
        val s = _state.value
        val job = s.job ?: return
        if (!s.canSave) return
        _state.value = s.copy(busy = "Saving", message = null)
        viewModelScope.launch {
            when (val r = queue.update(job.id, s.change)) {
                is ApiResult.Success -> load(job, message = "Saved.", changedQueue = true)
                is ApiResult.Error -> if (r.code == CONFLICT) {
                    load(job, message = "This job is no longer waiting, so it can't be changed. Reloaded.", changedQueue = false)
                } else {
                    _state.value = _state.value.copy(busy = null, message = r.toUiErrorState(aboutPrinter = false).message)
                }
            }
        }
    }

    /** Opens the slice page for the job's file. */
    fun reslice() {
        val s = _state.value
        val file = s.file ?: return
        val printer = s.printers.firstOrNull { it.id == s.edited?.printerId?.toString() } ?: return
        if (s.editable) slice.open(file, printer.model, s.slots)
    }

    /** Replaces the job with the new slice: the new job is staged at the old job's place first, and only then is the old one removed. */
    fun replace() {
        val s = _state.value
        val old = s.job ?: return
        val new = s.replacement ?: return
        val settings = s.edited ?: return
        val printerId = settings.printerId ?: return
        val plate = new.plates.firstOrNull() ?: return
        if (!new.isSliced) return _state.update("The new file is not sliced, so it can't replace the job.")
        _state.value = s.copy(busy = "Replacing the job", message = null)
        viewModelScope.launch {
            val chosen = JobEdit.carryOver(plate.filaments, s.chosen, s.slots)
            val staged =
                StagedPrint(
                    new.libraryId,
                    printerId,
                    plate.index,
                    JobEdit.mappingOf(chosen),
                    cleanup = true,
                    insertPosition = old.position.takeIf {
                        it >
                            0
                    },
                    options = settings.options,
                )
            when (val made = files.stage(staged)) {
                is ApiResult.Error -> _state.update(made.toUiErrorState(aboutPrinter = false).message)
                is ApiResult.Success -> when (val gone = queue.remove(old.id)) {
                    is ApiResult.Success ->
                        _state.value =
                            _state.value.copy(busy = null, replacement = null, changedQueue = true, closed = true)
                    is ApiResult.Error -> _state.value = _state.value.copy(
                        busy = null,
                        replacement = null,
                        changedQueue = true,
                        message = "The new job was added, but the old one could not be removed: ${gone.toUiErrorState(
                            aboutPrinter = false,
                        ).message} Both are in the queue.",
                    )
                }
            }
        }
    }

    /** Drops a new slice the user chose not to use, deleting the file this page made. */
    fun dismissReplacement() {
        val file = _state.value.replacement ?: return
        _state.value = _state.value.copy(replacement = null)
        viewModelScope.launch { (listOf(file.libraryId) + file.alsoCreated).forEach { files.discard(it) } }
    }

    /** Closes the page; any slice in progress stops being waited for. */
    fun close() {
        slice.close()
        _state.value = _state.value.copy(closed = true)
    }

    private fun MutableStateFlow<QueueJobState>.update(message: String) {
        value = value.copy(busy = null, message = message)
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(loading = false, error = message)
    }

    private fun recheck() {
        val s = _state.value
        val file = s.file ?: return
        val settings = s.edited ?: return
        val printer = s.printers.firstOrNull { it.id == settings.printerId?.toString() } ?: return
        _state.value = s.copy(
            findings = PrintValidation.evaluate(
                PrintCheckInput(
                    file = file,
                    printerModel = printer.model,
                    printerState = printer.state,
                    startRefusal = null,
                    hasPrinterErrors = printer.hasHmsWarning,
                    awaitingPlateClear = false,
                    required = s.plate?.filaments.orEmpty(),
                    chosen = s.chosen,
                    dryingTrays = emptySet(),
                ),
            ),
        )
    }

    /** Builds the view model with its repositories. */
    class Factory(
        private val queue: QueueRepository,
        private val files: PrintFileRepository,
        private val printers: PrinterRepository,
        private val ams: AmsRepository,
        private val memory: SliceMemoryStore,
    ) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = QueueJobViewModel(queue, files, printers, ams, memory) as T
    }

    private companion object {
        const val CONFLICT = 409
    }
}
