package io.github.michael3dprint.mybambuddy.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** The system-info reply in whichever shape the server sent it; the version may sit in more than one place. */
@JsonClass(generateAdapter = true)
data class SystemInfoResponse(
    @Json(name = "version") val version: String? = null,
    @Json(name = "system") val system: Any? = null,
    @Json(name = "appName") val appName: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "uptime") val uptime: Long? = null,
    @Json(name = "app") val app: AppInfoDto? = null,
) {
    val effectiveVersion: String? get() = app?.version ?: version
}

/** The app section of the system-info reply. */
@JsonClass(generateAdapter = true)
data class AppInfoDto(@Json(name = "version") val version: String? = null)
