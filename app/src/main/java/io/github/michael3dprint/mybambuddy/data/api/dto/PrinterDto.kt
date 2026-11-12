package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** One printer row as the server sends it. Every field is optional because the shape varies. */
@JsonClass(generateAdapter = true)
data class PrinterDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "model") val model: String? = null,
    @Json(name = "state") val state: String? = null,
    @Json(name = "status") val status: PrinterStatusDto? = null,
    @Json(name = "progress") val progress: Float? = null,
    @Json(name = "remaining_time") val remainingTime: Long? = null,
    @Json(name = "remaining_time_seconds") val remainingTimeSeconds: Long? = null,
    @Json(name = "current_layer") val currentLayer: Int? = null,
    @Json(name = "total_layers") val totalLayers: Int? = null,
    @Json(name = "snapshot_url") val snapshotUrl: String? = null,
    @Json(name = "camera_url") val cameraUrl: String? = null,
    @Json(name = "hms_status") val hmsStatus: List<HmsErrorDto>? = null,
    @Json(name = "hms_errors") val hmsErrors: List<HmsErrorDto>? = null,
    @Json(name = "print_name") val printName: String? = null,
    @Json(name = "subtask_name") val subtaskName: String? = null,
    @Json(name = "gcode_file") val gcodeFile: String? = null,
    @Json(name = "task_name") val taskName: String? = null,
    @Json(name = "temperatures") val temperatures: TemperaturesDto? = null,
) {
    /** remaining_time is in MINUTES on the Bambuddy server. */
    val effectiveRemainingTimeSeconds: Long?
        get() = remainingTimeSeconds ?: remainingTime?.let { it * 60L }

    val effectivePrintName: String?
        get() = printName ?: subtaskName ?: gcodeFile ?: taskName ?: status?.effectivePrintName
}
