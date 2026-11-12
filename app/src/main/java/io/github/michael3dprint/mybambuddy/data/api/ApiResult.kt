package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.domain.model.ErrorKind
import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState
import io.github.michael3dprint.mybambuddy.util.LogPolicy
import io.github.michael3dprint.mybambuddy.util.PrintDeckLogger
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/** The outcome of a server call: the data, or an error with its type and the server's own reason when it gave one. */
sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(
        /** The HTTP status code, or null when the failure was not an HTTP answer. */
        val code: Int? = null,
        /** The app's own text for this failure; the server's reason is in [serverDetail] when it gave one. */
        val message: String,
        /** What kind of failure this was, which decides the title and wording shown. */
        val errorType: ApiErrorType,
        /** The server's own readable reason from its error body, when it had one. */
        val serverDetail: String? = null,
        /** Bambuddy's machine-readable reason when its error body has one, such as "insufficient_filament". */
        val serverCode: String? = null,
    ) : ApiResult<Nothing>()
}

/** Why a call failed. UNSUPPORTED is a refusal made on the phone before anything was sent. */
enum class ApiErrorType {
    UNAUTHORIZED, // 401
    FORBIDDEN, // 403 (API key lacks the needed permission)
    REJECTED, // 400, 409, 410, 413, 422 (server refused the action or the file)
    UNAVAILABLE, // 503 (e.g. camera busy)
    UNSUPPORTED, // client-side: action not supported
    NOT_FOUND, // 404
    SERVER_ERROR, // 5xx
    NETWORK_ERROR, // Network connection / timeout
    PARSE_ERROR, // Bad JSON format
    UNKNOWN,
}

/**
 * [aboutPrinter] is false for calls that never touch the printer (inventory, catalogue). Their refusals then
 * say "request", not "printer", so a spool save can't blame the printer.
 */
fun ApiResult.Error.toUiErrorState(customContext: String? = null, aboutPrinter: Boolean = true): UiErrorState {
    val kind = when (errorType) {
        ApiErrorType.NETWORK_ERROR -> ErrorKind.NETWORK_UNREACHABLE
        ApiErrorType.UNAUTHORIZED -> ErrorKind.UNAUTHORIZED
        ApiErrorType.FORBIDDEN -> ErrorKind.FORBIDDEN
        ApiErrorType.REJECTED -> ErrorKind.ACTION_REJECTED
        ApiErrorType.UNAVAILABLE -> ErrorKind.PRINTER_UNAVAILABLE
        ApiErrorType.UNSUPPORTED -> ErrorKind.UNSUPPORTED
        ApiErrorType.NOT_FOUND -> ErrorKind.NOT_FOUND
        ApiErrorType.SERVER_ERROR -> ErrorKind.SERVER_ERROR
        ApiErrorType.PARSE_ERROR -> ErrorKind.PARSE_ERROR
        ApiErrorType.UNKNOWN -> ErrorKind.UNKNOWN
    }
    val title: String
    val defaultMsg: String
    val primaryLabel: String?
    val secondaryLabel: String?

    when (kind) {
        ErrorKind.NETWORK_UNREACHABLE -> {
            title = "Server Unreachable"
            defaultMsg = "Unable to connect to Bambuddy server. Please check your network and Tailscale connection."
            primaryLabel = "Retry"
            secondaryLabel = "Open Settings"
        }
        ErrorKind.UNAUTHORIZED -> {
            title = "Authentication Failed"
            defaultMsg = "Invalid API key or permission denied. Please check your API key in Settings."
            primaryLabel = "Retry"
            secondaryLabel = "Open Settings"
        }
        ErrorKind.FORBIDDEN -> {
            title = "Permission Denied"
            defaultMsg = "This API key isn't allowed to do that. In Bambuddy, give the key the needed permission."
            primaryLabel = "Retry"
            secondaryLabel = "Open Settings"
        }
        ErrorKind.ACTION_REJECTED -> {
            title = if (aboutPrinter) "Printer Refused" else "Request Rejected"
            defaultMsg = if (aboutPrinter) {
                "The printer can't do that right now."
            } else {
                "The server rejected this request. Check the details and try again."
            }
            primaryLabel = "Retry"
            secondaryLabel = null
        }
        ErrorKind.PRINTER_UNAVAILABLE -> {
            title = if (aboutPrinter) "Printer Unavailable" else "Service Unavailable"
            defaultMsg = if (aboutPrinter) {
                "The printer isn't connected to Bambuddy right now."
            } else {
                "Bambuddy or Spoolman isn't available right now. Try again shortly."
            }
            primaryLabel = "Retry"
            secondaryLabel = null
        }
        ErrorKind.UNSUPPORTED -> {
            title = "Not Supported"
            defaultMsg = "This action isn't supported."
            primaryLabel = null
            secondaryLabel = null
        }
        ErrorKind.NOT_FOUND -> {
            title = "Resource Not Found"
            defaultMsg = "The requested printer or endpoint was not found on the server."
            primaryLabel = "Retry"
            secondaryLabel = "Open Settings"
        }
        ErrorKind.SERVER_ERROR -> {
            title = "Server Error"
            defaultMsg = "The Bambuddy server encountered an internal error. Please try again later."
            primaryLabel = "Retry"
            secondaryLabel = null
        }
        ErrorKind.PARSE_ERROR -> {
            title = "Invalid Server Response"
            defaultMsg = "Received invalid JSON response from server. Verify your server URL and API key."
            primaryLabel = "Retry"
            secondaryLabel = "Open Settings"
        }
        ErrorKind.UNKNOWN -> {
            title = "Connection Error"
            defaultMsg = message.ifBlank { "An unexpected error occurred while communicating with the server." }
            primaryLabel = "Retry"
            secondaryLabel = null
        }
    }

    return UiErrorState(
        kind = kind,
        title = title,
        // A client-side refusal (UNSUPPORTED) carries its own reason, which says what to do; the generic text would hide it.
        message = customContext ?: serverDetail ?: if (kind == ErrorKind.UNSUPPORTED) message.ifBlank { defaultMsg } else defaultMsg,
        primaryActionLabel = primaryLabel,
        secondaryActionLabel = secondaryLabel,
    )
}

