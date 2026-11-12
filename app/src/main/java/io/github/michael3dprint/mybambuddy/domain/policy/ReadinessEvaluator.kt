package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.PrintActivity
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.PrinterReadiness
import io.github.michael3dprint.mybambuddy.domain.model.ToolingMode

/**
 * Turns a printer's [PrinterCapabilities] into a [PrinterReadiness]: its [ToolingMode] and the state of each tool. Pure. The
 * Printers card, the Printer tools screen and the repository gates all use it (through [ToolAvailability]), so they cannot disagree.
 */
object ReadinessEvaluator {
    /** [caps] null means the status could not be read; the mode is then [ToolingMode.UNKNOWN] and no tool is marked as verified. */
    fun evaluate(printerId: String, printerName: String, caps: PrinterCapabilities?): PrinterReadiness {
        val known = caps ?: PrinterCapabilities.UNKNOWN
        val mode = modeOf(known)
        return PrinterReadiness(printerId, printerName, mode, known, ToolAvailability.evaluate(known, printerName))
    }

    /** FULL only when Developer LAN mode is reported on; anything less certain is never FULL. */
    fun modeOf(caps: PrinterCapabilities): ToolingMode = when {
        caps.connected == false || caps.activity == PrintActivity.OFFLINE -> ToolingMode.OFFLINE
        !caps.isKnown -> ToolingMode.UNKNOWN
        caps.developerMode == true -> ToolingMode.FULL
        caps.developerMode == false -> ToolingMode.LIMITED
        else -> ToolingMode.UNVERIFIED
    }
}
