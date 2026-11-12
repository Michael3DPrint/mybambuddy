package io.github.michael3dprint.mybambuddy.domain.connectivity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** For tests and previews. */
object AlwaysOnline : ConnectivityStatus {
    override val state: StateFlow<OfflineState> = MutableStateFlow(OfflineState()).asStateFlow()

    override fun markOnline() = Unit

    override fun markOffline(savedAt: Long) = Unit

    override fun markUnreachable() = Unit
}
