package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** One archive row as the server sends it. Every field is optional because the shape varies. */
@JsonClass(generateAdapter = true)
data class ArchiveItemDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "filename") val filename: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "gcode_file") val gcodeFile: String? = null,
    @Json(name = "task_name") val taskName: String? = null,
    @Json(name = "printer") val printer: String? = null,
    @Json(name = "printer_name") val printerName: String? = null,
    @Json(name = "printer_id") val printerId: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "start_time") val startTime: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "date") val date: String? = null,
    @Json(name = "timestamp") val timestamp: Long? = null,
    @Json(name = "duration_seconds") val durationSeconds: Long? = null,
    @Json(name = "duration") val duration: Long? = null,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "thumbnail") val thumbnail: String? = null,
    @Json(name = "print_name") val printName: String? = null,
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "completed_at") val completedAt: String? = null,
    @Json(name = "actual_time_seconds") val actualTimeSeconds: Long? = null,
    @Json(name = "print_time_seconds") val printTimeSeconds: Long? = null,
    @Json(name = "thumbnail_path") val thumbnailPath: String? = null,
) {
    val effectiveName: String
        get() = printName ?: name ?: filename ?: title ?: gcodeFile ?: taskName ?: "Print Job"

    val effectivePrinterName: String
        get() = printerName ?: printer ?: printerId ?: "Printer"

    val effectiveStatus: String
        get() = status ?: "unknown"

    val effectiveDate: String?
        get() = startedAt ?: startTime ?: createdAt ?: date ?: timestamp?.toString()

    val effectiveDurationSeconds: Long?
        get() = actualTimeSeconds ?: durationSeconds ?: duration ?: printTimeSeconds

    val effectiveThumbnailUrl: String?
        get() = thumbnailUrl ?: thumbnail
            // The host part is replaced by DynamicUrlInterceptor with the configured server.
            ?: if (thumbnailPath != null && id != null) "http://localhost/api/v1/archives/$id/thumbnail" else null
}
