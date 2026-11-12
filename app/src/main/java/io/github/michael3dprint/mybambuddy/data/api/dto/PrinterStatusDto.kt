package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * A printer's live status as the server sends it: connection, state, progress, temperatures, HMS errors and capability flags.
 * Every field is optional.
 */
@JsonClass(generateAdapter = true)
data class PrinterStatusDto(
    @Json(name = "connected") val connected: Boolean? = null,
    @Json(name = "state") val state: String? = null,
    @Json(name = "layer_num") val layerNum: Int? = null,
    @Json(name = "chamber_light") val chamberLight: Boolean? = null,
    @Json(name = "speed_level") val speedLevel: Int? = null,
    @Json(name = "awaiting_plate_clear") val awaitingPlateClear: Boolean? = null,
    @Json(name = "current_print") val currentPrint: String? = null,
    @Json(name = "progress") val progress: Float? = null,
    @Json(name = "remaining_time") val remainingTime: Long? = null,
    @Json(name = "remaining_time_seconds") val remainingTimeSeconds: Long? = null,
    @Json(name = "current_layer") val currentLayer: Int? = null,
    @Json(name = "total_layers") val totalLayers: Int? = null,
    @Json(name = "temperatures") val temperatures: TemperaturesDto? = null,
    @Json(name = "hms_status") val hmsStatus: List<HmsErrorDto>? = null,
    @Json(name = "hms_errors") val hmsErrors: List<HmsErrorDto>? = null,
    @Json(name = "hms") val hms: List<HmsErrorDto>? = null,
    @Json(name = "print_name") val printName: String? = null,
    @Json(name = "subtask_name") val subtaskName: String? = null,
    @Json(name = "gcode_file") val gcodeFile: String? = null,
    @Json(name = "task_name") val taskName: String? = null,
    @Json(name = "developer_mode") val developerMode: Boolean? = null,
    @Json(name = "ams_exists") val amsExists: Boolean? = null,
    @Json(name = "supports_drying") val supportsDrying: Boolean? = null,
    @Json(name = "supports_drying_while_printing") val supportsDryingWhilePrinting: Boolean? = null,
    @Json(name = "drying_screen_only") val dryingScreenOnly: Boolean? = null,
    @Json(name = "supports_chamber_heater") val supportsChamberHeater: Boolean? = null,
) {
    val effectiveState: String?
        get() = state

    /** Bambuddy sends remaining_time in MINUTES. remaining_time_seconds (if a server sends it) wins. */
    val effectiveRemainingTimeSeconds: Long?
        get() = remainingTimeSeconds ?: remainingTime?.let { it * 60L }

    val effectiveCurrentLayer: Int?
        get() = layerNum ?: currentLayer

    val allHmsErrors: List<HmsErrorDto>
        get() = hmsErrors ?: hmsStatus ?: hms ?: emptyList()

    val effectivePrintName: String?
        get() = (
            printName ?: subtaskName?.takeIf { it.isNotBlank() } ?: currentPrint?.takeIf { it.isNotBlank() }
                ?: gcodeFile?.takeIf { it.isNotBlank() } ?: taskName
            )
            ?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}
