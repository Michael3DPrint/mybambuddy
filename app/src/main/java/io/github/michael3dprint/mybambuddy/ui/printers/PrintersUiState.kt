package io.github.michael3dprint.mybambuddy.ui.printers

import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterReadiness
import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState
import io.github.michael3dprint.mybambuddy.domain.policy.ReadinessEvaluator

/** What the Printers tab shows: loading, the printers, or an error. */
sealed interface PrintersUiState {
    data object Loading : PrintersUiState
    data class Success(
        val printers: List<Printer>,
        val isRefreshing: Boolean = false,
        /** Set when the last poll failed but we still show the previous data. */
        val staleError: UiErrorState? = null,
        /** The startup check per printer, worked out once when the list arrives instead of on every recomposition (standards section 2). */
        val readiness: List<PrinterReadiness> = printers.map { ReadinessEvaluator.evaluate(it.id, it.name, it.capabilities) },
    ) : PrintersUiState
    data class Error(val errorState: UiErrorState, val isRefreshing: Boolean = false) : PrintersUiState
}
