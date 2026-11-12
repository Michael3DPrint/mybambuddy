package io.github.michael3dprint.mybambuddy.data.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hands out the camera stream token, getting a new one when none is held or the held one is older than 50 minutes. One request at
 * a time.
 */
class CameraTokenProvider(private val api: CameraApi, private val clock: () -> Long = { System.currentTimeMillis() }) {
    private val mutex = Mutex()

    @Volatile private var token: String? = null

    @Volatile private var mintedAt = 0L

    /** Returns a token, minting a new one if none or older than 50 min. null if the server refuses. */
    suspend fun getToken(): String? = mutex.withLock {
        val current = token
        if (current != null && clock() - mintedAt < 50 * 60_000L) return@withLock current
        val fresh = (safeApiCall { api.getCameraStreamToken() } as? ApiResult.Success)?.data?.token
        token = fresh
        mintedAt = clock()
        fresh
    }

    /** Forgets the held token so the next call gets a new one. */
    fun invalidate() {
        token = null
    }
}
