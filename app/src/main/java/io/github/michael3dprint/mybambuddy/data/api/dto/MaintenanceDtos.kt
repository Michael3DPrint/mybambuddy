package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** One upkeep item as the server sends it, with hours or days until it is due. */
@JsonClass(generateAdapter = true)
data class MaintenanceItemDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "printer_id") val printerId: Int? = null,
    @Json(name = "maintenance_type_name") val name: String? = null,
    @Json(name = "enabled") val enabled: Boolean? = null,
    @Json(name = "interval_hours") val intervalHours: Double? = null,
    @Json(name = "interval_type") val intervalType: String? = null,
    @Json(name = "hours_since_maintenance") val hoursSince: Double? = null,
    @Json(name = "hours_until_due") val hoursUntilDue: Double? = null,
    @Json(name = "days_since_maintenance") val daysSince: Double? = null,
    @Json(name = "days_until_due") val daysUntilDue: Double? = null,
    @Json(name = "is_due") val isDue: Boolean? = null,
    @Json(name = "is_warning") val isWarning: Boolean? = null,
    @Json(name = "last_performed_at") val lastPerformedAt: String? = null,
)

/** One printer's upkeep items and total print hours as the server sends them. */
@JsonClass(generateAdapter = true)
data class PrinterMaintenanceDto(
    @Json(name = "printer_id") val printerId: Int? = null,
    @Json(name = "printer_name") val printerName: String? = null,
    @Json(name = "total_print_hours") val totalPrintHours: Double? = null,
    @Json(name = "maintenance_items") val items: List<MaintenanceItemDto>? = null,
    @Json(name = "due_count") val dueCount: Int? = null,
    @Json(name = "warning_count") val warningCount: Int? = null,
)

/** Body of POST maintenance/items/{id}/perform. The server wants an object even when there are no notes. */
@JsonClass(generateAdapter = true)
data class PerformMaintenanceBody(@Json(name = "notes") val notes: String? = null)
