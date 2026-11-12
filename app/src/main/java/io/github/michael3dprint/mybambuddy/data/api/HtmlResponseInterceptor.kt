package io.github.michael3dprint.mybambuddy.data.api

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Thrown when a successful reply is an HTML page instead of JSON, which usually means a wrong server address or a login page in
 * the way.
 */
class HtmlResponseException(message: String = HTML_ERROR_MESSAGE) : IOException(message) {
    companion object {
        /** The message used when HTML comes back where JSON was expected. */
        const val HTML_ERROR_MESSAGE =
            "Received invalid JSON response from server. Verify your server URL (e.g. http://host:8000) and API key."
    }
}

/**
 * Turns a successful reply that is really an HTML page into [HtmlResponseException]. Error statuses pass through for the normal
 * error handling.
 */
class HtmlResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        // Non-2xx responses (such as 401, 403, 404) are handled by Retrofit & safeApiCall
        if (!response.isSuccessful) {
            return response
        }

        val contentType = response.header("Content-Type")
        val isHtmlContentType = contentType?.contains("text/html", ignoreCase = true) == true

        val preview = try {
            response.peekBody(2048).string().trim()
        } catch (_: Exception) {
            ""
        }

        val isHtmlContent = preview.startsWith("<") ||
            preview.startsWith("<!DOCTYPE", ignoreCase = true) ||
            preview.startsWith("<html", ignoreCase = true) ||
            preview.startsWith("<!doctype", ignoreCase = true)

        if (isHtmlContentType || isHtmlContent) {
            throw HtmlResponseException()
        }
        return response
    }
}
