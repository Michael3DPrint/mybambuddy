package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.isUnreachable
import io.github.michael3dprint.mybambuddy.data.store.Cached
import io.github.michael3dprint.mybambuddy.data.store.JsonCacheStore
import io.github.michael3dprint.mybambuddy.domain.connectivity.ConnectivityStatus

/**
 * The one set of rules every caching decorator follows (design 16): a live success is saved and marks the app online; an unreachable
 * server serves the saved copy if it is younger than [maxAgeMs] and marks the app offline; with no usable copy the error is returned
 * unchanged but the app is still marked offline, so the banner and the disabled controls do not depend on a cache existing.
 *
 * @param minWriteGapMs saves at most once per this many milliseconds, so a 5-second poll does not rewrite the file 720 times an hour.
 */
class CachedSource<T, C : Any>(
    private val store: JsonCacheStore<C>,
    private val connectivity: ConnectivityStatus,
    private val wrap: (T) -> C,
    private val unwrap: (C) -> T,
    private val maxAgeMs: Long = JsonCacheStore.DEFAULT_MAX_AGE_MS,
    private val minWriteGapMs: Long = 0L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var lastWriteAt = Long.MIN_VALUE

    /** The saved copy, or null when there is none or it is too old. */
    fun saved(): Cached<T>? = store.read(maxAgeMs)?.let { Cached(unwrap(it.value), it.savedAt) }

    /** Applies the rules above to a live [result]. */
    fun serve(result: ApiResult<T>): ApiResult<T> = when (result) {
        is ApiResult.Success -> {
            saveThrottled(result.data)
            connectivity.markOnline()
            result
        }
        is ApiResult.Error -> serveFromSaved(result)
    }

    private fun serveFromSaved(error: ApiResult.Error): ApiResult<T> {
        if (!error.isUnreachable()) return error
        val copy = saved()
        if (copy == null) {
            connectivity.markUnreachable()
            return error
        }
        connectivity.markOffline(copy.savedAt)
        return ApiResult.Success(copy.value)
    }

    private fun saveThrottled(data: T) {
        val now = clock()
        if (lastWriteAt != Long.MIN_VALUE && now - lastWriteAt < minWriteGapMs) return
        lastWriteAt = now
        store.write(wrap(data))
    }
}
