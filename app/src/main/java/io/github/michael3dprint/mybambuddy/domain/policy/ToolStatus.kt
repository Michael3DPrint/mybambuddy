package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool

/** Whether one tool can be used on one printer right now, and why not (the reason always says what to do about it). */
data class ToolStatus(val tool: PrinterTool, val availability: Availability, val reason: String? = null) {
    /** True unless the tool is unavailable. A limited tool stays enabled. */
    val isEnabled: Boolean get() = availability != Availability.UNAVAILABLE
}
