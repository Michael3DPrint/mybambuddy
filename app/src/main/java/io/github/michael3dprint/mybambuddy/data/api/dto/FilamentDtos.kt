package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.github.michael3dprint.mybambuddy.data.api.ApiClient

/** Bambuddy's report of whether Spoolman is enabled and connected, and where it is. */
@JsonClass(generateAdapter = true)
data class SpoolmanStatusDto(
    @Json(name = "enabled") val enabled: Boolean? = null,
    @Json(name = "connected") val connected: Boolean? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "native_tags") val nativeTags: Boolean? = null,
)

/** Bambuddy's flat "InventorySpool" (same shape for /inventory/spools and /spoolman/inventory/spools). */
@JsonClass(generateAdapter = true)
data class InventorySpoolDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "material") val material: String? = null,
    @Json(name = "subtype") val subtype: String? = null,
    @Json(name = "brand") val brand: String? = null,
    @Json(name = "color_name") val colorName: String? = null,
    @Json(name = "rgba") val rgba: String? = null,
    @Json(name = "label_weight") val labelWeight: Int? = null,
    @Json(name = "core_weight") val coreWeight: Int? = null,
    @Json(name = "weight_used") val weightUsed: Double? = null,
    @Json(name = "nozzle_temp_min") val nozzleTempMin: Int? = null,
    @Json(name = "nozzle_temp_max") val nozzleTempMax: Int? = null,
    @Json(name = "note") val note: String? = null,
    @Json(name = "tag_uid") val tagUid: String? = null,
    @Json(name = "tray_uuid") val trayUuid: String? = null,
    @Json(name = "storage_location") val storageLocation: String? = null,
    @Json(name = "archived_at") val archivedAt: String? = null,
    @Json(name = "low_stock_threshold_pct") val lowStockThresholdPct: Int? = null,
)

/** Body for POST .../spools. Null fields are omitted so the server applies its own defaults. */
@JsonClass(generateAdapter = true)
data class SpoolCreateRequest(
    @Json(name = "material") val material: String,
    @Json(name = "subtype") val subtype: String? = null,
    @Json(name = "brand") val brand: String? = null,
    @Json(name = "color_name") val colorName: String? = null,
    @Json(name = "rgba") val rgba: String? = null,
    @Json(name = "label_weight") val labelWeight: Int? = null,
    @Json(name = "core_weight") val coreWeight: Int? = null,
    @Json(name = "note") val note: String? = null,
)

/** One product of SpoolmanDB (GET external/filament/search on the Spoolman server). */
@JsonClass(generateAdapter = true)
data class ExternalFilamentDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "manufacturer") val manufacturer: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "material") val material: String? = null,
    @Json(name = "weight") val weight: Double? = null,
    @Json(name = "spool_weight") val spoolWeight: Double? = null,
    @Json(name = "diameter") val diameter: Double? = null,
    @Json(name = "color_hex") val colorHex: String? = null,
    @Json(name = "extruder_temp") val extruderTemp: Int? = null,
    @Json(name = "bed_temp") val bedTemp: Int? = null,
    @Json(name = "finish") val finish: String? = null,
)

/** The part of POST inventory/assignments we read: whether Bambuddy configured the slot or is waiting for a spool. */
@JsonClass(generateAdapter = true)
data class SlotAssignResultDto(
    @Json(name = "configured") val configured: Boolean? = null,
    @Json(name = "pending_config") val pendingConfig: Boolean? = null,
) {
    companion object {
        /** Reads the reply, or returns null for an empty or unexpected body. The assignment itself already succeeded, so never fail on this. */
        private val adapter by lazy {
            ApiClient.createMoshi().adapter(SlotAssignResultDto::class.java).lenient()
        }

        /** Reads the assign reply leniently; null when the body is empty or not the expected shape. */
        fun parseOrNull(json: String): SlotAssignResultDto? = try {
            adapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }
}

/** GET inventory/assignments and spoolman/inventory/slot-assignments/all (both list the spool id + slot). */
@JsonClass(generateAdapter = true)
data class SlotAssignmentDto(
    @Json(name = "spool_id") val spoolId: Int? = null,
    @Json(name = "spoolman_spool_id") val spoolmanSpoolId: Int? = null,
    @Json(name = "printer_id") val printerId: Int? = null,
    @Json(name = "ams_id") val amsId: Int? = null,
    @Json(name = "tray_id") val trayId: Int? = null,
)

/** Body that assigns a Spoolman spool to an AMS slot. */
@JsonClass(generateAdapter = true)
data class SpoolSlotAssignRequest(
    @Json(name = "spoolman_spool_id") val spoolmanSpoolId: Int,
    @Json(name = "printer_id") val printerId: Int,
    @Json(name = "ams_id") val amsId: Int,
    @Json(name = "tray_id") val trayId: Int,
    @Json(name = "move") val move: Boolean = true,
)

/** Body that assigns a spool from Bambuddy's own inventory to an AMS slot. */
@JsonClass(generateAdapter = true)
data class InventoryAssignRequest(
    @Json(name = "spool_id") val spoolId: Int,
    @Json(name = "printer_id") val printerId: Int,
    @Json(name = "ams_id") val amsId: Int,
    @Json(name = "tray_id") val trayId: Int,
    @Json(name = "move") val move: Boolean = true,
)

/** Body that links an NFC tag UID to a spool. */
@JsonClass(generateAdapter = true)
data class SpoolTagLinkRequest(@Json(name = "tag_uid") val tagUid: String? = null)

/** Body that sets a spool's weight from a scale reading (spool included). */
@JsonClass(generateAdapter = true)
data class SpoolWeightRequest(@Json(name = "weight_grams") val weightGrams: Double)

/** One row of a spool's usage history (all fields optional: the shape is only partly verified against a live server). */
@JsonClass(generateAdapter = true)
data class SpoolUsageDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "print_name") val printName: String? = null,
    @Json(name = "weight_used") val weightUsed: Double? = null,
    @Json(name = "percent_used") val percentUsed: Int? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
)
