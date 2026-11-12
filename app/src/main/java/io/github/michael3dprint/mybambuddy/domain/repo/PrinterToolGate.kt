package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool

/**
 * Asks "may this tool be used on this printer right now?" before a command is sent. Implementations must be conservative about
 * what they refuse: return null (allow) when the answer cannot be found, so a status hiccup never blocks a command that
 * would have worked. The slot-change preflight is stricter and does not use this gate.
 */
interface PrinterToolGate {
    /** A user-facing refusal that says why and what to do, or null when the tool may be used. */
    suspend fun refusal(printerId: String, tool: PrinterTool, printerName: String = ""): String?
}
