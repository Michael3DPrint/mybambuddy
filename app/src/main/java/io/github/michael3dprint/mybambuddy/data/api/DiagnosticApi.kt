package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDiagnosticDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** Bambuddy's connection diagnostics. The server probes the printer and may wait several seconds, so use a client with long timeouts. */
interface DiagnosticApi {
    /** Runs the server's connection diagnostics for one printer. Read-only; may take up to about 15 seconds. */
    @GET("printers/{id}/diagnostic")
    suspend fun getDiagnostic(@Path("id") printerId: String): Response<PrinterDiagnosticDto>
}
