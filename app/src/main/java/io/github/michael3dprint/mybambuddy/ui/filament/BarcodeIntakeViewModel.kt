package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.domain.barcode.BarcodeOutcome
import io.github.michael3dprint.mybambuddy.domain.barcode.BarcodeResolver
import io.github.michael3dprint.mybambuddy.domain.barcode.BarcodeScanner
import io.github.michael3dprint.mybambuddy.domain.barcode.OnlineLookupConsent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where barcode scanning stands: idle, busy, or waiting for the user to allow or refuse the online lookup. */
sealed interface IntakeState {
    data object Idle : IntakeState

    data object Busy : IntakeState

    /** Waiting for the user to allow or refuse the online lookup of [ean]. */
    data class AskConsent(val ean: String) : IntakeState
}

/**
 * Scan a barcode/QR on a box or spool and turn it into something the Add Spool sheet can use.
 * It only reports results ([outcomes]); filling the form is the screen's job.
 */
class BarcodeIntakeViewModel(private val scanner: BarcodeScanner, private val resolver: BarcodeResolver) : ViewModel() {
    private val _state = MutableStateFlow<IntakeState>(IntakeState.Idle)
    val state: StateFlow<IntakeState> = _state.asStateFlow()

    private val _outcomes = MutableSharedFlow<BarcodeOutcome>(extraBufferCapacity = 1)
    val outcomes: SharedFlow<BarcodeOutcome> = _outcomes.asSharedFlow()

    fun scan() {
        if (_state.value != IntakeState.Idle) return
        viewModelScope.launch {
            _state.value = IntakeState.Busy
            val raw = scanner.scan()
            if (raw == null) {
                _state.value = IntakeState.Idle
                return@launch
            }
            handle(resolver.resolve(raw))
        }
    }

    /** The user's answer to "look this barcode up online?". [consent] ASK means "just this once". */
    fun answerConsent(ean: String, allow: Boolean, consent: OnlineLookupConsent = OnlineLookupConsent.ASK) {
        viewModelScope.launch {
            if (consent != OnlineLookupConsent.ASK) resolver.remember(consent)
            if (!allow) {
                _state.value = IntakeState.Idle
                _outcomes.tryEmit(BarcodeOutcome.NotFound("No problem. Search the catalog by name instead."))
                return@launch
            }
            _state.value = IntakeState.Busy
            handle(resolver.resolveOnline(ean))
        }
    }

    private fun handle(outcome: BarcodeOutcome) {
        if (outcome is BarcodeOutcome.AskOnlineLookup) {
            _state.value = IntakeState.AskConsent(outcome.ean)
        } else {
            _state.value = IntakeState.Idle
            _outcomes.tryEmit(outcome)
        }
    }

    class Factory(private val scanner: BarcodeScanner, private val resolver: BarcodeResolver) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BarcodeIntakeViewModel(scanner, resolver) as T
    }
}
