package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.store.JsonCacheStore
import io.github.michael3dprint.mybambuddy.domain.connectivity.ConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.repo.QueueRepository

/** The saved copy of the print queue. */
data class QueueCache(val items: List<QueueItem> = emptyList())

/** Saves the queue and serves it when the server can't be reached. Every action goes straight to the server. */
class CachingQueueRepository(
    private val inner: QueueRepository,
    store: JsonCacheStore<QueueCache>,
    connectivity: ConnectivityStatus,
    maxAgeMs: Long = JsonCacheStore.DEFAULT_MAX_AGE_MS,
) : QueueRepository by inner {
    private val source = CachedSource<List<QueueItem>, QueueCache>(store, connectivity, { QueueCache(it) }, { it.items }, maxAgeMs)

    override suspend fun getQueue(): ApiResult<List<QueueItem>> = source.serve(inner.getQueue())
}
