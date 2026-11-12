package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.ArchivesResponseDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Print history (the server calls it "archives"). */
interface HistoryApi {
    /** Trailing slash is required by the server. */
    @GET("archives/")
    suspend fun getArchives(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("printer_id") printerId: Int? = null,
    ): Response<ArchivesResponseDto>

    /** The picture of one archived print, as image bytes. */
    @GET("archives/{id}/thumbnail")
    suspend fun thumbnail(@Path("id") id: Int): Response<ResponseBody>
}
