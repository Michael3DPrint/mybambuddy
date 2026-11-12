package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.PrinterApi
import io.github.michael3dprint.mybambuddy.data.api.SystemApi
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall

/** What the connection test learned about the server: its version and how many printers it has. */
data class ServerInfo(val version: String?, val printerCount: Int)

/** The two narrow interfaces [testServerConnection] needs; Setup and Settings build them from a temporary client. */
class ServerProbeApis(val printers: PrinterApi, val system: SystemApi) {
    companion object {
        /** Builds the two probe APIs on the given HTTP client. */
        fun create(okHttpClient: okhttp3.OkHttpClient): ServerProbeApis = ServerProbeApis(
            ApiClient.createService(okHttpClient, PrinterApi::class.java),
            ApiClient.createService(okHttpClient, SystemApi::class.java),
        )
    }
}

/**
 * One connection test for Setup and Settings. Bambuddy has no /api/v1/health, so we call
 * GET printers/ (proves URL + key work) and, optionally, GET system/info for the version
 * (a limited API key may get 403 there; that is fine).
 */
suspend fun testServerConnection(apis: ServerProbeApis): ApiResult<ServerInfo> {
    val printers = safeApiCall { apis.printers.getPrinters() }
    if (printers is ApiResult.Error) return printers
    val count = (printers as ApiResult.Success).data.effectivePrinters.size
    val version = (safeApiCall { apis.system.getSystemInfo() } as? ApiResult.Success)?.data?.effectiveVersion
    return ApiResult.Success(ServerInfo(version, count))
}

/** The one-line result shown after a successful connection test. */
fun ServerInfo.toMessage(): String = "Connected to Bambuddy${version?.let { " v$it" } ?: ""} — $printerCount printer(s)"
