package io.github.michael3dprint.mybambuddy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState

/** A background with the text/icon colour that sits on it. */
data class ChipColors(val container: Color, val content: Color)

/** Meaning-carrying colours for printer states, defined for both light and dark so nothing is hard-coded in screens. */
data class StatusColors(
    val printing: ChipColors,
    val paused: ChipColors,
    val finished: ChipColors,
    val error: ChipColors,
    val offline: ChipColors,
    val unknown: ChipColors,
) {
    fun forState(state: PrinterState): ChipColors = when (state) {
        PrinterState.PRINTING -> printing
        PrinterState.PAUSED -> paused
        PrinterState.IDLE, PrinterState.FINISHED -> finished
        PrinterState.ERROR, PrinterState.FAILED -> error
        PrinterState.OFFLINE -> offline
        PrinterState.UNKNOWN -> unknown
    }

    companion object {
        val Light = StatusColors(
            printing = ChipColors(Color(0xFFC8F2D4), Color(0xFF00531F)),
            paused = ChipColors(Color(0xFFFFE9B3), Color(0xFF5B4300)),
            finished = ChipColors(Color(0xFFCFE6FF), Color(0xFF00315C)),
            error = ChipColors(Color(0xFFFFDAD6), Color(0xFF8C0009)),
            offline = ChipColors(Color(0xFFE2E8EA), Color(0xFF3D4649)),
            unknown = ChipColors(Color(0xFFE2E8EA), Color(0xFF3D4649)),
        )
        val Dark = StatusColors(
            printing = ChipColors(Color(0xFF0B3D1B), Color(0xFF9DEAB5)),
            paused = ChipColors(Color(0xFF4A3600), Color(0xFFFFDF9C)),
            finished = ChipColors(Color(0xFF0A2F55), Color(0xFFB8D8FF)),
            error = ChipColors(Color(0xFF5F1112), Color(0xFFFFDAD6)),
            offline = ChipColors(Color(0xFF2B3437), Color(0xFFD2DADD)),
            unknown = ChipColors(Color(0xFF2B3437), Color(0xFFD2DADD)),
        )
    }
}

val LocalStatusColors = staticCompositionLocalOf { StatusColors.Light }

/** `MaterialTheme.statusColors.forState(state)` */
val MaterialTheme.statusColors: StatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current