/**
 * Runs one Retrofit call and turns every outcome into an [ApiResult]: the body on success, a typed error with the server's own reason on a
 * bad status, and a parse or network error for anything else. Never throws, and never retries (that is the caller's decision).
 */
suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): ApiResult<T> = try {
    val response = apiCall()
    if (response.isSuccessful) {
        val body = response.body()
        if (body != null) {
            ApiResult.Success(body)
        } else {
            val errorMsg = "Received invalid JSON response from server. Verify your server URL (e.g. http://host:8000) and API key."
            PrintDeckLogger.e("PrintDeckTrace", "HTTP response body was null. Status code: ${response.code()}")
            ApiResult.Error(
                code = response.code(),
                message = errorMsg,
                errorType = ApiErrorType.PARSE_ERROR,
            )
        }
    } else {
        val code = response.code()
        val errorType = when (code) {
            401 -> ApiErrorType.UNAUTHORIZED
            403 -> ApiErrorType.FORBIDDEN
            404 -> ApiErrorType.NOT_FOUND
            400, 409, 410, 413, 422 -> ApiErrorType.REJECTED
            503 -> ApiErrorType.UNAVAILABLE
            in 500..599 -> ApiErrorType.SERVER_ERROR
            else -> ApiErrorType.UNKNOWN
        }
        val errorMsg = when (code) {
            401 -> "Invalid API key (HTTP 401)."
            403 -> "Permission denied for this API key (HTTP 403)."
            404 -> "Endpoint not found on server (HTTP 404)."
            in 500..599 -> "Server error occurred (HTTP $code)."
            else -> "HTTP error $code"
        }
        val rawError = try {
            response.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        val detail = ServerDetail.from(rawError)
        // The body is server-supplied text: only a debuggable build may log it (design 19).
        val bodyText = if (LogPolicy.verbose) ": ${rawError?.take(500)}" else ""
        PrintDeckLogger.e("PrintDeckTrace", "API HTTP Error code $code ($errorType)$bodyText")
        ApiResult.Error(
            code = code,
            message = errorMsg,
            errorType = errorType,
            serverDetail = detail,
            serverCode = ServerDetail.codeOf(rawError),
        )
    }
} catch (e: HtmlResponseException) {
    PrintDeckLogger.e("PrintDeckTrace", "HtmlResponseException: HTML payload returned instead of JSON", e)
    ApiResult.Error(
        message = "Received invalid JSON response from server. Verify your server URL (e.g. http://host:8000) and API key.",
        errorType = ApiErrorType.PARSE_ERROR,
    )
} catch (e: com.squareup.moshi.JsonEncodingException) {
    PrintDeckLogger.e("PrintDeckTrace", "JsonEncodingException: Malformed JSON stream", e)
    ApiResult.Error(
        message = "Received invalid JSON response from server. Verify your server URL (e.g. http://host:8000) and API key.",
        errorType = ApiErrorType.PARSE_ERROR,
    )
} catch (e: com.squareup.moshi.JsonDataException) {
    PrintDeckLogger.e("PrintDeckTrace", "JsonDataException: Invalid JSON structure or type mismatch", e)
    ApiResult.Error(
        message = "Received invalid JSON response from server. Verify your server URL (e.g. http://host:8000) and API key.",
        errorType = ApiErrorType.PARSE_ERROR,
    )
} catch (e: java.io.IOException) {
    PrintDeckLogger.e("PrintDeckTrace", "IOException during API call", e)
    val msg = e.message ?: ""
    if (msg.contains("HTML", ignoreCase = true) ||
        msg.contains("JSON", ignoreCase = true) ||
        msg.contains("malformed", ignoreCase = true) ||
        msg.contains("lenient", ignoreCase = true)
    ) {
        ApiResult.Error(
            message = "Received invalid JSON response from server. Verify your server URL (e.g. http://host:8000) and API key.",
            errorType = ApiErrorType.PARSE_ERROR,
        )
    } else {
        ApiResult.Error(
            message = "Network connection failed or timed out. Please check your network and Tailscale connection.",
            errorType = ApiErrorType.NETWORK_ERROR,
        )
    }
} catch (e: Exception) {
    PrintDeckLogger.e("PrintDeckTrace", "Unhandled Exception during API call", e)
    val msg = e.message ?: ""
    if (msg.contains("HTML", ignoreCase = true) ||
        msg.contains("JSON", ignoreCase = true) ||
        msg.contains("malformed", ignoreCase = true) ||
        msg.contains("lenient", ignoreCase = true) ||
        msg.contains("Moshi", ignoreCase = true)
    ) {
        ApiResult.Error(
            message = "Received invalid JSON response from server. Verify your server URL (e.g. http://host:8000) and API key.",
            errorType = ApiErrorType.PARSE_ERROR,
        )
    } else {
        ApiResult.Error(
            message = msg.ifEmpty { "An unexpected error occurred." },
            errorType = ApiErrorType.UNKNOWN,
        )
    }
}

/** For control endpoints: any 2xx is success, even with an empty body (204). */
/** Drops a response body we do not need, keeping the status, so an empty or unexpected body cannot turn a success into an error. */
fun <T> Response<T>.discardBody(): Response<Unit> =
    if (isSuccessful) Response.success(Unit, raw()) else Response.error(errorBody() ?: "".toResponseBody(null), raw())

/** [safeApiCall] for a call that returns no body: a success with an empty body is still a success. */
suspend fun safeUnitCall(apiCall: suspend () -> Response<Unit>): ApiResult<Unit> = safeApiCall {
    val resp = apiCall()
    if (resp.isSuccessful && resp.body() == null) Response.success(Unit, resp.raw()) else resp
}

/** True for "could not reach the server / server down". Auth and permission errors are real answers and are never hidden behind saved data. */
fun ApiResult.Error.isUnreachable(): Boolean = when (errorType) {
    ApiErrorType.NETWORK_ERROR, ApiErrorType.SERVER_ERROR, ApiErrorType.UNAVAILABLE -> true
    else -> false
}
