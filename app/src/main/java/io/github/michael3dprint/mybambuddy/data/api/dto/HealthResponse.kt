package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** The health reply in whichever shape the server sent it (see [io.github.michael3dprint.mybambuddy.data.api.HealthResponseAdapter]). */
@JsonClass(generateAdapter = true)
data class HealthResponse(
    @Json(name = "status") val status: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "ok") val ok: Boolean? = null,
)
