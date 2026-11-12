package io.github.michael3dprint.mybambuddy.domain.model

import io.github.michael3dprint.mybambuddy.domain.policy.ToolStatus

/** One printer's tooling: the [mode] it gets and the state of every [PrinterTool]. */
data class PrinterReadiness(
    val printerId: String,
    val printerName: String,
    val mode: ToolingMode,
    val capabilities: PrinterCapabilities,
    val tools: List<ToolStatus>,
) {
    /** The tools that are switched off, with reasons. */
    val unavailable: List<ToolStatus> get() = tools.filter { !it.isEnabled }

    fun statusOf(tool: PrinterTool): ToolStatus? = tools.firstOrNull { it.tool == tool }
}
