package io.github.michael3dprint.mybambuddy.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/** Adds ?token=... to camera snapshot requests (Bambuddy ignores X-API-Key there when auth is on). */
class CameraTokenInterceptor(private val provider: CameraTokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!SNAPSHOT_PATH.containsMatchIn(request.url.encodedPath) || request.url.queryParameter("token") != null) {
            return chain.proceed(request)
        }
        val token = runBlocking { provider.getToken() } ?: return chain.proceed(request)
        val response = chain.proceed(request.withToken(token))
        if (response.code != 401) return response
        response.close()
        provider.invalidate()
        val fresh = runBlocking { provider.getToken() } ?: return chain.proceed(request)
        return chain.proceed(request.withToken(fresh))
    }

    private fun Request.withToken(token: String): Request =
        newBuilder().url(url.newBuilder().setQueryParameter("token", token).build()).build()

    private companion object {
        val SNAPSHOT_PATH = Regex("/printers/[^/]+/camera/(snapshot|stream)$")
    }
}
