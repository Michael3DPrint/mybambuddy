package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.prefs.SettingsRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Points each request at the server address saved in Settings (adding `http://` and `/api/v1` when they are missing), so a changed
 * address takes effect without rebuilding the client.
 */
class DynamicUrlInterceptor(private val serverUrlProvider: () -> String) : Interceptor {
    constructor(settingsRepository: SettingsRepository) : this({ settingsRepository.getServerUrlSync() })

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var configuredUrl = serverUrlProvider().trim()

        if (configuredUrl.isNotEmpty()) {
            if (!configuredUrl.startsWith("http://") && !configuredUrl.startsWith("https://")) {
                configuredUrl = "http://$configuredUrl"
            }
            while (configuredUrl.endsWith("/")) {
                configuredUrl = configuredUrl.dropLast(1)
            }

            val targetHttpUrl = configuredUrl.toHttpUrlOrNull()
            if (targetHttpUrl != null) {
                val targetPathSegments = targetHttpUrl.pathSegments.filter { it.isNotEmpty() }
                val basePaths = when {
                    targetPathSegments.takeLast(2) == listOf("api", "v1") -> targetPathSegments
                    targetPathSegments.lastOrNull() == "api" -> targetPathSegments + "v1"
                    else -> targetPathSegments + listOf("api", "v1")
                }

                val originalPathSegments = request.url.encodedPathSegments.filter { it.isNotEmpty() }
                val hadTrailingSlash = request.url.encodedPath.length > 1 && request.url.encodedPath.endsWith("/")
                val effectiveOriginalSegments = when {
                    originalPathSegments.take(2) == listOf("api", "v1") -> originalPathSegments.drop(2)
                    originalPathSegments.firstOrNull() == "api" && basePaths.lastOrNull() == "api" -> originalPathSegments.drop(1)
                    else -> originalPathSegments
                }

                val newUrlBuilder = request.url.newBuilder()
                    .scheme(targetHttpUrl.scheme)
                    .host(targetHttpUrl.host)
                    .port(targetHttpUrl.port)
                    .encodedPath("/" + (basePaths + effectiveOriginalSegments).joinToString("/") + if (hadTrailingSlash) "/" else "")

                request = request.newBuilder()
                    .url(newUrlBuilder.build())
                    .build()
            }
        }
        return chain.proceed(request)
    }
}
