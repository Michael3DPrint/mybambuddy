package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.PrintActivity
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.model.Spool

/**
 * Validation and risk rules for assigning a spool to an AMS slot. Pure and free of I/O so the same rules back both the
 * view model (fast feedback) and the repository (never send a bad request).
 *
 * Why this exists: Bambuddy's assign endpoint can reconfigure the physical slot (type, colour, temperatures), see
 * docs/design/09-slot-assignment-safety.md and docs/design/10-print-state-gate.md.
 */
object SlotLoadPolicy {
    /** Highest tray index the server accepts for a slot assignment. */
    const val MAX_TRAY_ID = 3

    /** Returns a user-facing message when the ids cannot be a valid assignment, or null when they can. */
    fun validate(spoolId: Int, printerId: Int, amsId: Int, trayId: Int): String? = when {
        spoolId <= 0 -> "That spool has no valid id."
        printerId <= 0 -> "That printer has no valid id."
        amsId < 0 -> "That AMS unit is not valid."
        trayId !in 0..MAX_TRAY_ID -> "Slot ${trayId + 1} is not a valid AMS slot."
        else -> null
    }

    /**
     * Everything that would stop the change from being processed. Empty means it may go ahead (after confirmation).
     * Used twice: early on the data shown, and again on a fresh status read right before the request is sent.
     */
    fun preflight(caps: PrinterCapabilities, tray: AmsTray, printerName: String): List<SlotBlocker> = buildList {
        val tool = ToolAvailability.statusOf(PrinterTool.SLOT_CHANGE, caps, printerName)
        if (!tool.isEnabled) add(SlotBlocker.ToolUnavailable(tool.reason.orEmpty()))
        when (caps.activity) {
            PrintActivity.BUSY -> add(SlotBlocker.PrinterBusy)
            PrintActivity.UNKNOWN -> if (caps.connected != false) add(SlotBlocker.CannotTell)
            PrintActivity.IDLE, PrintActivity.OFFLINE -> Unit
        }
        if (tray.hasRfidTag) add(SlotBlocker.RfidSpool(tray.trayId + 1, tray.label))
    }

    /** The sentence shown to the user for [blocker]. */
    fun message(blocker: SlotBlocker, printerName: String): String {
        val name = printerName.ifBlank { "The printer" }
        return when (blocker) {
            is SlotBlocker.ToolUnavailable -> blocker.reason
            SlotBlocker.PrinterBusy -> "$name is printing (or paused mid-print). Wait for it to finish before changing filament."
            SlotBlocker.CannotTell -> "Can't tell what $name is doing right now, so the change is not allowed."
            is SlotBlocker.RfidSpool ->
                "Slot ${blocker.slotNumber} holds a Bambu RFID spool (${blocker.label}). " +
                    "The printer reads its settings from the tag. Use it as it is, or take it out first."
        }
    }

    /** [allUnits] is every known AMS unit; it is used to find where the spool is currently recorded. */
    fun assess(spool: Spool, unit: AmsUnit, tray: AmsTray, allUnits: List<AmsUnit>): SlotLoadAssessment {
        val warnings = buildList {
            val other = tray.loadedSpoolId
            if (other != null && other != spool.id) add(SlotWarning.ReplacesSpool(other))
            if (!tray.isEmpty && other == null) add(SlotWarning.OverwritesDetected(tray.label))
            if (!tray.isEmpty && materialsDiffer(tray.material, spool.material)) {
                add(SlotWarning.MaterialMismatch(tray.material, spool.material))
            }
            recordedElsewhere(spool, unit, tray, allUnits)?.let { add(SlotWarning.MovesFromOtherSlot(it)) }
            if (unit.isDrying) add(SlotWarning.UnitDrying)
            if (tray.isEmpty) add(SlotWarning.SlotLooksEmpty)
            val tool = ToolAvailability.statusOf(PrinterTool.SLOT_CHANGE, unit.capabilities, unit.printerName)
            if (tool.availability == Availability.LIMITED) add(SlotWarning.MayBeRefused(tool.reason.orEmpty()))
        }
        val summary = listOf(spool.material, spool.colorName).filter { it.isNotBlank() }.joinToString(", ")
        return SlotLoadAssessment(summary.ifBlank { spool.title }, warnings)
    }

    private fun materialsDiffer(slot: String, spool: String): Boolean {
        val a = normalise(slot)
        val b = normalise(spool)
        return a.isNotEmpty() && b.isNotEmpty() && a != b
    }

    private fun normalise(material: String) = material.lowercase().filter { it.isLetterOrDigit() }

    private fun recordedElsewhere(spool: Spool, unit: AmsUnit, tray: AmsTray, allUnits: List<AmsUnit>): String? {
        for (u in allUnits) {
            for (t in u.trays) {
                val sameSlot = u.printerId == unit.printerId && u.amsId == unit.amsId && t.trayId == tray.trayId
                if (t.loadedSpoolId == spool.id && !sameSlot) return "${u.printerName} · ${u.name} slot ${t.trayId + 1}"
            }
        }
        return null
    }
}
