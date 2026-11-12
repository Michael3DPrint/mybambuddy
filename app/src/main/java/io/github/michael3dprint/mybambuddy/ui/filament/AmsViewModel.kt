package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState
import io.github.michael3dprint.mybambuddy.domain.repo.AmsRepository
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the AMS list shows: loading, the units, or an error. */
sealed interface AmsListState {
    data object Loading : AmsListState

    data class Success(val units: List<AmsUnit>) : AmsListState

    data class Error(val error: UiErrorState) : AmsListState
}

/** A slot the user tapped, waiting for them to choose a spool to load. */
data class SlotTarget(val unit: AmsUnit, val trayId: Int)

/**
 * Screen model for the AMS list: what is loaded where, humidity, and the slot the user tapped. Loading spools is
 * [SlotAssignmentViewModel] and drying is [DryingViewModel] (docs/design/12-ams-viewmodel-split.md); the screen refreshes this list
 * when either reports a change.
 */
class AmsViewModel(private val ams: AmsRepository, private val spools: SpoolRepository) : ViewModel() {
    private val _state = MutableStateFlow<AmsListState>(AmsListState.Loading)
    val state: StateFlow<AmsListState> = _state.asStateFlow()

    private val _slotTarget = MutableStateFlow<SlotTarget?>(null)
    val slotTarget: StateFlow<SlotTarget?> = _slotTarget.asStateFlow()

    private val _spoolChoices = MutableStateFlow<List<Spool>>(emptyList())
    val spoolChoices: StateFlow<List<Spool>> = _spoolChoices.asStateFlow()

    private val _spoolsById = MutableStateFlow<Map<Int, Spool>>(emptyMap())

    /** The inventory's spools by id, read with each refresh, so a slot can show its linked spool's grams. Empty if that read fails. */
    val spoolsById: StateFlow<Map<Int, Spool>> = _spoolsById.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            if (_state.value !is AmsListState.Success) _state.value = AmsListState.Loading
            when (val result = ams.getAms()) {
                is ApiResult.Success -> {
                    _state.value = AmsListState.Success(result.data)
                    (spools.getSpools() as? ApiResult.Success)?.let { r -> _spoolsById.value = r.data.associateBy { it.id } }
                }
                is ApiResult.Error ->
                    // Keep showing the last data when a refresh fails.
                    if (_state.value !is AmsListState.Success) _state.value = AmsListState.Error(result.toUiErrorState())
            }
        }
    }

    fun chooseSlot(unit: AmsUnit, trayId: Int) {
        _slotTarget.value = SlotTarget(unit, trayId)
        viewModelScope.launch {
            val all = (spools.getSpools() as? ApiResult.Success)?.data.orEmpty()
            _spoolChoices.value = all
        }
    }

    fun dismissSlot() {
        _slotTarget.value = null
    }

    class Factory(private val ams: AmsRepository, private val spools: SpoolRepository) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AmsViewModel(ams, spools) as T
    }
}
