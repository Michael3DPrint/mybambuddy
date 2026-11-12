package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.PrinterState

/** Which action buttons the printer overview shows for a printer state (design 38). */
enum class OverviewAction {
    /** A print is running or paused: Pause or Resume, and Cancel. */
    PAUSE_AND_CANCEL,

    /** The printer is in an error: only Cancel, which clears it. */
    CANCEL_ONLY,

    /** Nothing is active: the Print button. */
    PRINT,
    ;

    companion object {
        /** Pause and Cancel show only for an active print; every state with nothing active offers Print. */
        fun forState(state: PrinterState): OverviewAction = when (state) {
            PrinterState.PRINTING, PrinterState.PAUSED -> PAUSE_AND_CANCEL
            PrinterState.ERROR -> CANCEL_ONLY
            PrinterState.IDLE, PrinterState.FINISHED, PrinterState.FAILED, PrinterState.OFFLINE, PrinterState.UNKNOWN -> PRINT
        }
    }
}
