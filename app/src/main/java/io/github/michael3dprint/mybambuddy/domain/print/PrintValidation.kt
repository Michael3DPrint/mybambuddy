package io.github.michael3dprint.mybambuddy.domain.print

import io.github.michael3dprint.mybambuddy.domain.model.PrintFile
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.model.RequiredFilament

/** How serious a finding is: a blocker disables the action, a warning needs a tick, info is shown only. */
enum class FindingLevel { BLOCKER, WARNING, INFO }

/** One thing the review found; [forStartOnly] findings block Start print but not Add to queue. */
data class Finding(val level: FindingLevel, val text: String, val forStartOnly: Boolean = false)

/** Everything the validation looks at. [startRefusal] is the reason the Start tool is off for this printer (the existing tool gate), if any. */
data class PrintCheckInput(
    val file: PrintFile,
    val printerModel: String,
    val printerState: PrinterState,
    val startRefusal: String?,
    val hasPrinterErrors: Boolean,
    val awaitingPlateClear: Boolean = false,
    val required: List<RequiredFilament>,
    val chosen: Map<Int, SlotCandidate?>,
    val dryingTrays: Set<Int>,
)

/** The review's validation (design 35, the rows built so far). Pure: the same input always gives the same findings. */
object PrintValidation {
    /** Every finding for [input], most serious first. */
    fun evaluate(input: PrintCheckInput): List<Finding> {
        val out = mutableListOf<Finding>()
        fileFindings(input, out)
        slotFindings(input, out)
        printerFindings(input, out)
        return out.sortedBy { it.level.ordinal }
    }

    /** True when nothing blocks adding to the queue. */
    fun canAdd(findings: List<Finding>) = findings.none { it.level == FindingLevel.BLOCKER && !it.forStartOnly }

    /** True when nothing blocks starting the print. */
    fun canStart(findings: List<Finding>) = findings.none { it.level == FindingLevel.BLOCKER }

    private fun fileFindings(input: PrintCheckInput, out: MutableList<Finding>) {
        if (!input.file.isSliced) {
            out +=
                Finding(
                    FindingLevel.BLOCKER,
                    "This file isn't sliced. Slice it in Bambu Studio for this printer, then choose the sliced file.",
                )
        }
        val sliced = ModelNames.normalize(input.file.slicedForModel)
        val printer = ModelNames.normalize(input.printerModel)
        when {
            sliced.isEmpty() || printer.isEmpty() ->
                out +=
                    Finding(
                        FindingLevel.WARNING,
                        "The file doesn't say which printer it was sliced for, so it can't be checked against yours.",
                    )
            sliced != printer ->
                out += Finding(FindingLevel.BLOCKER, "This file was sliced for ${input.file.slicedForModel}, not ${input.printerModel}.")
        }
    }

    private fun slotFindings(input: PrintCheckInput, out: MutableList<Finding>) {
        val trayUse = input.chosen.values.filterNotNull().groupingBy { it.tray }.eachCount()
        for (need in input.required) {
            val slot = input.chosen[need.slotId]
            val name = "Filament ${need.slotId} (${need.type})"
            if (slot == null) {
                out += Finding(FindingLevel.BLOCKER, "$name has no slot chosen.")
                continue
            }
            if (slot.isEmpty) out += Finding(FindingLevel.BLOCKER, "$name points at an empty slot (${slot.label}).")
            if ((trayUse[slot.tray] ?: 0) > 1) out += Finding(FindingLevel.BLOCKER, "$name shares a slot with another filament.")
            if (slot.material.isBlank()) {
                out += Finding(FindingLevel.BLOCKER, "$name: the slot's filament type is unknown.")
            } else if (!slot.material.equals(need.type, ignoreCase = true)) {
                out += Finding(FindingLevel.WARNING, "$name is ${need.type} but the slot holds ${slot.material}.")
            }
            val left = slot.gramsLeft
            if (left != null && need.grams > 0 && left < need.grams) {
                out += Finding(FindingLevel.WARNING, "$name needs about ${need.grams.toInt()} g but the slot has about $left g.")
            }
            if (FilamentMatcher.colorDistance(need.colorHex, slot.colorHex) > FilamentMatcher.CLOSE_ENOUGH) {
                out += Finding(FindingLevel.INFO, "$name: the slot colour differs from the file's.")
            }
            if (slot.tray in input.dryingTrays) out += Finding(FindingLevel.WARNING, "$name: that AMS unit is drying right now.")
        }
    }

    private fun printerFindings(input: PrintCheckInput, out: MutableList<Finding>) {
        input.startRefusal?.let { out += Finding(FindingLevel.BLOCKER, it, forStartOnly = true) }
        when (input.printerState) {
            PrinterState.PRINTING, PrinterState.PAUSED ->
                out += Finding(FindingLevel.BLOCKER, "The printer is busy; it can start this when it is idle.", forStartOnly = true)
            PrinterState.OFFLINE, PrinterState.UNKNOWN ->
                out += Finding(FindingLevel.BLOCKER, "The printer isn't reachable right now.", forStartOnly = true)
            PrinterState.ERROR -> out += Finding(FindingLevel.BLOCKER, "The printer reports an error.", forStartOnly = true)
            PrinterState.IDLE, PrinterState.FINISHED, PrinterState.FAILED -> Unit
        }
        if (input.awaitingPlateClear) {
            out +=
                Finding(
                    FindingLevel.BLOCKER,
                    "The printer is waiting for you to clear the build plate. Mark it cleared first.",
                    forStartOnly = true,
                )
        }
        if (input.hasPrinterErrors) out += Finding(FindingLevel.WARNING, "The printer reports active errors.")
    }
}
