package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.PrinterControlApi
import io.github.michael3dprint.mybambuddy.data.api.safeUnitCall
import io.github.michael3dprint.mybambuddy.domain.model.PrinterControlCommand
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.repo.PrintControlRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterToolGate

/**
 * Sends control commands. Each command that needs Developer LAN mode is first checked with the [gate] (see [PrinterTool]); a refusal
 * is returned without anything being sent. Single attempt, never retried.
 */
class PrintControlRepositoryImpl(private val api: PrinterControlApi, private val gate: PrinterToolGate) : PrintControlRepository {

    override suspend fun pausePrint(printerId: String): ApiResult<Unit> = gated(printerId, PrinterTool.PAUSE_RESUME) {
        api.pausePrint(printerId)
    }

    override suspend fun resumePrint(printerId: String): ApiResult<Unit> = gated(printerId, PrinterTool.PAUSE_RESUME) {
        api.resumePrint(printerId)
    }

    override suspend fun cancelPrint(printerId: String): ApiResult<Unit> = gated(printerId, PrinterTool.CANCEL_PRINT) {
        api.cancelPrint(printerId)
    }

    override suspend fun refreshStatus(printerId: String): ApiResult<Unit> = gated(printerId, PrinterTool.REFRESH_STATUS) {
        api.refreshStatus(printerId)
    }

    override suspend fun clearHms(printerId: String): ApiResult<Unit> = gated(printerId, PrinterTool.CLEAR_ERRORS) {
        api.clearHms(printerId)
    }

    override suspend fun clearPlate(printerId: String): ApiResult<Unit> = gated(printerId, PrinterTool.CLEAR_PLATE) {
        api.clearPlate(printerId)
    }

    override suspend fun setPrintSpeed(printerId: String, mode: Int): ApiResult<Unit> = gated(printerId, PrinterTool.PRINT_SPEED) {
        api.setPrintSpeed(printerId, mode)
    }

    override suspend fun amsLoad(printerId: String, trayId: Int): ApiResult<Unit> = gated(printerId, PrinterTool.FEED_FILAMENT) {
        api.amsLoad(printerId, trayId)
    }

    override suspend fun amsUnload(printerId: String, trayId: Int): ApiResult<Unit> = gated(printerId, PrinterTool.FEED_FILAMENT) {
        api.amsUnload(printerId, trayId)
    }

    override suspend fun executeCommand(printerId: String, command: PrinterControlCommand): ApiResult<Unit> = when (command) {
        is PrinterControlCommand.SetPrintSpeed ->
            if (command.mode in 1..4) {
                setPrintSpeed(printerId, command.mode)
            } else {
                unsupported("Speed mode must be 1-4")
            }
        is PrinterControlCommand.SetChamberLight -> gated(printerId, PrinterTool.CHAMBER_LIGHT) {
            api.setChamberLight(printerId, command.on)
        }
        else -> super.executeCommand(printerId, command)
    }

    private suspend fun gated(printerId: String, tool: PrinterTool, call: suspend () -> retrofit2.Response<Unit>): ApiResult<Unit> =
        gate.refusal(printerId, tool)?.let { unsupported(it) } ?: safeUnitCall(call)

    private fun unsupported(msg: String) = ApiResult.Error(message = msg, errorType = ApiErrorType.UNSUPPORTED)
}
