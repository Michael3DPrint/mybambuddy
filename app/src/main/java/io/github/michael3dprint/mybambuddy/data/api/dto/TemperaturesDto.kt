package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Temperatures as the server sends them, with targets. Every field is optional. */
@JsonClass(generateAdapter = true)
data class TemperaturesDto(
    @Json(name = "nozzle") val nozzle: Float? = null,
    @Json(name = "nozzle_target") val nozzleTarget: Float? = null,
    @Json(name = "bed") val bed: Float? = null,
    @Json(name = "bed_target") val bedTarget: Float? = null,
    @Json(name = "chamber") val chamber: Float? = null,
    @Json(name = "chamber_target") val chamberTarget: Float? = null,
)
