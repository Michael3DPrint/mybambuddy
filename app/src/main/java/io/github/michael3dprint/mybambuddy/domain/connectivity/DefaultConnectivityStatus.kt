package io.github.michael3dprint.mybambuddy.domain.connectivity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * @param failuresBeforeOffline how many failures in a row, with no success between them, before the app counts as offline. One dropped
 * request is normal on a phone (a closed keep-alive connection, a Wi-Fi handover) and must not switch every control off.
 */
class DefaultConnectivityStatus(private val failuresBeforeOffline: Int = 1) : ConnectivityStatus {
    private val _state = MutableStateFlow(OfflineState())
    override val state: StateFlow<OfflineState> = _state.asStateFlow()
    private var failures = 0

    @Synchronized
    override fun markOnline() {
        failures = 0
        _state.value = OfflineState()
    }

    @Synchronized
    override fun markOffline(savedAt: Long) {
        if (++failures >= failuresBeforeOffline) _state.value = OfflineState(isOffline = true, dataFrom = savedAt)
    }

    @Synchronized
    override fun markUnreachable() {
        if (++failures >= failuresBeforeOffline) _state.value = OfflineState(isOffline = true, dataFrom = null)
    }
}
