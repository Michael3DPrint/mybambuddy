package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterToolGate
import io.github.michael3dprint.mybambuddy.domain.repo.ReadinessCheck

/**
 * Re-reads the printer's state through [ReadinessCheck] right before a command that needs Developer LAN mode and refuses it,
 * with the reason, when the tool is off (mode off, printer offline, no drying support, and so on). Tools that do not need the mode
 * cost no request. If the state cannot be read the command is allowed ("fails open"): it would fail with its own error, and
 * blocking on a status hiccup could stop a pause or cancel when it matters.
 */
class DeveloperModeGuard(private val readiness: ReadinessCheck) : PrinterToolGate {
    override suspend fun refusal(printerId: String, tool: PrinterTool, printerName: String): String? {
        if (!tool.needsDeveloperMode) return null
        val report = (readiness.check(printerId, printerName) as? ApiResult.Success)?.data ?: return null
        val status = report.statusOf(tool) ?: return null
        return if (status.isEnabled) null else status.reason
    }
}
