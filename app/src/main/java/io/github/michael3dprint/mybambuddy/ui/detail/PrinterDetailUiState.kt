package io.github.michael3dprint.mybambuddy.ui.detail

import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState

/** What the detail screen shows: loading, the printer, or an error. */
sealed interface PrinterDetailUiState {
    object Loading : PrinterDetailUiState
    data class Success(val printer: Printer, val snapshotTimestamp: Long = System.currentTimeMillis()) : PrinterDetailUiState
    data class Error(val errorState: UiErrorState) : PrinterDetailUiState
}
