package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.store.JsonCacheStore
import io.github.michael3dprint.mybambuddy.domain.connectivity.ConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.HistoryItem
import io.github.michael3dprint.mybambuddy.domain.repo.HistoryRepository

/** The saved first page of the print history. */
data class HistoryCache(val items: List<HistoryItem> = emptyList())

/**
 * Saves the newest page of the history (archives are finished prints, so they are the safest thing to show offline).
 * Later pages are online only: with the server unreachable they return the real error instead of a partial list.
 */
class CachingHistoryRepository(
    private val inner: HistoryRepository,
    store: JsonCacheStore<HistoryCache>,
    connectivity: ConnectivityStatus,
    maxAgeMs: Long = JsonCacheStore.DEFAULT_MAX_AGE_MS,
) : HistoryRepository {
    private val source = CachedSource<List<HistoryItem>, HistoryCache>(store, connectivity, { HistoryCache(it) }, { it.items }, maxAgeMs)

    override suspend fun getHistory(limit: Int, offset: Int): ApiResult<List<HistoryItem>> {
        val result = inner.getHistory(limit, offset)
        return if (offset == 0) source.serve(result) else result
    }
}
