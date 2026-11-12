package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.StreamTokenResponse
import retrofit2.Response
import retrofit2.http.POST

/** Camera calls. Snapshots and streams are fetched by URL through Coil and OkHttp, with a token from [getCameraStreamToken]. */
interface CameraApi {
    /** Short-lived (about 60 minutes) token that authorises snapshot and stream URLs when server auth is on. */
    @POST("printers/camera/stream-token")
    suspend fun getCameraStreamToken(): Response<StreamTokenResponse>
}
