package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.store.JsonCacheStore
import io.github.michael3dprint.mybambuddy.domain.connectivity.ConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.repo.AmsRepository

/** The saved copy of the AMS overview. */
data class AmsCache(val units: List<AmsUnit> = emptyList())

/** Saves the AMS overview. Loading, unloading and drying are never cached or replayed. */
class CachingAmsRepository(
    private val inner: AmsRepository,
    store: JsonCacheStore<AmsCache>,
    connectivity: ConnectivityStatus,
    maxAgeMs: Long = JsonCacheStore.DEFAULT_MAX_AGE_MS,
) : AmsRepository by inner {
    private val source = CachedSource<List<AmsUnit>, AmsCache>(store, connectivity, { AmsCache(it) }, { it.units }, maxAgeMs)

    override suspend fun getAms(): ApiResult<List<AmsUnit>> = source.serve(inner.getAms())
}
