package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.connectivity.AlwaysOnline
import io.github.michael3dprint.mybambuddy.domain.connectivity.ConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Sends a scale reading for a spool. Only changes the inventory record; refused while offline and in the built-in inventory mode. */
class SpoolWeighViewModel(private val spools: SpoolRepository, private val connectivity: ConnectivityStatus = AlwaysOnline) : ViewModel() {
    private val _available = MutableStateFlow(false)

    /** True when the weigh button should show (Spoolman is the active inventory). */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** One-line results for the snackbar. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _updated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emitted after a successful update, so the spool list reloads. */
    val updated: SharedFlow<Unit> = _updated.asSharedFlow()

    /** Asks which inventory is active and shows or hides the weigh button to match. */
    fun checkAvailable() {
        viewModelScope.launch { _available.value = spools.canWeigh() }
    }

    /** The dialog has already checked the reading with [io.github.michael3dprint.mybambuddy.domain.policy.SpoolWeighRules]. */
    fun weigh(spool: Spool, grossGrams: Double) {
        viewModelScope.launch {
            if (connectivity.state.value.isOffline) {
                _messages.emit("You're offline — can't update the weight right now.")
                return@launch
            }
            when (val result = spools.updateWeight(spool.id, grossGrams)) {
                is ApiResult.Success -> {
                    _messages.emit("${spool.title} updated from the scale")
                    _updated.emit(Unit)
                }
                is ApiResult.Error -> _messages.emit(result.toUiErrorState().message)
            }
        }
    }

    class Factory(private val spools: SpoolRepository, private val connectivity: ConnectivityStatus = AlwaysOnline) :
        ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SpoolWeighViewModel(spools, connectivity) as T
    }
}
