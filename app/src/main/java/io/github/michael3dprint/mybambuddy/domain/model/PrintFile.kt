package io.github.michael3dprint.mybambuddy.domain.model

/** One filament a plate needs: its slot number in the project (1-based), type, colour and the slicer's grams estimate. */
data class RequiredFilament(val slotId: Int, val type: String, val colorHex: String, val grams: Double)

/** One plate of a library file with the slicer's own estimates; [seconds] and [grams] are null when the file carries none. */
data class PlateInfo(
    val index: Int,
    val name: String?,
    val seconds: Int?,
    val grams: Double?,
    val filaments: List<RequiredFilament>,
    /** True when the file carries a picture of this plate. */
    val hasPreview: Boolean = false,
)

/** A previous print to run again: its archive id and name (design 44). */
data class ArchiveRef(val id: Int, val name: String)

/** Where a file under review lives: Bambuddy's library, or its print archive (a previous print being run again, design 44). */
enum class FileOrigin { LIBRARY, ARCHIVE }

/**
 * A file in Bambuddy's library that can be printed. [slicedForModel] is the printer model the slicer targeted, when the file says.
 * [createdHere] is true when this flow uploaded or imported it, so cleaning up on cancel never deletes a file the user already had.
 */
data class PrintFile(
    val libraryId: Int,
    val name: String,
    val slicedForModel: String?,
    val plates: List<PlateInfo>,
    val createdHere: Boolean,
    /** Other library files this flow made on the way (the source of a slice), deleted together with this one when the user backs out. */
    val alsoCreated: List<Int> = emptyList(),
    /** For an archive, [libraryId] holds the archive id, and nothing about it is ever deleted by this app. */
    val origin: FileOrigin = FileOrigin.LIBRARY,
) {
    /** A file is ready to print only when at least one plate carries the slicer's time estimate. */
    val isSliced: Boolean get() = plates.any { it.seconds != null }
}

/** One plate configuration of a MakerWorld model. */
data class MakerWorldInstance(val profileId: Int, val title: String, val coverUrl: String? = null)

/** A MakerWorld model Bambuddy resolved from a link, with the library files already imported from it. */
data class MakerWorldModel(
    val modelId: Int,
    val title: String,
    val instances: List<MakerWorldInstance>,
    val alreadyImported: List<Int>,
    /** MakerWorld's cover picture address for the model, shown before anything is imported so the user can confirm it is the right one. */
    val coverUrl: String? = null,
)

/** Whether Bambuddy can download from MakerWorld, which needs its own Bambu Cloud sign-in. */
enum class MakerWorldAccess { READY, NOT_SIGNED_IN, SIGN_IN_EXPIRED }

/** What a staged queue item is created from. [amsMapping] has one entry per project filament slot, a tray number or -1. */
data class StagedPrint(
    val libraryFileId: Int,
    val printerId: Int,
    val plateIndex: Int,
    val amsMapping: List<Int>,
    val cleanup: Boolean,
    /** Where in the queue the job goes (1 is first); null puts it at the end. Used when a re-slice replaces a job. */
    val insertPosition: Int? = null,
    /** True when [libraryFileId] is really an archive id (Run again from a previous print). */
    val fromArchive: Boolean = false,
    /** The print options to carry over; null leaves Bambuddy's defaults. */
    val options: JobOptions? = null,
)
