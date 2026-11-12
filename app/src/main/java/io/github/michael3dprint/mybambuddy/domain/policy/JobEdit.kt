package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.JobOptions
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.model.RequiredFilament
import io.github.michael3dprint.mybambuddy.domain.print.AmsMapping
import io.github.michael3dprint.mybambuddy.domain.print.FilamentMatcher
import io.github.michael3dprint.mybambuddy.domain.print.SlotCandidate

/** How a waiting job will print, as far as the app lets the user change it (design 42). */
data class JobSettings(val printerId: Int?, val plateId: Int?, val amsMapping: List<Int>, val options: JobOptions)

/** The fields of a job that differ from what the server has; null means unchanged. Only these are sent. */
data class JobChange(
    val printerId: Int? = null,
    val plateId: Int? = null,
    val amsMapping: List<Int>? = null,
    val bedLevelling: io.github.michael3dprint.mybambuddy.domain.model.TriState? = null,
    val flowCalibration: io.github.michael3dprint.mybambuddy.domain.model.TriState? = null,
    val vibrationCalibration: Boolean? = null,
    val layerInspection: Boolean? = null,
    val timelapse: Boolean? = null,
    val useAms: Boolean? = null,
) {
    /** True when nothing differs, so there is nothing to save. */
    val isEmpty: Boolean get() = this == JobChange()
}

/** Pure rules for editing a waiting job: what changed, how to say it, and how a slot choice becomes the mapping the server takes. */
object JobEdit {
    /** The settings the job has now. */
    fun settingsOf(item: QueueItem) = JobSettings(item.printerId, item.plateId, item.amsMapping, item.options)

    /** What differs between [original] and [edited]. */
    fun diff(original: JobSettings, edited: JobSettings): JobChange {
        val a = original.options
        val b = edited.options
        return JobChange(
            printerId = edited.printerId.takeIf { it != original.printerId },
            plateId = edited.plateId.takeIf { it != original.plateId },
            amsMapping = edited.amsMapping.takeIf { it != original.amsMapping },
            bedLevelling = b.bedLevelling.takeIf { it != a.bedLevelling },
            flowCalibration = b.flowCalibration.takeIf { it != a.flowCalibration },
            vibrationCalibration = b.vibrationCalibration.takeIf { it != a.vibrationCalibration },
            layerInspection = b.layerInspection.takeIf { it != a.layerInspection },
            timelapse = b.timelapse.takeIf { it != a.timelapse },
            useAms = b.useAms.takeIf { it != a.useAms },
        )
    }

    /** One plain line per change for the confirmation; [printerName] turns a printer id into its name. */
    fun lines(change: JobChange, printerName: (Int) -> String): List<String> = listOfNotNull(
        change.printerId?.let { "Printer: ${printerName(it)}" },
        change.plateId?.let { "Plate: $it" },
        change.amsMapping?.let { "Filament slots changed" },
        change.bedLevelling?.let { "Bed levelling: ${it.label}" },
        change.flowCalibration?.let { "Flow calibration: ${it.label}" },
        change.vibrationCalibration?.let { "Vibration calibration: ${onOff(it)}" },
        change.layerInspection?.let { "Layer inspection: ${onOff(it)}" },
        change.timelapse?.let { "Timelapse: ${onOff(it)}" },
        change.useAms?.let { "Use the AMS: ${onOff(it)}" },
    )

    private fun onOff(value: Boolean) = if (value) "on" else "off"

    /** The slot each project filament currently uses, read back from the server's mapping (position is the project slot minus one). */
    fun chosenFrom(mapping: List<Int>, required: List<RequiredFilament>, slots: List<SlotCandidate>): Map<Int, SlotCandidate?> =
        required.associate { need ->
            val tray = mapping.getOrNull(need.slotId - 1)?.takeIf { it >= 0 }
            need.slotId to slots.firstOrNull { it.tray == tray }
        }

    /** The mapping list the server takes for the slots chosen. */
    fun mappingOf(chosen: Map<Int, SlotCandidate?>): List<Int> =
        AmsMapping.build(chosen.mapNotNull { (slot, c) -> c?.let { slot to it.tray } }.toMap())

    /** After a re-slice: keep a filament's earlier slot where the new file still needs that filament slot, else propose one. */
    fun carryOver(
        required: List<RequiredFilament>,
        earlier: Map<Int, SlotCandidate?>,
        slots: List<SlotCandidate>,
    ): Map<Int, SlotCandidate?> {
        val proposed = FilamentMatcher.propose(required, slots)
        return required.associate { need -> need.slotId to (earlier[need.slotId] ?: proposed[need.slotId]) }
    }
}
