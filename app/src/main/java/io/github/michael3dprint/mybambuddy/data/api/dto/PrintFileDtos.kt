package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Reply to a library upload; [metadata] is the server's reading of the file and may hold the printer model it was sliced for. */
@JsonClass(generateAdapter = true)
data class LibraryUploadDto(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "filename") val filename: String? = null,
    @Json(name = "duplicate_of") val duplicateOf: Int? = null,
    @Json(name = "metadata") val metadata: Map<String, Any?>? = null,
)

/** One filament a plate uses, from the plates reply. */
@JsonClass(generateAdapter = true)
data class PlateFilamentDto(
    @Json(name = "slot_id") val slotId: Int? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "color") val color: String? = null,
    @Json(name = "used_grams") val usedGrams: Double? = null,
)

/** One plate with the slicer's estimates. */
@JsonClass(generateAdapter = true)
data class PlateDto(
    @Json(name = "index") val index: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "print_time_seconds") val printTimeSeconds: Int? = null,
    @Json(name = "filament_used_grams") val filamentUsedGrams: Double? = null,
    @Json(name = "filaments") val filaments: List<PlateFilamentDto>? = null,
    @Json(name = "has_thumbnail") val hasThumbnail: Boolean? = null,
)

/** The plates of a library file; [embeddedPrinter] is the printer preset the file was sliced with, when it carries one. */
@JsonClass(generateAdapter = true)
data class PlatesDto(
    @Json(name = "plates") val plates: List<PlateDto>? = null,
    @Json(name = "embedded_printer") val embeddedPrinter: String? = null,
)

/** Body that creates a queue item from a library file. Always staged: [manualStart] is true so it never starts by itself. */
@JsonClass(generateAdapter = true)
data class QueueCreateBody(
    @Json(name = "printer_id") val printerId: Int,
    @Json(name = "library_file_id") val libraryFileId: Int? = null,
    @Json(name = "archive_id") val archiveId: Int? = null,
    @Json(name = "plate_id") val plateId: Int,
    @Json(name = "ams_mapping") val amsMapping: List<Int>,
    @Json(name = "manual_start") val manualStart: Boolean,
    @Json(name = "cleanup_library_after_dispatch") val cleanupLibraryAfterDispatch: Boolean,
    @Json(name = "insert_position") val insertPosition: Int? = null,
    @Json(name = "bed_levelling") val bedLevelling: String? = null,
    @Json(name = "flow_cali") val flowCali: String? = null,
    @Json(name = "vibration_cali") val vibrationCali: Boolean? = null,
    @Json(name = "layer_inspect") val layerInspect: Boolean? = null,
    @Json(name = "timelapse") val timelapse: Boolean? = null,
    @Json(name = "use_ams") val useAms: Boolean? = null,
)

/** MakerWorld sign-in state of the server. */
@JsonClass(generateAdapter = true)
data class MakerWorldStatusDto(
    @Json(name = "has_cloud_token") val hasCloudToken: Boolean? = null,
    @Json(name = "can_download") val canDownload: Boolean? = null,
    @Json(name = "sign_in_expired") val signInExpired: Boolean? = null,
)

/** Body of a MakerWorld link lookup. */
@JsonClass(generateAdapter = true)
data class MakerWorldResolveBody(@Json(name = "url") val url: String)

/** What a link resolved to; the design and instances are passed through by the server as free-form objects. */
@JsonClass(generateAdapter = true)
data class MakerWorldResolvedDto(
    @Json(name = "model_id") val modelId: Int? = null,
    @Json(name = "design") val design: Map<String, Any?>? = null,
    @Json(name = "instances") val instances: List<Map<String, Any?>>? = null,
    @Json(name = "already_imported_library_ids") val alreadyImported: List<Int>? = null,
)

/** Body of a MakerWorld import: the model and, when chosen, the plate configuration. */
@JsonClass(generateAdapter = true)
data class MakerWorldImportBody(@Json(name = "model_id") val modelId: Int, @Json(name = "profile_id") val profileId: Int?)

/** Reply to a MakerWorld import. */
@JsonClass(generateAdapter = true)
data class MakerWorldImportedDto(
    @Json(name = "library_file_id") val libraryFileId: Int? = null,
    @Json(name = "filename") val filename: String? = null,
)

/** One project filament slot from the filament-requirements reply. */
@JsonClass(generateAdapter = true)
data class RequirementDto(
    @Json(name = "slot_id") val slotId: Int? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "color") val color: String? = null,
    @Json(name = "used_in_plate") val usedInPlate: Boolean? = null,
)

/** The filament-requirements reply: every project slot when asked for the full list. */
@JsonClass(generateAdapter = true)
data class RequirementsDto(@Json(name = "filaments") val filaments: List<RequirementDto>? = null)

/** A preset reference in a slice request. */
@JsonClass(generateAdapter = true)
data class PresetRefDto(@Json(name = "source") val source: String, @Json(name = "id") val id: String)

/** Body that starts a slice. Always asks for a 3MF with the G-code inside, and sends a colour for every slot. */
@JsonClass(generateAdapter = true)
data class SliceBody(
    @Json(name = "printer_preset") val printerPreset: PresetRefDto,
    @Json(name = "process_preset") val processPreset: PresetRefDto,
    @Json(name = "filament_presets") val filamentPresets: List<PresetRefDto>,
    @Json(name = "filament_colours") val filamentColours: List<String>,
    @Json(name = "plate") val plate: Int,
    @Json(name = "bed_type") val bedType: String,
    @Json(name = "auto_orient") val autoOrient: Boolean,
    @Json(name = "auto_arrange") val autoArrange: Boolean,
    @Json(name = "use_embedded_settings") val useEmbeddedSettings: Boolean,
    @Json(name = "export_3mf") val export3mf: Boolean,
)

/** Reply to starting a slice: the job to poll. */
@JsonClass(generateAdapter = true)
data class SliceStartedDto(@Json(name = "job_id") val jobId: Int? = null)

/** A slice job's status; the progress and result objects are free-form. */
@JsonClass(generateAdapter = true)
data class SliceJobDto(
    @Json(name = "status") val status: String? = null,
    @Json(name = "progress") val progress: Map<String, Any?>? = null,
    @Json(name = "result") val result: Map<String, Any?>? = null,
    @Json(name = "error_detail") val errorDetail: String? = null,
)
