package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.policy.OverviewAction
import io.github.michael3dprint.mybambuddy.ui.print.LocalOpenPrintFlow
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** Pause or resume, and cancel, each disabled when its tool is off. */
@Composable
internal fun PrinterControlRow(
    currentPrinter: Printer,
    toolOn: (PrinterTool) -> Boolean,
    showCancelDialogState: MutableState<Boolean>,
    showPauseResumeDialogState: MutableState<Boolean>,
) {
    var showCancelDialog by showCancelDialogState
    var showPauseResumeDialog by showPauseResumeDialogState
    // Pause and Cancel exist only while something is active; otherwise the Print button opens the print flow (design 38).
    val action = OverviewAction.forState(currentPrinter.state)
    if (action == OverviewAction.PRINT) {
        val openPrintFlow = LocalOpenPrintFlow.current
        Button(
            onClick = { openPrintFlow(currentPrinter.id) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) { Text("Print") }
        return
    }
    val canControl = currentPrinter.state == PrinterState.PRINTING || currentPrinter.state == PrinterState.PAUSED
    val isPaused = currentPrinter.state == PrinterState.PAUSED

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space.m),
    ) {
        // Pause / Resume Toggle Button
        if (action == OverviewAction.PAUSE_AND_CANCEL) {
            Button(
                onClick = { showPauseResumeDialog = true },
                enabled = canControl && toolOn(PrinterTool.PAUSE_RESUME),
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(Dimens.Space.s))
                Text(if (isPaused) "Resume" else "Pause")
            }
        }

        // Cancel Print Button
        Button(
            onClick = { showCancelDialog = true },
            enabled = (canControl || currentPrinter.state == PrinterState.ERROR) && toolOn(PrinterTool.CANCEL_PRINT),
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(Dimens.Space.s))
            Text(stringResource(R.string.detail_cancel_print))
        }
    }
}
