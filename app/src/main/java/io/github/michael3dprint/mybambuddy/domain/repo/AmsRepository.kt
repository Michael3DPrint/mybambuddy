package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.SlotLoadResult

/** AMS units: what is loaded where, humidity, and drying. */
interface AmsRepository {
    /** Every AMS unit of every printer, with humidity, drying state and slots. */
    suspend fun getAms(): ApiResult<List<AmsUnit>>

    /**
     * One slot as the printer reports it right now, from a single status request for that printer (not the whole AMS list).
     * Always live, never from the saved copy, because it is used to confirm a change. Success with null means the slot is not there.
     */
    suspend fun getTray(printerId: Int, amsId: Int, trayId: Int): ApiResult<AmsTray?>

    /**
     * Assigns a spool to a slot. Refused before any assignment request is sent when the printer is printing, offline, in
     * Developer LAN mode off, or holds an RFID spool in that slot ([io.github.michael3dprint.mybambuddy.domain.policy.SlotLoadPolicy.preflight]).
     * A success means Bambuddy saved the assignment; see [SlotLoadResult].
     */
    suspend fun loadSpool(
        spoolId: Int,
        printerId: Int,
        amsId: Int,
        trayId: Int,
        printerName: String = GENERIC_PRINTER_NAME,
    ): ApiResult<SlotLoadResult>

    /** Clears the record of which spool is in a slot. Nothing is sent to the printer. */
    suspend fun unloadSpool(spoolId: Int, printerId: Int, amsId: Int, trayId: Int): ApiResult<Unit>

    /** Starts drying on one AMS unit at [tempC] for [hours] hours. A control call: a single attempt, never retried. */
    suspend fun startDrying(printerId: Int, amsId: Int, tempC: Int, hours: Int, filament: String): ApiResult<Unit>

    /** Stops drying on one AMS unit. A single attempt, never retried. */
    suspend fun stopDrying(printerId: Int, amsId: Int): ApiResult<Unit>

    companion object {
        /** Used in refusal text when the caller has no printer name. */
        const val GENERIC_PRINTER_NAME = "The printer"
    }
}
