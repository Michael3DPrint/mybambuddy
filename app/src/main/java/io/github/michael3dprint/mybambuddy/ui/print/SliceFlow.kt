package io.github.michael3dprint.mybambuddy.ui.print

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.BedType
import io.github.michael3dprint.mybambuddy.domain.model.PrintFile
import io.github.michael3dprint.mybambuddy.domain.model.ProjectSlot
import io.github.michael3dprint.mybambuddy.domain.model.RequiredFilament
import io.github.michael3dprint.mybambuddy.domain.model.SliceJobState
import io.github.michael3dprint.mybambuddy.domain.model.SliceOptions
import io.github.michael3dprint.mybambuddy.domain.model.SliceRequest
import io.github.michael3dprint.mybambuddy.domain.model.SlicerPreset
import io.github.michael3dprint.mybambuddy.domain.model.SlicerPresets
import io.github.michael3dprint.mybambuddy.domain.print.FilamentMatcher
import io.github.michael3dprint.mybambuddy.domain.print.ModelNames
import io.github.michael3dprint.mybambuddy.domain.print.SlotCandidate
import io.github.michael3dprint.mybambuddy.domain.repo.PrintFileRepository
import io.github.michael3dprint.mybambuddy.domain.slicing.ScaleRule
import io.github.michael3dprint.mybambuddy.domain.slicing.SliceMemoryRules
import io.github.michael3dprint.mybambuddy.domain.slicing.SliceMemoryStore
import io.github.michael3dprint.mybambuddy.domain.slicing.SlicePicker
import io.github.michael3dprint.mybambuddy.domain.slicing.SliceRequestBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the slice dialog is. */
enum class SlicePhase { CLOSED, LOADING, READY, SLICING, FAILED }

/** What the slice dialog shows (design 39). The options are computed from [presets], not stored. */
data class SliceState(
    val phase: SlicePhase = SlicePhase.CLOSED,
    val source: PrintFile? = null,
    val model: String = "",
    val presets: SlicerPresets? = null,
    val printer: SlicerPreset? = null,
    val process: SlicerPreset? = null,
    val slots: List<ProjectSlot> = emptyList(),
    val chosen: Map<Int, SlicerPreset?> = emptyMap(),
    /** The loaded AMS slots the filaments can come from (design 39 amendment). */
    val loaded: List<SlotCandidate> = emptyList(),
    /** The build plate on the printer; null until the user says, and a slice cannot start without it. */
    val bed: BedType? = null,
    /** The additional options (design 40). */
    val options: SliceOptions = SliceOptions(),
    /** True while the build plate is the one remembered from the last slice, so the page can remind the user to check it. */
    val bedFromMemory: Boolean = false,
    /** The scale of everything on the plate in percent; null while the text typed is not a number from 10 to 400 (design 40). */
    val scalePercent: Int? = 100,
    /** What the app is doing before the slice itself, such as making a scaled copy; blank while slicing. */
    val working: String = "",
    /** The AMS slot chosen for each project filament; null means none, and the file's own colour is sent. */
    val ams: Map<Int, SlotCandidate?> = emptyMap(),
    val message: String? = null,
    val percent: Int? = null,
) {
    /** The project slots with the colour of the loaded AMS slot where one is chosen, since that is what will really be printed. */
    val effectiveSlots: List<ProjectSlot> get() = slots.map { s -> ams[s.slotId]?.let { s.copy(colorHex = it.colorHex) } ?: s }

    /** The preset recommended for [slot]: the one for the chosen spool's product line, else the best of the filament's type. */
    fun recommended(slot: ProjectSlot): SlicerPreset? = printer?.let {
        SlicePicker.presetForSpool(slot.type, ams[slot.slotId]?.subBrand.orEmpty(), it, model, presets?.filaments.orEmpty())
    }

    /** The process presets that fit the chosen printer, best first. */
    val processOptions: List<SlicerPreset> get() = printer?.let {
        SlicePicker.processOptions(it, model, presets?.processes.orEmpty())
    }.orEmpty()

    /** The filament presets that fit slot [slot] on the chosen printer, best first. */
    fun filamentOptions(slot: ProjectSlot): List<SlicerPreset> =
        printer?.let { SlicePicker.filamentOptions(slot.type, it, model, presets?.filaments.orEmpty()) }.orEmpty()

    /** The designer's own settings can be used only when the file says it targets this printer's model. */
    val embeddedAvailable: Boolean get() = ModelNames.same(source?.slicedForModel, model)

    /** Why Slice is off, in words, or null when it can start. */
    val blocker: String? get() = when {
        phase != SlicePhase.READY -> "Not ready yet."
        bed == null -> "Choose which build plate is on the printer (Plate tab)."
        scalePercent == null -> "Enter a scale from ${ScaleRule.MIN_PERCENT} to ${ScaleRule.MAX_PERCENT} percent (Options tab)."
        process == null -> "Choose a process preset (Process tab)."
        else -> slots.firstOrNull { chosen[it.slotId] == null }?.let { "Choose a preset for filament ${it.slotId} (Filament tab)." }
    }

    /** True when every choice is made and a slice can be started. */
    val canSlice: Boolean get() = blocker == null && request != null

    /** The request that would be sent, or null while a choice is missing. */
    val request: SliceRequest?
        get() = SliceRequestBuilder.build(printer, process, effectiveSlots, chosen, PLATE, bed, effectiveOptions)

    /** The options with the designer's-settings switch forced off when the file does not target this printer. */
    val effectiveOptions: SliceOptions get() = options.copy(useEmbeddedSettings = options.useEmbeddedSettings && embeddedAvailable)
}

