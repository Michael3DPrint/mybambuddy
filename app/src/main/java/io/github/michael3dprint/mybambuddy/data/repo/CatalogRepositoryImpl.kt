package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.SpoolmanDbApi
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.data.prefs.SettingsRepository
import io.github.michael3dprint.mybambuddy.domain.model.CatalogFilament
import io.github.michael3dprint.mybambuddy.domain.repo.CatalogRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Reads SpoolmanDB straight from the Spoolman server. Its address comes from Bambuddy
 * (GET spoolman/status), so nothing extra has to be configured.
 */
class CatalogRepositoryImpl(
    private val mode: InventoryModeProvider,
    private val settings: SettingsRepository,
    private val dbApiFactory: (String) -> SpoolmanDbApi = { ApiClient.createSpoolmanDbApi(it) },
) : CatalogRepository {

    /** Repeat searches are answered from memory (design 16, F6); the catalog barely changes and this is cleared when the process ends. */
    private val recent = object : LinkedHashMap<String, List<CatalogFilament>>(MAX_RECENT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<CatalogFilament>>?) = size > MAX_RECENT
    }

    override suspend fun search(query: String): ApiResult<List<CatalogFilament>> {
        val q = query.trim()
        if (q.isEmpty()) return ApiResult.Success(emptyList())
        val spoolmanUrl = mode.current().spoolmanUrl
            ?: return ApiResult.Error(
                message = "Spoolman isn't connected to Bambuddy, so the filament catalog is unavailable.",
                errorType = ApiErrorType.UNSUPPORTED,
            )
        val key = q.lowercase()
        synchronized(recent) { recent[key] }?.let { return ApiResult.Success(it) }
        val dbApi = dbApiFactory(reachableSpoolmanUrl(spoolmanUrl, settings.getServerUrlSync()))
        return when (val result = safeApiCall { dbApi.searchFilaments(q) }) {
            is ApiResult.Success -> {
                val found = result.data.mapNotNull { it.toCatalog() }
                synchronized(recent) { recent[key] = found }
                ApiResult.Success(found)
            }
            is ApiResult.Error -> result
        }
    }

    internal companion object {
        private const val MAX_RECENT = 32

        /**
         * The address Bambuddy reports is how the SERVER reaches Spoolman. If that is localhost or a
         * single-word docker name the phone can't use it, so reuse the Bambuddy host with Spoolman's port.
         */
        fun reachableSpoolmanUrl(spoolmanUrl: String, bambuddyUrl: String): String {
            val spoolman = spoolmanUrl.toHttpUrlOrNull() ?: return spoolmanUrl
            val host = spoolman.host
            val unreachable = host == "localhost" ||
                host.startsWith("127.") ||
                host == "0.0.0.0" ||
                (!host.contains('.') && !host.contains(':'))
            if (!unreachable) return spoolmanUrl
            val withScheme = if (bambuddyUrl.startsWith("http")) bambuddyUrl else "http://$bambuddyUrl"
            val bambuddy = withScheme.toHttpUrlOrNull() ?: return spoolmanUrl
            return spoolman.newBuilder().host(bambuddy.host).build().toString()
        }
    }
}
