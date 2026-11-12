package io.github.michael3dprint.mybambuddy.domain.model

/** One slicer preset Bambuddy can slice with. [source] is its tier (standard, cloud, local or orca_cloud); [type] is the filament type when known. */
data class SlicerPreset(val source: String, val id: String, val name: String, val type: String?, val compatiblePrinters: List<String>)

/** Every preset Bambuddy offers, flattened across its tiers. */
data class SlicerPresets(val printers: List<SlicerPreset>, val processes: List<SlicerPreset>, val filaments: List<SlicerPreset>)

/** One filament slot of a project that is being sliced: its slot number, type, colour (hex, may be blank) and whether the plate uses it. */
data class ProjectSlot(val slotId: Int, val type: String, val colorHex: String, val usedInPlate: Boolean)

/** The build plates the user's slicer knows. [apiName] is the exact name Bambuddy's slice request takes. */
enum class BedType(val apiName: String, val label: String, val alsoKnownAs: String?) {
    COOL("Cool Plate", "Cool Plate", "also sold as Bambu Cool Plate or PLA Plate"),
    ENGINEERING("Engineering Plate", "Engineering Plate", null),
    HIGH_TEMP("High Temp Plate", "High Temp Plate", "also called Smooth PEI"),
    TEXTURED_PEI("Textured PEI Plate", "Textured PEI Plate", null),
    SUPERTACK("Supertack Plate", "Supertack Plate", null),
}

/** The additional options of a slice; each is off by default, because each changes the model or the settings the designer chose. */
data class SliceOptions(val autoOrient: Boolean = false, val autoArrange: Boolean = false, val useEmbeddedSettings: Boolean = false) {
    /** How many options are changed from their defaults. */
    val changed: Int get() = listOf(autoOrient, autoArrange, useEmbeddedSettings).count { it }
}

/** What is sent to start a slice: one filament preset and one colour for every project slot, in slot order. */
data class SliceRequest(
    val printer: SlicerPreset,
    val process: SlicerPreset,
    val filaments: List<SlicerPreset>,
    val colours: List<String>,
    val plate: Int,
    val bed: BedType,
    val options: SliceOptions = SliceOptions(),
)

/** Where a slice job is: running (with a percent when the slicer reports one), done with the new library file, or failed with the reason. */
sealed interface SliceJobState {
    /** Still waiting or running. */
    data class Running(val percent: Int?) : SliceJobState

    /** Finished; the sliced file is in the library under [fileId]. */
    data class Done(val fileId: Int, val name: String) : SliceJobState

    /** The slice failed; [detail] is Bambuddy's own reason. */
    data class Failed(val detail: String) : SliceJobState
}
