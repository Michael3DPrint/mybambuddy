package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** One check in a diagnostics reply. Every field is optional; `params` is not read. */
@JsonClass(generateAdapter = true)
data class DiagnosticCheckDto(@Json(name = "id") val id: String? = null, @Json(name = "status") val status: String? = null)

/** The diagnostics reply (`PrinterDiagnosticResult`). The printer's `ip_address` is not read. */
@JsonClass(generateAdapter = true)
data class PrinterDiagnosticDto(
    @Json(name = "overall") val overall: String? = null,
    @Json(name = "checks") val checks: List<DiagnosticCheckDto>? = null,
)
