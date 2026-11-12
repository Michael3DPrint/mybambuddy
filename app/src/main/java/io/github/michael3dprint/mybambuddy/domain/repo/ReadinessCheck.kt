package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.PrinterReadiness

/**
 * The validation API: asks Bambuddy for each printer's current state and says which tools can be used. Read-only (GET requests
 * only), never sends a command. A status that cannot be read is an error or an UNKNOWN printer, never an assumed "full".
 */
interface ReadinessCheck {
    /** Every printer Bambuddy knows. A printer whose status cannot be read comes back as UNKNOWN; the call fails only if the list cannot be read. */
    suspend fun checkAll(): ApiResult<List<PrinterReadiness>>

    /** One printer, from a fresh status read. [printerName] only words the messages. */
    suspend fun check(printerId: String, printerName: String = ""): ApiResult<PrinterReadiness>
}
