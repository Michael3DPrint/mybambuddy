package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.ArchiveStatsDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** Print statistics (the server keeps them with the archives). */
interface StatsApi {
    /** Totals for the days between [dateFrom] and [dateTo] (YYYY-MM-DD, inclusive), counted as calendar days in [timeZone]. */
    @GET("archives/stats")
    suspend fun getStats(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("tz") timeZone: String? = null,
    ): Response<ArchiveStatsDto>
}
