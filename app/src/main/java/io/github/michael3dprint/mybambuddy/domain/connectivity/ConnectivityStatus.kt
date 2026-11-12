package io.github.michael3dprint.mybambuddy.domain.connectivity

import kotlinx.coroutines.flow.StateFlow

/** Whether the data on screen is live or a saved copy. [dataFrom] is the save time of the copy shown, or null when there is none. */
data class OfflineState(val isOffline: Boolean = false, val dataFrom: Long? = null)

/** One place that knows if the app is currently showing saved data (single source for the banner and for disabling controls). */
interface ConnectivityStatus {
    /** Whether the app is showing live data or a saved copy right now. */
    val state: StateFlow<OfflineState>

    /** A request reached the server: the app is live again. */
    fun markOnline()

    /** The server could not be reached; the app is showing a copy saved at [savedAt]. */
    fun markOffline(savedAt: Long)

    /** The server could not be reached and there is no saved copy to show. */
    fun markUnreachable()
}
