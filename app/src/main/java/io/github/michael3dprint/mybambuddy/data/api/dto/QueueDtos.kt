package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** One queue job as the server sends it. */
@JsonClass(generateAdapter = true)
data class QueueItemDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "printer_id") val printerId: Int? = null,
    @Json(name = "printer_name") val printerName: String? = null,
    @Json(name = "position") val position: Int? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "archive_name") val archiveName: String? = null,
    @Json(name = "library_file_name") val libraryFileName: String? = null,
    @Json(name = "waiting_reason") val waitingReason: String? = null,
    @Json(name = "filament_short") val filamentShort: Boolean? = null,
    @Json(name = "error_message") val errorMessage: String? = null,
    @Json(name = "library_file_id") val libraryFileId: Int? = null,
    @Json(name = "archive_id") val archiveId: Int? = null,
    @Json(name = "print_time_seconds") val printTimeSeconds: Int? = null,
    @Json(name = "filament_used_grams") val filamentUsedGrams: Double? = null,
    @Json(name = "filament_type") val filamentType: String? = null,
    @Json(name = "plate_id") val plateId: Int? = null,
    @Json(name = "ams_mapping") val amsMapping: List<Int>? = null,
    // Older Bambuddy versions sent these two as true or false, newer ones as "auto", "on" or "off"; both are read.
    @Json(name = "bed_levelling") val bedLevelling: Any? = null,
    @Json(name = "flow_cali") val flowCali: Any? = null,
    @Json(name = "vibration_cali") val vibrationCali: Boolean? = null,
    @Json(name = "layer_inspect") val layerInspect: Boolean? = null,
    @Json(name = "timelapse") val timelapse: Boolean? = null,
    @Json(name = "use_ams") val useAms: Boolean? = null,
)

/** One job and its new position in a reorder request. */
@JsonClass(generateAdapter = true)
data class QueueReorderItem(@Json(name = "id") val id: Int, @Json(name = "position") val position: Int)

/** Body of a queue reorder request; positions must be unique. */
@JsonClass(generateAdapter = true)
data class QueueReorderBody(@Json(name = "items") val items: List<QueueReorderItem>)

/** A change to a waiting job. Only the fields that changed are set; the server leaves the rest alone. */
@JsonClass(generateAdapter = true)
data class QueueUpdateBody(
    @Json(name = "printer_id") val printerId: Int? = null,
    @Json(name = "plate_id") val plateId: Int? = null,
    @Json(name = "ams_mapping") val amsMapping: List<Int>? = null,
    @Json(name = "bed_levelling") val bedLevelling: String? = null,
    @Json(name = "flow_cali") val flowCali: String? = null,
    @Json(name = "vibration_cali") val vibrationCali: Boolean? = null,
    @Json(name = "layer_inspect") val layerInspect: Boolean? = null,
    @Json(name = "timelapse") val timelapse: Boolean? = null,
    @Json(name = "use_ams") val useAms: Boolean? = null,
)
