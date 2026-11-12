package io.github.michael3dprint.mybambuddy.ui.print

import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldAccess
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldModel
import io.github.michael3dprint.mybambuddy.domain.model.PlateInfo
import io.github.michael3dprint.mybambuddy.domain.model.PrintFile
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.print.Finding
import io.github.michael3dprint.mybambuddy.domain.print.PrintValidation
import io.github.michael3dprint.mybambuddy.domain.print.SlotCandidate

/** Where the print flow is: choosing a source, waiting on the server, entering a link, reviewing, or finished. */
enum class PrintStep { SOURCE, WORKING, LINK, REVIEW, DONE }

/** A choice the user makes on the review screen. */
sealed interface PrintSelection {
    /** The printer to print on. */
    data class Printer(val id: String) : PrintSelection

    /** The plate of a multi-plate file. */
    data class Plate(val index: Int) : PrintSelection

    /** The slot that supplies project filament [filamentSlot]; null clears the choice. */
    data class Slot(val filamentSlot: Int, val slot: SlotCandidate?) : PrintSelection

    /** The user marked the build plate cleared: tells Bambuddy (no printer command) and reads the printer again. */
    data object PlateCleared : PrintSelection

    /** A file the slice dialog just made; it replaces the one under review. */
    data class Sliced(val file: io.github.michael3dprint.mybambuddy.domain.model.PrintFile) : PrintSelection
}

/** Everything the print flow screens show. Derived values (findings, what can be done) are computed from the rest, not stored twice. */
data class PrintFlowState(
    val step: PrintStep = PrintStep.SOURCE,
    val message: String? = null,
    val workingLabel: String = "",
    val progress: Float? = null,
    val access: MakerWorldAccess? = null,
    val model: MakerWorldModel? = null,
    /** MakerWorld pictures by address (the model's cover and each plate configuration's), loaded after the lookup. */
    val images: Map<String, ByteArray> = emptyMap(),
    val file: PrintFile? = null,
    val printers: List<Printer> = emptyList(),
    val printerId: String? = null,
    val plateIndex: Int? = null,
    val slots: List<SlotCandidate> = emptyList(),
    /** The selected printer's AMS units, drawn with the same tiles as the Filament tab so loaded colour and type look the same. */
    val units: List<io.github.michael3dprint.mybambuddy.domain.model.AmsUnit> = emptyList(),
    /** The image of the plate under review (PNG or JPEG bytes), or null when the file has none or it is still loading. */
    val preview: ByteArray? = null,
    val chosen: Map<Int, SlotCandidate?> = emptyMap(),
    val findings: List<Finding> = emptyList(),
    val stagedItemId: Int? = null,
    /** The server's filament-shortfall reason after a start was refused with 409; shown with a "Print anyway" choice. */
    val shortfall: String? = null,
    val closed: Boolean = false,
    /** True while the app looks at whether the printer took a started job; the message says so. */
    val watching: Boolean = false,
    /** True when the final message is a problem (the print is not going ahead as it stands). */
    val problem: Boolean = false,
) {
    /** The chosen plate, or null before a file is reviewed. */
    val plate: PlateInfo? get() = file?.plates?.firstOrNull { it.index == plateIndex }

    /** The selected printer, or null. */
    val printer: Printer? get() = printers.firstOrNull { it.id == printerId }

    /** True when nothing blocks adding to the queue. */
    val canAdd: Boolean get() = printer != null && plate != null && PrintValidation.canAdd(findings)

    /** True when nothing blocks starting the print. */
    val canStart: Boolean get() = canAdd && PrintValidation.canStart(findings)
}
