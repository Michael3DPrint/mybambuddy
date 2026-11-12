package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterStatusDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrintersResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** Read-only printer data: the list, one printer, and its live status. Control calls live in [PrinterControlApi]. */
interface PrinterApi {
    /** Trailing slash is required by the server. */
    @GET("printers/")
    suspend fun getPrinters(): Response<PrintersResponse>

    /** One printer's row. */
    @GET("printers/{id}")
    suspend fun getPrinter(@Path("id") id: String): Response<PrinterDto>

    /** One printer's live status. */
    @GET("printers/{id}/status")
    suspend fun getPrinterStatus(@Path("id") id: String): Response<PrinterStatusDto>
}
