package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.prefs.SettingsRepository
import okhttp3.Interceptor
import okhttp3.Response

/** Adds the API key (the `X-API-Key` header) to each request when one is set. */
class AuthInterceptor(private val apiKeyProvider: () -> String) : Interceptor {
    constructor(settingsRepository: SettingsRepository) : this({ settingsRepository.getApiKeySync() })

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val apiKey = apiKeyProvider().trim()

        return if (apiKey.isNotEmpty()) {
            val authenticatedRequest = originalRequest.newBuilder()
                .header("X-API-Key", apiKey)
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
