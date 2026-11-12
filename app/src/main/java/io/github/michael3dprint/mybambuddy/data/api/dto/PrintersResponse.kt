package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** The printers reply; the list may arrive under `printers`, `data` or `items`. */
@JsonClass(generateAdapter = true)
data class PrintersResponse(
    @Json(name = "printers") val printers: List<PrinterDto>? = null,
    @Json(name = "data") val data: List<PrinterDto>? = null,
    @Json(name = "items") val items: List<PrinterDto>? = null,
    @Json(name = "count") val count: Int? = null,
) {
    val effectivePrinters: List<PrinterDto>
        get() = printers ?: data ?: items ?: emptyList()
}
