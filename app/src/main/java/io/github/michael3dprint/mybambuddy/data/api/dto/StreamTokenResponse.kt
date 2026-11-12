package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** The short-lived token that authorises a camera stream. */
@JsonClass(generateAdapter = true)
data class StreamTokenResponse(@Json(name = "token") val token: String? = null)
