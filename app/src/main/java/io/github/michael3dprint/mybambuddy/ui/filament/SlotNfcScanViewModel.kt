package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.nfc.NfcGateway
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where a slot's tag scan stands. */
sealed interface SlotScanState {
    data object Idle : SlotScanState

    /** Waiting for a tag to be held against the phone, to assign its spool to [target]. */
    data class Waiting(val target: SlotTarget) : SlotScanState

    data class Problem(val message: String) : SlotScanState
}

/** A scanned spool and the slot it was scanned for. The assignment itself still goes through the usual checks and confirmation. */
data class SlotScanResult(val spool: Spool, val target: SlotTarget)

/**
 * Reads a spool's NFC tag to assign that spool to one chosen slot (design 33). Read only: it listens only while waiting, writes nothing
 * to the tag, and hands the matched spool on to the normal assignment check and confirmation.
 */
class SlotNfcScanViewModel(private val gateway: NfcGateway, private val spools: SpoolRepository) : ViewModel() {
    private val _state = MutableStateFlow<SlotScanState>(SlotScanState.Idle)
    val state: StateFlow<SlotScanState> = _state.asStateFlow()

    private val _found = MutableSharedFlow<SlotScanResult>(extraBufferCapacity = 1)

    /** A tag that belongs to a spool in the inventory was read. */
    val found: SharedFlow<SlotScanResult> = _found.asSharedFlow()

    private var listenJob: Job? = null

    /** True when the phone has NFC hardware, so the screen can hide the option otherwise. */
    val isAvailable: Boolean get() = gateway.isAvailable

    /** Starts listening for a tag for [target]. */
    fun scan(target: SlotTarget) {
        val problem = when {
            !gateway.isAvailable -> "This phone has no NFC."
            !gateway.isEnabled -> "NFC is turned off. Turn it on in the phone's settings."
            else -> null
        }
        if (problem != null) {
            _state.value = SlotScanState.Problem(problem)
            return
        }
        listenJob?.cancel()
        _state.value = SlotScanState.Waiting(target)
        gateway.setListening(true)
        listenJob = viewModelScope.launch {
            gateway.tags.collect { tag ->
                val known = (spools.getSpools() as? ApiResult.Success)?.data.orEmpty()
                val spool = known.firstOrNull { it.tagUid.equals(tag.uidHex, ignoreCase = true) }
                gateway.setListening(false)
                if (spool == null) {
                    _state.value =
                        SlotScanState.Problem("No spool in your inventory carries this tag. Add or link it on the Filament tab first.")
                } else {
                    _state.value = SlotScanState.Idle
                    _found.emit(SlotScanResult(spool, target))
                }
                listenJob?.cancel()
            }
        }
    }

    /** Stops listening and closes the message. */
    fun cancel() {
        listenJob?.cancel()
        gateway.setListening(false)
        _state.value = SlotScanState.Idle
    }

    override fun onCleared() {
        gateway.setListening(false)
    }

    class Factory(private val gateway: NfcGateway, private val spools: SpoolRepository) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SlotNfcScanViewModel(gateway, spools) as T
    }
}
