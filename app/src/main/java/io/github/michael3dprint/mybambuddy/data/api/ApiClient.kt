package io.github.michael3dprint.mybambuddy.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.michael3dprint.mybambuddy.data.net.LogRedaction
import io.github.michael3dprint.mybambuddy.data.net.ReadRetryInterceptor
import io.github.michael3dprint.mybambuddy.data.net.ResponseSizeLimitInterceptor
import io.github.michael3dprint.mybambuddy.data.prefs.SettingsRepository
import io.github.michael3dprint.mybambuddy.util.PrintDeckLogger
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/** Builds the HTTP client, the Moshi instance and the Retrofit services (one small interface per area). */
object ApiClient {
    /** The Moshi instance with the lenient adapters for the server's varying reply shapes. */
    fun createMoshi(): Moshi = Moshi.Builder()
        .add(HealthResponseAdapter())
        .add(SystemInfoResponseAdapter())
        .add(PrintersResponseAdapter())
        .add(HmsErrorDtoAdapter())
        .add(PrinterStatusDtoAdapter())
        .add(ArchivesResponseDtoAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    /** The shared HTTP client; it reads the server address and API key from the settings on every request. */
    fun createOkHttpClient(settingsRepository: SettingsRepository): OkHttpClient = createOkHttpClient(
        serverUrlProvider = { settingsRepository.getServerUrlSync() },
        apiKeyProvider = { settingsRepository.getApiKeySync() },
    )

    /** Client for a URL/key that has NOT been saved yet (connection test). */
    fun createOkHttpClient(serverUrlProvider: () -> String, apiKeyProvider: () -> String): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor {
            PrintDeckLogger.i(message = LogRedaction.redact(it))
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("X-API-Key")
            redactHeader("Authorization")
        }

        return OkHttpClient.Builder()
            .addInterceptor(DynamicUrlInterceptor(serverUrlProvider))
            .addInterceptor(AuthInterceptor(apiKeyProvider))
            .addInterceptor(HtmlResponseInterceptor())
            .addInterceptor(loggingInterceptor)
            // Closest to the network: wraps the raw body before any other interceptor reads it.
            .addInterceptor(ResponseSizeLimitInterceptor())
            // Innermost: repeats a dropped read once (a dead keep-alive connection) and never a write.
            .addInterceptor(ReadRetryInterceptor())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            // Whole-call limit, so no request can hang forever.
            .callTimeout(30, TimeUnit.SECONDS)
            // Never silently resend a request (a control command could otherwise be sent twice).
            .retryOnConnectionFailure(false)
            .build()
    }

    /** Plain client for the Spoolman server: no Bambuddy URL rewriting, no Bambuddy API key. */
    fun createSpoolmanDbApi(spoolmanUrl: String, moshi: Moshi = createMoshi()): SpoolmanDbApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val base = spoolmanUrl.trim().trimEnd('/').removeSuffix("/api/v1")
        return Retrofit.Builder()
            .baseUrl("$base/api/v1/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SpoolmanDbApi::class.java)
    }

    /** Builds any Retrofit service interface on the same Bambuddy base URL; one small interface per area (Interface Segregation). */
    fun <T> createService(
        okHttpClient: OkHttpClient,
        service: Class<T>,
        moshi: Moshi = createMoshi(),
        baseUrl: String = "http://localhost/api/v1/",
    ): T = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
        .build()
        .create(service)
}
