package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** One AMS slot as the printer reports it. */
@JsonClass(generateAdapter = true)
data class AmsTrayDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "tray_type") val trayType: String? = null,
    @Json(name = "tray_sub_brands") val subBrands: String? = null,
    @Json(name = "tray_color") val color: String? = null,
    @Json(name = "remain") val remain: Int? = null,
    @Json(name = "tag_uid") val tagUid: String? = null,
    @Json(name = "tray_uuid") val trayUuid: String? = null,
)

/** One AMS unit as the printer reports it: humidity, temperature, drying state and its slots. */
@JsonClass(generateAdapter = true)
data class AmsUnitDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "humidity") val humidity: Int? = null,
    @Json(name = "temp") val temp: Double? = null,
    @Json(name = "is_ams_ht") val isAmsHt: Boolean? = null,
    @Json(name = "tray") val tray: List<AmsTrayDto>? = null,
    @Json(name = "dry_time") val dryTime: Int? = null,
    @Json(name = "dry_status") val dryStatus: Int? = null,
    @Json(name = "dry_target_temp") val dryTargetTemp: Int? = null,
    @Json(name = "dry_filament") val dryFilament: String? = null,
)

/** Only the part of GET printers/{id}/status this feature needs. */
@JsonClass(generateAdapter = true)
data class PrinterAmsStatusDto(
    @Json(name = "ams") val ams: List<AmsUnitDto>? = null,
    @Json(name = "supports_drying") val supportsDrying: Boolean? = null,
    @Json(name = "connected") val connected: Boolean? = null,
    @Json(name = "state") val state: String? = null,
    @Json(name = "developer_mode") val developerMode: Boolean? = null,
    @Json(name = "ams_exists") val amsExists: Boolean? = null,
    @Json(name = "supports_drying_while_printing") val supportsDryingWhilePrinting: Boolean? = null,
    @Json(name = "drying_screen_only") val dryingScreenOnly: Boolean? = null,
    @Json(name = "supports_chamber_heater") val supportsChamberHeater: Boolean? = null,
)
