package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.store.JsonCacheStore
import io.github.michael3dprint.mybambuddy.domain.connectivity.ConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository

/** The saved copy of the spool list. */
data class SpoolsCache(val spools: List<Spool> = emptyList())

/** Saves the spool list and serves it when the server can't be reached. Writes (create, tag link) always go to the server. */
class CachingSpoolRepository(
    private val inner: SpoolRepository,
    store: JsonCacheStore<SpoolsCache>,
    connectivity: ConnectivityStatus,
    maxAgeMs: Long = JsonCacheStore.DEFAULT_MAX_AGE_MS,
) : SpoolRepository by inner {
    private val source = CachedSource<List<Spool>, SpoolsCache>(store, connectivity, { SpoolsCache(it) }, { it.spools }, maxAgeMs)

    override suspend fun getSpools(): ApiResult<List<Spool>> = source.serve(inner.getSpools())

    override suspend fun createSpool(draft: SpoolDraft): ApiResult<Spool> = inner.createSpool(draft)
}
