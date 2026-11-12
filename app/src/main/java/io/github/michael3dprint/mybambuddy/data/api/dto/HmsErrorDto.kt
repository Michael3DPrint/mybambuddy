package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** One HMS error as the server sends it. Every field is optional. */
@JsonClass(generateAdapter = true)
data class HmsErrorDto(
    @Json(name = "code") val code: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "severity") val severity: String? = null,
    @Json(name = "attr") val attr: Long? = null,
    @Json(name = "module") val module: Int? = null,
    @Json(name = "full_code") val fullCode: String? = null,
    @Json(name = "actions") val actions: List<String>? = null,
)