/** A choice made in the slice dialog. */
sealed interface SliceChoice {
    /** A different process preset. */
    data class Process(val preset: SlicerPreset) : SliceChoice

    /** A different filament preset for project slot [slotId]. */
    data class Filament(val slotId: Int, val preset: SlicerPreset) : SliceChoice

    /** Turns the additional options on or off. */
    data class Option(val orient: Boolean? = null, val arrange: Boolean? = null, val embedded: Boolean? = null) : SliceChoice

    /** The scale in percent, or null when what was typed is not a valid number. */
    data class Scale(val percent: Int?) : SliceChoice

    /** The build plate that is on the printer. */
    data class Bed(val type: BedType) : SliceChoice

    /** The loaded AMS slot that supplies project filament [slotId]; its product line picks the preset and its colour is sent. */
    data class AmsSlot(val slotId: Int, val slot: SlotCandidate) : SliceChoice
}

private const val PLATE = 1
private const val FULL_SCALE = 100

/**
 * Slices a file that has no G-code for the user's printer, then hands the result to [onSliced] for the normal review (design 39).
 * One slice request per tap, never repeated; leaving the dialog stops waiting but cannot stop the server's job.
 */
class SliceFlow(
    private val files: PrintFileRepository,
    private val scope: CoroutineScope,
    private val memory: SliceMemoryStore,
    private val onSliced: (PrintFile) -> Unit,
) {
    private val _state = MutableStateFlow(SliceState())
    val state: StateFlow<SliceState> = _state.asStateFlow()
    private var job: Job? = null

    /** Opens the dialog for [file] on a printer of [printerModel], loading the presets and the project's filament slots. */
    fun open(file: PrintFile, printerModel: String, loaded: List<SlotCandidate>) {
        job?.cancel()
        _state.value = SliceState(phase = SlicePhase.LOADING, source = file, model = printerModel)
        job = scope.launch {
            val presets = when (val r = files.slicerPresets()) {
                is ApiResult.Success -> r.data
                is ApiResult.Error -> return@launch fail(r.toUiErrorState(aboutPrinter = false).message)
            }
            val printer = SlicePicker.printer(printerModel, presets.printers)
                ?: return@launch fail("Bambuddy has no printer preset for $printerModel. Check Tools, Slicing.")
            val plate = file.plates.firstOrNull()?.index ?: PLATE
            val slots = when (val r = files.projectSlots(file.libraryId, plate)) {
                is ApiResult.Success -> r.data
                is ApiResult.Error -> return@launch fail(r.toUiErrorState(aboutPrinter = false).message)
            }
            if (slots.isEmpty()) return@launch fail("The file lists no filaments to slice with.")
            val remembered = memory.load()
            val processFits = SlicePicker.processOptions(printer, printerModel, presets.processes)
            val process = SliceMemoryRules.process(remembered, printerModel, processFits) ?: processFits.firstOrNull()
            val message = if (process == null) "No process preset fits $printerModel. Choose one in Bambuddy's slicer settings." else null
            val ams = FilamentMatcher.propose(
                slots.filter { it.usedInPlate }.map { RequiredFilament(it.slotId, it.type, it.colorHex, 0.0) },
                loaded,
            )
            val defaults = SlicePicker.defaultFilaments(slots, printer, printerModel, presets.filaments)
            val chosen = slots.associate { slot ->
                val spool = ams[slot.slotId]
                slot.slotId to
                    (
                        spool?.let { SlicePicker.presetForSpool(slot.type, it.subBrand, printer, printerModel, presets.filaments) }
                            ?: SliceMemoryRules.filament(
                                remembered,
                                slot.type,
                                SlicePicker.filamentOptions(slot.type, printer, printerModel, presets.filaments),
                            )
                            ?: defaults[slot.slotId]
                        )
            }
            _state.value = _state.value.copy(
                loaded = loaded,
                ams = ams,
                phase = SlicePhase.READY,
                presets = presets,
                printer = printer,
                process = process,
                slots = slots,
                chosen = chosen,
                message = message,
                bed = SliceMemoryRules.bed(remembered),
                bedFromMemory = SliceMemoryRules.bed(remembered) != null,
                options = SliceMemoryRules.options(remembered, ModelNames.same(file.slicedForModel, printerModel)),
            )
        }
    }

    /** Changes a preset in the dialog. */
    fun choose(choice: SliceChoice) {
        val s = _state.value
        _state.value = when (choice) {
            is SliceChoice.Process -> s.copy(process = choice.preset)
            is SliceChoice.Bed -> s.copy(bed = choice.type, bedFromMemory = false)
            is SliceChoice.Scale -> s.copy(scalePercent = choice.percent)
            is SliceChoice.Option -> s.copy(
                options = s.options.copy(
                    autoOrient = choice.orient ?: s.options.autoOrient,
                    autoArrange = choice.arrange ?: s.options.autoArrange,
                    useEmbeddedSettings = choice.embedded ?: s.options.useEmbeddedSettings,
                ),
            )
            is SliceChoice.Filament -> s.copy(chosen = s.chosen + (choice.slotId to choice.preset))
            is SliceChoice.AmsSlot -> {
                val slot = s.slots.firstOrNull { it.slotId == choice.slotId }
                val preset = s.printer?.let { p ->
                    slot?.let { SlicePicker.presetForSpool(it.type, choice.slot.subBrand, p, s.model, s.presets?.filaments.orEmpty()) }
                }
                s.copy(
                    ams = s.ams + (choice.slotId to choice.slot),
                    chosen =
                    s.chosen + (choice.slotId to (preset ?: s.chosen[choice.slotId])),
                )
            }
        }
    }

    /** Starts the slice (one request) and waits for the result. */
    fun start() {
        val s = _state.value
        val source = s.source ?: return
        val request = s.request ?: return
        val scale = s.scalePercent ?: return
        _state.value = s.copy(phase = SlicePhase.SLICING, message = null, percent = null, working = "")
        job = scope.launch {
            var target = source.libraryId
            var copyId: Int? = null
            if (scale != FULL_SCALE) {
                _state.value = _state.value.copy(working = "Making a copy of the file at $scale %")
                when (val c = files.scaledCopy(source, scale)) {
                    is ApiResult.Success -> {
                        copyId = c.data.libraryId
                        target = c.data.libraryId
                    }
                    is ApiResult.Error -> return@launch ready(c.serverDetail ?: c.toUiErrorState(aboutPrinter = false).message)
                }
                _state.value = _state.value.copy(working = "")
            }
            val jobId = when (val r = files.startSlice(target, request)) {
                is ApiResult.Success -> r.data.also { remember(s, request) }
                is ApiResult.Error -> {
                    copyId?.let { files.discard(it) }
                    return@launch ready(r.toUiErrorState(aboutPrinter = false).message)
                }
            }
            poll(jobId, source, copyId)
        }
    }

    /** Remembers what this slice used, once Bambuddy has accepted it. */
    private fun remember(s: SliceState, request: SliceRequest) {
        val types = s.slots.sortedBy { it.slotId }.map { it.type }
        memory.save(SliceMemoryRules.after(memory.load(), request, s.model, types))
    }

    /** Closes the dialog and stops waiting; a slice already started keeps running on the server. */
    fun close() {
        job?.cancel()
        _state.value = SliceState()
    }

    private suspend fun poll(jobId: Int, source: PrintFile, copyId: Int?) {
        var failures = 0
        repeat(MAX_POLLS) {
            when (val r = files.sliceJob(jobId)) {
                is ApiResult.Success -> {
                    failures = 0
                    when (val j = r.data) {
                        is SliceJobState.Running -> _state.value = _state.value.copy(percent = j.percent)
                        is SliceJobState.Failed -> {
                            copyId?.let { files.discard(it) }
                            return ready(j.detail)
                        }
                        is SliceJobState.Done -> {
                            finish(j, source)
                            // The scaled copy was only needed for the slice itself; the sliced file carries the scaled model.
                            copyId?.let { files.discard(it) }
                            return
                        }
                    }
                }
                is ApiResult.Error -> if (++failures >= MAX_FAILURES) return ready(r.toUiErrorState(aboutPrinter = false).message)
            }
            delay(POLL_MS)
        }
        ready("Slicing is taking longer than expected. Check Bambuddy's library later for the sliced file.")
    }

    private suspend fun finish(done: SliceJobState.Done, source: PrintFile) {
        val extras = source.alsoCreated + if (source.createdHere) listOf(source.libraryId) else emptyList()
        when (val r = files.open(done.fileId, done.name, createdHere = true, alsoCreated = extras)) {
            is ApiResult.Success -> {
                _state.value = SliceState()
                onSliced(r.data)
            }
            is ApiResult.Error -> ready(r.toUiErrorState(aboutPrinter = false).message)
        }
    }

    private fun ready(message: String) {
        _state.value = _state.value.copy(phase = SlicePhase.READY, message = message, percent = null)
    }

    /** Something needed before any choice could be made did not load: show the reason alone, with no half-filled choices. */
    private fun fail(message: String) {
        _state.value = _state.value.copy(phase = SlicePhase.FAILED, message = message)
    }

    private companion object {
        const val POLL_MS = 2_000L
        const val MAX_POLLS = 450
        const val MAX_FAILURES = 3
    }
}
