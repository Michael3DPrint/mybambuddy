package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.policy.ReadBack
import io.github.michael3dprint.mybambuddy.domain.policy.SlotLoadPolicy
import io.github.michael3dprint.mybambuddy.domain.policy.SlotReadBack
import io.github.michael3dprint.mybambuddy.domain.repo.AmsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A spool waiting for the user to pick the AMS slot it goes into. [units] is empty if no AMS is available. */
data class PendingLoad(val spool: Spool, val units: List<AmsUnit>, val loading: Boolean = false)

/**
 * Loading a spool into an AMS slot, and clearing a slot's record. [requestLoad] is the only way into an assignment: it validates,
 * assesses the risks and asks the user to confirm, and nothing is sent until [confirmLoad]. [changed] fires whenever the AMS list
 * is worth refreshing.
 */
class SlotAssignmentViewModel(private val ams: AmsRepository) : ViewModel() {
    private val _pendingLoad = MutableStateFlow<PendingLoad?>(null)
    val pendingLoad: StateFlow<PendingLoad?> = _pendingLoad.asStateFlow()

    private val _confirmation = MutableStateFlow<PendingAssignment?>(null)
    val confirmation: StateFlow<PendingAssignment?> = _confirmation.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _changed = MutableSharedFlow<Unit>()
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    /** Starts "load this spool into a slot": fetches the AMS units so the user can pick one. */
    fun beginLoad(spool: Spool) {
        _pendingLoad.value = PendingLoad(spool, emptyList(), loading = true)
        viewModelScope.launch {
            val units = (ams.getAms() as? ApiResult.Success)?.data.orEmpty()
            _pendingLoad.value = PendingLoad(spool, units)
            if (units.isEmpty()) _messages.emit("No AMS available to load into.")
        }
    }

    fun cancelLoad() {
        _pendingLoad.value = null
    }

    /**
     * Validates and assesses a load into [unit]'s slot [trayId]. [units] is every AMS unit the screen knows, used for the warnings
     * (for example "this spool is already in another slot").
     */
    fun requestLoad(spool: Spool, unit: AmsUnit, trayId: Int, units: List<AmsUnit>) {
        val invalid = SlotLoadPolicy.validate(spool.id, unit.printerId, unit.amsId, trayId)
        val tray = unit.trays.firstOrNull { it.trayId == trayId }
        _pendingLoad.value = null
        if (invalid != null || tray == null) {
            viewModelScope.launch { _messages.emit(invalid ?: "That slot was not found.") }
            return
        }
        // Early check on the data shown; the repository repeats it on a fresh read before anything is sent.
        SlotLoadPolicy.preflight(unit.capabilities, tray, unit.printerName).firstOrNull()?.let { blocker ->
            viewModelScope.launch { _messages.emit(SlotLoadPolicy.message(blocker, unit.printerName)) }
            return
        }
        _confirmation.value =
            PendingAssignment(spool, unit, trayId, SlotLoadPolicy.assess(spool, unit, tray, units.ifEmpty { listOf(unit) }))
    }

    fun cancelConfirmation() {
        _confirmation.value = null
    }

    /**
     * Sends the confirmed assignment: a single attempt, never retried. Success only means Bambuddy saved it (the printer may still
     * refuse), so the slot is read back and the user is told what it actually shows.
     */
    fun confirmLoad() {
        val p = _confirmation.value ?: return
        _confirmation.value = null
        viewModelScope.launch {
            when (val result = ams.loadSpool(p.spool.id, p.unit.printerId, p.unit.amsId, p.trayId, p.unit.printerName)) {
                is ApiResult.Error -> _messages.emit(result.toUiErrorState().message)
                is ApiResult.Success -> {
                    _changed.emit(Unit)
                    if (result.data.pendingConfig == true) {
                        _messages.emit("Saved. The slot looks empty, so Bambuddy will set it up when you insert the spool.")
                    } else {
                        _messages.emit("Sent to the printer. Checking the slot…")
                        readBack(p)
                    }
                }
            }
        }
    }

    private suspend fun readBack(p: PendingAssignment) {
        var shown: AmsTray? = null
        var readAny = false
        for (wait in READ_BACK_DELAYS_MS) {
            delay(wait)
            // One status request for this printer per look, never the whole AMS list, and never the saved copy.
            val reply = ams.getTray(p.unit.printerId, p.unit.amsId, p.trayId) as? ApiResult.Success ?: continue
            readAny = true
            shown = reply.data
            if (SlotReadBack.compare(p.spool, shown) == ReadBack.CONFIRMED) {
                _messages.emit("Printer confirmed: ${p.spool.title} is in ${p.unit.name} · slot ${p.trayId + 1}.")
                _changed.emit(Unit)
                return
            }
        }
        if (!readAny) {
            _messages.emit("Saved, but the slot couldn't be re-read to confirm it. Check the printer's screen.")
            _changed.emit(Unit)
            return
        }
        val now = shown?.takeUnless { it.isEmpty }?.label ?: "nothing"
        _messages.emit("Saved, but the printer still shows $now in that slot. It may have refused the change.")
        _changed.emit(Unit)
    }

    /** Clears the record for a slot. Nothing is sent to the printer. */
    fun unload(unit: AmsUnit, trayId: Int, spoolId: Int) {
        viewModelScope.launch {
            when (val result = ams.unloadSpool(spoolId, unit.printerId, unit.amsId, trayId)) {
                is ApiResult.Success -> {
                    _messages.emit("Unloaded (record cleared; nothing sent to the printer)")
                    _changed.emit(Unit)
                }
                is ApiResult.Error -> _messages.emit(result.toUiErrorState().message)
            }
        }
    }

    private companion object {
        /** Waits before each look at the slot after an assignment: about 3 s, then about 10 s in all. Bambuddy waits up to 30 s itself. */
        val READ_BACK_DELAYS_MS = listOf(3_000L, 7_000L)
    }

    class Factory(private val ams: AmsRepository) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SlotAssignmentViewModel(ams) as T
    }
}
