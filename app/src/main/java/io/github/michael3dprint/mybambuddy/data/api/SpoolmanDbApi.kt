package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.ExternalFilamentDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** Read-only calls straight to the Spoolman server (its address comes from Bambuddy's /spoolman/status). */
interface SpoolmanDbApi {
    /** SpoolmanDB search; the query is matched word by word against manufacturer, name and material. */
    @GET("external/filament/search")
    suspend fun searchFilaments(
        @Query("query") query: String,
        @Query("limit") limit: Int = 40,
        @Query("offset") offset: Int = 0,
    ): Response<List<ExternalFilamentDto>>
}
