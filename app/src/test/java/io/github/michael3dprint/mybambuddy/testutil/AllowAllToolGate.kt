package io.github.michael3dprint.mybambuddy.testutil

import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterToolGate

/** A gate that never refuses, for tests that are not about gating. */
object AllowAllToolGate : PrinterToolGate {
    override suspend fun refusal(printerId: String, tool: PrinterTool, printerName: String): String? = null
}
