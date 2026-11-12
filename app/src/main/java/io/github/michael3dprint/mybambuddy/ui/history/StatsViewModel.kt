package io.github.michael3dprint.mybambuddy.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.PrintStats
import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState
import io.github.michael3dprint.mybambuddy.domain.policy.StatsPeriod
import io.github.michael3dprint.mybambuddy.domain.repo.StatsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the statistics card shows. */
sealed interface StatsState {
    data object Loading : StatsState

    data class Success(val stats: PrintStats) : StatsState

    data class Error(val error: UiErrorState) : StatsState
}

/** Loads statistics for the chosen period. Nothing is requested until the card asks, and a newer choice cancels an older request. */
class StatsViewModel(private val repository: StatsRepository) : ViewModel() {
    private val _period = MutableStateFlow(StatsPeriod.THIS_MONTH)

    /** The chosen period; it starts on this month. */
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    private val _state = MutableStateFlow<StatsState>(StatsState.Loading)

    /** The loading, loaded or failed state of the current period. */
    val state: StateFlow<StatsState> = _state.asStateFlow()

    private var job: Job? = null

    /** Loads the current period again (used when the card opens and by Retry). */
    fun refresh() = load()

    /** Switches to [period] and loads it. */
    fun select(period: StatsPeriod) {
        _period.value = period
        load()
    }

    private fun load() {
        job?.cancel()
        _state.value = StatsState.Loading
        val period = _period.value
        job = viewModelScope.launch {
            _state.value = when (val result = repository.getStats(period)) {
                is ApiResult.Success -> StatsState.Success(result.data)
                is ApiResult.Error -> StatsState.Error(result.toUiErrorState())
            }
        }
    }

    class Factory(private val repository: StatsRepository) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StatsViewModel(repository) as T
    }
}
