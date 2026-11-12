package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.HealthResponse
import io.github.michael3dprint.mybambuddy.data.api.dto.SystemInfoResponse
import retrofit2.Response
import retrofit2.http.GET

/** Server-level calls: liveness and version. The connection test uses `printers/` and [getSystemInfo]; see [getHealth]. */
interface SystemApi {
    /**
     * Liveness probe. Not used by any screen today: it is kept on purpose, with its lenient parser and tests, for the optional server
     * diagnostics check and manual use.
     */
    @GET("health")
    suspend fun getHealth(): Response<HealthResponse>

    /** The server's version and system information. */
    @GET("system/info")
    suspend fun getSystemInfo(): Response<SystemInfoResponse>
}
