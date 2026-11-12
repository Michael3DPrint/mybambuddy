package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.PrintActivity
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool

/**
 * Decides which tools are usable from a printer's [PrinterCapabilities]. Pure. The same function drives the Tools card,
 * the disabled buttons and the repository guards, so the screen and the safety checks cannot disagree.
 */
object ToolAvailability {
    /** What to tell the user to do when Developer LAN mode is off. */
    const val DEVELOPER_MODE_FIX = "Turn on Developer LAN mode in the printer's network settings."

    /** Whether [tool] can be used on a printer with [caps]; the order of checks is offline, developer mode, AMS, drying, busy printing, then unknown developer mode. */
    fun statusOf(tool: PrinterTool, caps: PrinterCapabilities, printerName: String = "The printer"): ToolStatus {
        val name = printerName.ifBlank { "The printer" }
        if (caps.connected == false || caps.activity == PrintActivity.OFFLINE) {
            return if (tool.needsDeveloperMode || tool.needsAms) {
                ToolStatus(tool, Availability.UNAVAILABLE, "$name is offline.")
            } else {
                ToolStatus(tool, Availability.AVAILABLE)
            }
        }
        if (tool.needsDeveloperMode && caps.developerMode == false) {
            return ToolStatus(tool, Availability.UNAVAILABLE, "$name has Developer LAN mode off, so it rejects this. $DEVELOPER_MODE_FIX")
        }
        if (tool.needsAms && caps.hasAms == false) {
            return ToolStatus(tool, Availability.UNAVAILABLE, "$name has no AMS.")
        }
        if (tool.needsDrying && caps.supportsDrying == false) {
            val why = if (caps.dryingScreenOnly == true) "can only dry from the printer's screen" else "does not support drying"
            return ToolStatus(tool, Availability.UNAVAILABLE, "$name's AMS $why.")
        }
        if (tool.needsDrying && caps.activity == PrintActivity.BUSY && caps.dryingWhilePrinting == false) {
            return ToolStatus(tool, Availability.UNAVAILABLE, "$name can't dry while a print is running. Wait for the print to finish.")
        }
        if (tool.needsIdle && caps.activity == PrintActivity.BUSY) {
            return ToolStatus(tool, Availability.UNAVAILABLE, "$name is printing. Wait for the print to finish.")
        }
        if (tool.needsDeveloperMode && caps.developerMode == null) {
            return ToolStatus(tool, Availability.LIMITED, "$name does not report Developer LAN mode; this may be refused.")
        }
        return ToolStatus(tool, Availability.AVAILABLE)
    }

    /** One [ToolStatus] per [PrinterTool], in declaration order. */
    fun evaluate(caps: PrinterCapabilities, printerName: String = "The printer"): List<ToolStatus> =
        PrinterTool.entries.map { statusOf(it, caps, printerName) }
}
