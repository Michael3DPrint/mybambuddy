package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** The statistics reply. Every field is optional: a missing number counts as zero, unknown fields are ignored. */
@JsonClass(generateAdapter = true)
data class ArchiveStatsDto(
    @Json(name = "total_prints") val totalPrints: Int? = null,
    @Json(name = "successful_prints") val successfulPrints: Int? = null,
    @Json(name = "failed_prints") val failedPrints: Int? = null,
    @Json(name = "cancelled_prints") val cancelledPrints: Int? = null,
    @Json(name = "total_print_time_hours") val totalHours: Double? = null,
    @Json(name = "total_filament_grams") val totalGrams: Double? = null,
    @Json(name = "prints_by_filament_type") val byMaterial: Map<String, Double>? = null,
    @Json(name = "prints_by_printer") val byPrinter: Map<String, Double>? = null,
    @Json(name = "printer_names") val printerNames: Map<String, String>? = null,
)
