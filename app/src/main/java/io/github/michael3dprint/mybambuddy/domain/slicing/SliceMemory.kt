package io.github.michael3dprint.mybambuddy.domain.slicing

import io.github.michael3dprint.mybambuddy.domain.model.BedType
import io.github.michael3dprint.mybambuddy.domain.model.SliceOptions
import io.github.michael3dprint.mybambuddy.domain.model.SliceRequest
import io.github.michael3dprint.mybambuddy.domain.model.SlicerPreset
import io.github.michael3dprint.mybambuddy.domain.print.ModelNames

/** What the user chose the last time a slice was accepted by Bambuddy (design 43). Preset ids and names only; nothing secret. */
data class SliceMemory(
    /** The build plate's name as Bambuddy takes it. */
    val bed: String? = null,
    /** The process preset id used for each printer model (keyed by the normalised model name). */
    val processByModel: Map<String, String> = emptyMap(),
    /** The filament preset id used for each filament type (keyed by the upper-case type). */
    val filamentByType: Map<String, String> = emptyMap(),
    val autoOrient: Boolean = false,
    val autoArrange: Boolean = false,
    val useEmbeddedSettings: Boolean = false,
)

/** Where the memory is kept between runs. */
interface SliceMemoryStore {
    /** The saved memory, or an empty one when nothing was saved or it cannot be read. */
    fun load(): SliceMemory

    /** Saves [memory], replacing what was there. */
    fun save(memory: SliceMemory)
}

/** A store that remembers nothing, for screens and tests that do not need it. */
object NoSliceMemory : SliceMemoryStore {
    override fun load(): SliceMemory = SliceMemory()

    override fun save(memory: SliceMemory) = Unit
}

/** Pure rules for using and updating the memory. Anything remembered that no longer exists is ignored, so a deleted preset never breaks a slice. */
object SliceMemoryRules {
    /** The remembered build plate, or null when none or it is not one of the plates the app offers. */
    fun bed(memory: SliceMemory): BedType? = BedType.entries.firstOrNull { it.apiName == memory.bed }

    /** The remembered process preset for [model] when it is still among [options]. */
    fun process(memory: SliceMemory, model: String, options: List<SlicerPreset>): SlicerPreset? =
        memory.processByModel[ModelNames.normalize(model)]?.let { id -> options.firstOrNull { it.id == id } }

    /** The remembered filament preset for [type] when it is still among [options]. */
    fun filament(memory: SliceMemory, type: String, options: List<SlicerPreset>): SlicerPreset? =
        memory.filamentByType[type.uppercase()]?.let { id -> options.firstOrNull { it.id == id } }

    /** The remembered options; the designer's-settings switch only counts when the file targets this printer ([embeddedAvailable]). */
    fun options(memory: SliceMemory, embeddedAvailable: Boolean) = SliceOptions(
        autoOrient = memory.autoOrient,
        autoArrange = memory.autoArrange,
        useEmbeddedSettings = memory.useEmbeddedSettings && embeddedAvailable,
    )

    /** The memory after a slice with [request] on a printer of [model]; [types] are the filament types in the request's order. */
    fun after(memory: SliceMemory, request: SliceRequest, model: String, types: List<String>): SliceMemory = memory.copy(
        bed = request.bed.apiName,
        processByModel = memory.processByModel + (ModelNames.normalize(model) to request.process.id),
        filamentByType = memory.filamentByType + types.zip(request.filaments).associate { (type, preset) -> type.uppercase() to preset.id },
        autoOrient = request.options.autoOrient,
        autoArrange = request.options.autoArrange,
        useEmbeddedSettings = request.options.useEmbeddedSettings,
    )
}
