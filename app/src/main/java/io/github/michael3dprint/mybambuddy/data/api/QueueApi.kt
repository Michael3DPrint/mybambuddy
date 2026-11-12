package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.QueueItemDto
import io.github.michael3dprint.mybambuddy.data.api.dto.QueueReorderBody
import io.github.michael3dprint.mybambuddy.data.api.dto.QueueUpdateBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Bambuddy print-queue routes. */
interface QueueApi {
    /** The print queue. */
    @GET("queue/")
    suspend fun getQueue(): Response<List<QueueItemDto>>

    /** Sets new positions for jobs; positions must be unique. */
    @POST("queue/reorder")
    suspend fun reorder(@Body body: QueueReorderBody): Response<Unit>

    /** Changes a waiting job in place; only the fields in [body] change. The job's file cannot be swapped this way. */
    @PATCH("queue/{id}")
    suspend fun update(@Path("id") id: Int, @Body body: QueueUpdateBody): Response<Unit>

    /** Starts a queued job now. */
    @POST("queue/{id}/start")
    suspend fun start(@Path("id") id: Int, @Query("skip_filament_check") skip: Boolean): Response<Unit>

    /** Cancels a job. */
    @POST("queue/{id}/cancel")
    suspend fun cancel(@Path("id") id: Int): Response<Unit>

    /** Removes a job from the queue. */
    @DELETE("queue/{id}")
    suspend fun remove(@Path("id") id: Int): Response<Unit>

    /** The picture of a job's library file, as image bytes. */
    @GET("library/files/{id}/thumbnail")
    suspend fun libraryThumbnail(@Path("id") id: Int): Response<ResponseBody>

    /** The picture of a job's archive, as image bytes. */
    @GET("archives/{id}/thumbnail")
    suspend fun archiveThumbnail(@Path("id") id: Int): Response<ResponseBody>

    /** Resumes a printer's paused queue. */
    @POST("queue/printer/{printerId}/resume")
    suspend fun resume(@Path("printerId") printerId: Int): Response<Unit>
}
