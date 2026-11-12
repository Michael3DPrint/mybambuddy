package io.github.michael3dprint.mybambuddy.util

import android.content.Context
import coil.ImageLoader
import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.CameraApi
import io.github.michael3dprint.mybambuddy.data.api.CameraTokenInterceptor
import io.github.michael3dprint.mybambuddy.data.api.CameraTokenProvider
import io.github.michael3dprint.mybambuddy.data.prefs.SettingsRepository
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object CoilImageLoaderFactory {
    /** OkHttp client for camera snapshots and the live stream: adds the stream token, longer read timeout. */
    fun createCameraClient(settingsRepository: SettingsRepository, api: CameraApi): OkHttpClient =
        ApiClient.createOkHttpClient(settingsRepository)
            .newBuilder()
            .addInterceptor(CameraTokenInterceptor(CameraTokenProvider(api)))
            // The server may take up to ~15 s to grab a camera frame.
            .readTimeout(20, TimeUnit.SECONDS)
            // The live MJPEG stream runs for minutes; the read timeout above still catches a stalled stream.
            .callTimeout(0, TimeUnit.SECONDS)
            .build()

    fun create(context: Context, settingsRepository: SettingsRepository, api: CameraApi): ImageLoader =
        create(context, createCameraClient(settingsRepository, api))

    fun create(context: Context, cameraClient: OkHttpClient): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(cameraClient)
        .crossfade(true)
        .build()
}
