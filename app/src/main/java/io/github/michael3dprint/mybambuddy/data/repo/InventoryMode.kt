package io.github.michael3dprint.mybambuddy.data.repo

import com.squareup.moshi.JsonClass
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.InventoryApi
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.data.store.JsonCacheStore

/** Where spools live: Bambuddy's Spoolman passthrough when Spoolman is enabled and connected, else Bambuddy's own inventory. */
@JsonClass(generateAdapter = true)
data class InventoryMode(val useSpoolman: Boolean, val spoolmanUrl: String?)

/**
 * Asks Bambuddy once per minute which inventory is active (GET spoolman/status), so each call doesn't repeat it.
 *
 * A failed probe is never remembered as an answer (design 16, F7): the last known mode, in memory or saved by [store], is used
 * and the probe is retried on the next call. Only with no history at all does it assume Bambuddy's own inventory, and that guess
 * is not cached.
 */
class InventoryModeProvider(
    private val api: InventoryApi,
    private val store: JsonCacheStore<InventoryMode>? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var cached: InventoryMode? = null
    private var fetchedAt = 0L

    suspend fun current(): InventoryMode {
        val known = cached
        if (known != null && clock() - fetchedAt < CACHE_MS) return known
        return when (val result = safeApiCall { api.getSpoolmanStatus() }) {
            is ApiResult.Success -> {
                val status = result.data
                val mode = InventoryMode(
                    useSpoolman = status.enabled == true && status.connected == true,
                    spoolmanUrl = status.url?.takeIf { it.isNotBlank() },
                )
                cached = mode
                fetchedAt = clock()
                store?.write(mode)
                mode
            }
            is ApiResult.Error -> known ?: store?.read()?.value?.also { cached = it }
                ?: InventoryMode(useSpoolman = false, spoolmanUrl = null)
        }
    }

    private companion object {
        const val CACHE_MS = 60_000L
    }
}
