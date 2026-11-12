package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.PrinterControlCommand

/** Pause, resume, cancel and status refresh for one printer. Control calls are single attempts, never retried or cached. */
interface PrintControlRepository {
    /** Pauses the running print. A control call: a single attempt, never retried. */
    suspend fun pausePrint(printerId: String): ApiResult<Unit>

    /** Resumes a paused print. A single attempt, never retried. */
    suspend fun resumePrint(printerId: String): ApiResult<Unit>

    /** Cancels the running print. A single attempt, never retried. */
    suspend fun cancelPrint(printerId: String): ApiResult<Unit>

    /** Asks Bambuddy to request a fresh status from the printer. */
    suspend fun refreshStatus(printerId: String): ApiResult<Unit>

    /** Clears the printer's HMS errors. A single attempt, never retried. */
    suspend fun clearHms(printerId: String): ApiResult<Unit>

    /** Marks the build plate as cleared in Bambuddy. Nothing is sent to the printer. */
    suspend fun clearPlate(printerId: String): ApiResult<Unit>

    /** Sets the print speed: 1 silent, 2 standard, 3 sport, 4 ludicrous. A single attempt, never retried. */
    suspend fun setPrintSpeed(printerId: String, mode: Int): ApiResult<Unit>

    /** Feeds filament from the AMS slot [trayId] into the printer. Moves filament; a single attempt, never retried. */
    suspend fun amsLoad(printerId: String, trayId: Int): ApiResult<Unit> = notSupported("Loading filament")

    /** Unloads the filament fed from the AMS slot [trayId]. Moves filament; a single attempt, never retried. */
    suspend fun amsUnload(printerId: String, trayId: Int): ApiResult<Unit> = notSupported("Unloading filament")

    /** Single entry point for every control action. Default maps to the individual methods above. */
    suspend fun executeCommand(printerId: String, command: PrinterControlCommand): ApiResult<Unit> = when (command) {
        PrinterControlCommand.Pause -> pausePrint(printerId)
        PrinterControlCommand.Resume -> resumePrint(printerId)
        PrinterControlCommand.Cancel -> cancelPrint(printerId)
        PrinterControlCommand.RefreshStatus -> refreshStatus(printerId)
        PrinterControlCommand.ClearHms -> clearHms(printerId)
        PrinterControlCommand.ClearPlate -> clearPlate(printerId)
        is PrinterControlCommand.SetPrintSpeed -> setPrintSpeed(printerId, command.mode)
        is PrinterControlCommand.SetChamberLight ->
            ApiResult.Error(
                message = "The chamber light is not supported here",
                errorType = ApiErrorType.UNSUPPORTED,
            )
        is PrinterControlCommand.AmsLoadFilament -> amsLoad(printerId, command.trayId)
        is PrinterControlCommand.AmsUnloadFilament -> amsUnload(printerId, command.trayId)
    }

    private fun notSupported(what: String): ApiResult<Unit> =
        ApiResult.Error(message = "$what is not supported here", errorType = ApiErrorType.UNSUPPORTED)
}
