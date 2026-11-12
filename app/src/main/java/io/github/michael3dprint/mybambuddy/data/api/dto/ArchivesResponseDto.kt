package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** The archive list reply; the list may arrive under `archives`, `items` or `data`. */
@JsonClass(generateAdapter = true)
data class ArchivesResponseDto(
    @Json(name = "archives") val archives: List<ArchiveItemDto>? = null,
    @Json(name = "items") val items: List<ArchiveItemDto>? = null,
    @Json(name = "data") val data: List<ArchiveItemDto>? = null,
    @Json(name = "total") val total: Int? = null,
    @Json(name = "count") val count: Int? = null,
    @Json(name = "offset") val offset: Int? = null,
    @Json(name = "limit") val limit: Int? = null,
) {
    val effectiveItems: List<ArchiveItemDto>
        get() = archives ?: items ?: data ?: emptyList()
}
