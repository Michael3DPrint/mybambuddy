package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.LibraryUploadDto
import io.github.michael3dprint.mybambuddy.data.api.dto.MakerWorldImportBody
import io.github.michael3dprint.mybambuddy.data.api.dto.MakerWorldImportedDto
import io.github.michael3dprint.mybambuddy.data.api.dto.MakerWorldResolveBody
import io.github.michael3dprint.mybambuddy.data.api.dto.MakerWorldResolvedDto
import io.github.michael3dprint.mybambuddy.data.api.dto.MakerWorldStatusDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PlatesDto
import io.github.michael3dprint.mybambuddy.data.api.dto.QueueCreateBody
import io.github.michael3dprint.mybambuddy.data.api.dto.QueueItemDto
import io.github.michael3dprint.mybambuddy.data.api.dto.RequirementsDto
import io.github.michael3dprint.mybambuddy.data.api.dto.SliceBody
import io.github.michael3dprint.mybambuddy.data.api.dto.SliceJobDto
import io.github.michael3dprint.mybambuddy.data.api.dto.SliceStartedDto
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** The routes behind "print from the phone" (design 35 and 38): upload or import a file, read it, stage it, start it. */
interface PrintFileApi {
    /** Uploads a file to Bambuddy's library. The server refuses raw G-code and anything that is not a valid 3MF. */
    @Multipart
    @POST("library/files/")
    suspend fun upload(@Part file: MultipartBody.Part): Response<LibraryUploadDto>

    /** The plates of a library file with the slicer's time and filament estimates. */
    @GET("library/files/{id}/plates")
    suspend fun plates(@Path("id") id: Int): Response<PlatesDto>

    /** Deletes one library file. */
    @DELETE("library/files/{id}")
    suspend fun deleteFile(@Path("id") id: Int): Response<Unit>

    /** Creates a queue item. The body always asks for a staged item. */
    @POST("queue/")
    suspend fun createQueueItem(@Body body: QueueCreateBody): Response<QueueItemDto>

    /** Starts a staged item. [skip] skips the server's filament check and is true only after the user confirmed a shortfall. */
    @POST("queue/{id}/start")
    suspend fun start(@Path("id") id: Int, @Query("skip_filament_check") skip: Boolean): Response<Unit>

    /** Whether the server can download from MakerWorld. */
    @GET("makerworld/status")
    suspend fun makerWorldStatus(): Response<MakerWorldStatusDto>

    /** Looks up a MakerWorld link. */
    @POST("makerworld/resolve")
    suspend fun resolve(@Body body: MakerWorldResolveBody): Response<MakerWorldResolvedDto>

    /** A MakerWorld cover picture, fetched by Bambuddy (the phone never contacts MakerWorld); [url] is MakerWorld's own picture address. */
    @GET("makerworld/thumbnail")
    suspend fun makerWorldThumbnail(@Query("url") url: String): Response<ResponseBody>

    /** Downloads a MakerWorld model into the library. */
    @POST("makerworld/import")
    suspend fun import(@Body body: MakerWorldImportBody): Response<MakerWorldImportedDto>

    /** Every filament slot of a project for [plate]; the full list is what a slice needs, one preset per slot. */
    @GET("library/files/{id}/filament-requirements")
    suspend fun requirements(
        @Path("id") id: Int,
        @Query("plate_id") plate: Int,
        @Query("full_slots") fullSlots: Boolean = true,
    ): Response<RequirementsDto>

    /** The whole library file as a stream, for making a scaled copy of it on the phone. */
    @Streaming
    @GET("library/files/{id}/download")
    suspend fun download(@Path("id") id: Int): Response<ResponseBody>

    /** The picture of one plate of a library file, as image bytes. */
    @GET("library/files/{id}/plate-thumbnail/{plate}")
    suspend fun plateThumbnail(@Path("id") id: Int, @Path("plate") plate: Int): Response<ResponseBody>

    /** The plates of an archived print with the slicer's estimates; the reply has the same shape as the library's. */
    @GET("archives/{id}/plates")
    suspend fun archivePlates(@Path("id") id: Int): Response<PlatesDto>

    /** The picture of one plate of an archived print. */
    @GET("archives/{id}/plate-thumbnail/{plate}")
    suspend fun archivePlateThumbnail(@Path("id") id: Int, @Path("plate") plate: Int): Response<ResponseBody>

    /** An archived print's own thumbnail. */
    @GET("archives/{id}/thumbnail")
    suspend fun archiveThumbnail(@Path("id") id: Int): Response<ResponseBody>

    /** The library file's own thumbnail, as image bytes. */
    @GET("library/files/{id}/thumbnail")
    suspend fun thumbnail(@Path("id") id: Int): Response<ResponseBody>

    /** Starts a slice of a library file; the server answers 202 with a job to poll. */
    @POST("library/files/{id}/slice")
    suspend fun slice(@Path("id") id: Int, @Body body: SliceBody): Response<SliceStartedDto>

    /** A slice job's status. */
    @GET("slice-jobs/{id}")
    suspend fun sliceJob(@Path("id") id: Int): Response<SliceJobDto>
}
