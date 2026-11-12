package io.github.michael3dprint.mybambuddy.data.barcode

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import io.github.michael3dprint.mybambuddy.domain.barcode.EanLookup
import io.github.michael3dprint.mybambuddy.domain.barcode.EanLookupResult
import io.github.michael3dprint.mybambuddy.domain.nfc.TagText
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** The reply of the barcode lookup: a list of matching products. */
@JsonClass(generateAdapter = true)
internal data class UpcItemsResponse(@Json(name = "items") val items: List<UpcItem>? = null)

/** One product from the barcode lookup: its title and brand. */
@JsonClass(generateAdapter = true)
internal data class UpcItem(@Json(name = "title") val title: String? = null, @Json(name = "brand") val brand: String? = null)

/**
 * Product lookup on upcitemdb.com (free trial endpoint). Safety rules: https only, one fixed host, digits-only input,
 * a single attempt with short timeouts, and the response is read through a hard size cap.
 */
class UpcItemDbLookup(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = "https://api.upcitemdb.com/prod/trial/lookup",
) : EanLookup {
    private val adapter = Moshi.Builder().build().adapter(UpcItemsResponse::class.java)

    override suspend fun lookup(ean: String): EanLookupResult = withContext(Dispatchers.IO) {
        if (!ean.matches(DIGITS)) return@withContext EanLookupResult.Unavailable("invalid barcode")
        val url = baseUrl.toHttpUrl().newBuilder().addQueryParameter("upc", ean).build()
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                when {
                    response.code == 429 -> EanLookupResult.Unavailable("daily limit reached")
                    !response.isSuccessful -> EanLookupResult.Unavailable("HTTP ${response.code}")
                    else -> {
                        val body = response.peekBody(MAX_BODY_BYTES).string()
                        val item = adapter.fromJson(body)?.items?.firstOrNull()
                        val title = item?.title?.let { TagText.sanitize(it, 120) }.orEmpty()
                        if (title.isBlank()) EanLookupResult.NotFound else EanLookupResult.Found(title)
                    }
                }
            }
        } catch (e: IOException) {
            EanLookupResult.Unavailable("network error")
        } catch (e: com.squareup.moshi.JsonDataException) {
            EanLookupResult.Unavailable("unexpected response")
        }
    }

    private companion object {
        val DIGITS = Regex("^[0-9]{8,14}$")
        const val MAX_BODY_BYTES = 64L * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
