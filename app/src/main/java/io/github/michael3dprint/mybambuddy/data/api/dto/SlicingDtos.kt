package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** The slicing fields of Bambuddy's settings; every other setting in the reply is ignored. */
@JsonClass(generateAdapter = true)
data class SlicerSettingsDto(
    @Json(name = "use_slicer_api") val useSlicerApi: Boolean? = null,
    @Json(name = "preferred_slicer") val preferredSlicer: String? = null,
    @Json(name = "orcaslicer_api_url") val orcaslicerApiUrl: String? = null,
    @Json(name = "bambu_studio_api_url") val bambuStudioApiUrl: String? = null,
)

/** One preset; only its name is used. */
@JsonClass(generateAdapter = true)
data class PresetItemDto(
    @Json(name = "name") val name: String? = null,
    @Json(name = "id") val id: String? = null,
    @Json(name = "source") val source: String? = null,
    @Json(name = "filament_type") val filamentType: String? = null,
    @Json(name = "compatible_printers") val compatiblePrinters: List<String>? = null,
)

/** The presets of one tier, by kind. */
@JsonClass(generateAdapter = true)
data class PresetTierDto(
    @Json(name = "printer") val printer: List<PresetItemDto>? = null,
    @Json(name = "process") val process: List<PresetItemDto>? = null,
    @Json(name = "filament") val filament: List<PresetItemDto>? = null,
)

/** The reply of the presets route: tiers plus the Bambu Cloud sign-in state. */
@JsonClass(generateAdapter = true)
data class SlicerPresetsDto(
    @Json(name = "standard") val standard: PresetTierDto? = null,
    @Json(name = "cloud") val cloud: PresetTierDto? = null,
    @Json(name = "local") val local: PresetTierDto? = null,
    @Json(name = "orca_cloud") val orcaCloud: PresetTierDto? = null,
    @Json(name = "cloud_status") val cloudStatus: String? = null,
)
