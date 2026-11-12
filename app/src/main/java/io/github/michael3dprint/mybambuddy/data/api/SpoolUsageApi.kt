package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.SpoolUsageDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** A spool's usage history, from Spoolman or from Bambuddy's own inventory. */
interface SpoolUsageApi {
    /** A spool's usage history from Spoolman. */
    @GET("spoolman/inventory/spools/{id}/usage")
    suspend fun getSpoolmanUsage(@Path("id") id: Int): Response<List<SpoolUsageDto>>

    /** A spool's usage history from Bambuddy's own inventory. */
    @GET("inventory/spools/{id}/usage")
    suspend fun getInventoryUsage(@Path("id") id: Int): Response<List<SpoolUsageDto>>
}
