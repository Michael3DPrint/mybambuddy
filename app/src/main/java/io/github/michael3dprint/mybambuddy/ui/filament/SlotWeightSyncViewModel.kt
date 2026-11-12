package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.policy.WeightSyncPlan
import io.github.michael3dprint.mybambuddy.domain.policy.WeightSyncRule
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * "Update the inventory from the printer" for one Bambu slot (design 33). [prepare] only works out the plan with [WeightSyncRule] and
 * either shows it for confirmation or says why not; only [confirm] writes, once, as the user's own tap. Never automatic.
 */
class SlotWeightSyncViewModel(private val spools: SpoolRepository) : ViewModel() {
    private val _pending = MutableStateFlow<WeightSyncPlan.Ready?>(null)

    /** The update waiting for the user's confirmation, or null. */
    val pending: StateFlow<WeightSyncPlan.Ready?> = _pending.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** What happened, in words for the user. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emitted after the inventory was updated, so the screen can read it again. */
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    /** Works out the update for [tray] from [inventory]. Sends nothing: it waits for [confirm] or says why it can't. */
    fun prepare(tray: AmsTray, inventory: Collection<Spool>, printerBusy: Boolean) {
        viewModelScope.launch {
            when (val plan = WeightSyncRule.plan(tray, inventory.toList(), spools.canWeigh(), printerBusy)) {
                is WeightSyncPlan.Ready -> _pending.value = plan
                is WeightSyncPlan.Refused -> _messages.emit(plan.reason)
            }
        }
    }

    /** The user confirmed: sends one weight update. */
    fun confirm() {
        val plan = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch {
            when (val result = spools.updateWeight(plan.spool.id, plan.grossGrams)) {
                is ApiResult.Success -> {
                    _messages.emit("Updated ${plan.spool.title}: about ${plan.newRemainingGrams} g left.")
                    _changed.emit(Unit)
                }
                is ApiResult.Error -> _messages.emit(result.toUiErrorState(aboutPrinter = false).message)
            }
        }
    }

    /** The user backed out: nothing is written. */
    fun cancel() {
        _pending.value = null
    }

    class Factory(private val spools: SpoolRepository) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SlotWeightSyncViewModel(spools) as T
    }
}
