package io.github.michael3dprint.mybambuddy.data.discovery

import io.github.michael3dprint.mybambuddy.domain.network.BambuddyFingerprint
import io.github.michael3dprint.mybambuddy.domain.network.HealthProbe
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Asks an address for Bambuddy's /health page. It uses its own plain client on purpose: the app's shared client adds the API key, and the
 * key must never be sent to an address that is only being looked at. Short limits, no redirects, no retries.
 */
class OkHttpHealthProbe(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(PROBE_MS, TimeUnit.MILLISECONDS)
        .callTimeout(PROBE_MS * 2, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build(),
) : HealthProbe {
    override suspend fun isBambuddy(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("http://$host:$port/health").get().build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful && BambuddyFingerprint.matches(response.peekBody(BambuddyFingerprint.MAX_BODY + 1L).string())
            }
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val PROBE_MS = 400L
    }
}
