package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.PrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import kotlinx.coroutines.flow.Flow

/** Printers and their status: a polled stream and one-off reads. */
interface PrinterRepository {
    /** The printers as a stream, one result per poll every [pollIntervalMs]. */
    fun getPrintersFlow(pollIntervalMs: Long = 5000L): Flow<ApiResult<List<Printer>>>

    /** All printers, read once. */
    suspend fun fetchPrinters(): ApiResult<List<Printer>>

    /** One printer, read once. */
    suspend fun fetchPrinter(printerId: String): ApiResult<Printer>

    /** The live status of one printer, read once. Never served from a saved copy. */
    suspend fun fetchPrinterStatus(printerId: String): ApiResult<PrintStatus>
}
