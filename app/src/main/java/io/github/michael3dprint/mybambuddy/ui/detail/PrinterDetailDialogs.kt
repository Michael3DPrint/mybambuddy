package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.ui.common.ConfirmDialog
import io.github.michael3dprint.mybambuddy.ui.common.RefreshingImage
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** The full-screen image viewer and the cancel, pause/resume and speed dialogs. */
@Composable
internal fun PrinterDetailDialogs(
    currentPrinter: Printer,
    imageLoader: ImageLoader?,
    snapshotTimestamp: Long,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSetSpeed: (Int) -> Unit,
    showFullScreenImageState: MutableState<Boolean>,
    showCancelDialogState: MutableState<Boolean>,
    showPauseResumeDialogState: MutableState<Boolean>,
    showSpeedPresetDialogState: MutableState<Boolean>,
) {
    var showFullScreenImage by showFullScreenImageState
    var showCancelDialog by showCancelDialogState
    var showPauseResumeDialog by showPauseResumeDialogState
    var showSpeedPresetDialog by showSpeedPresetDialogState
    val isPaused = currentPrinter.state == PrinterState.PAUSED
    // Full Screen Image Viewer Dialog
    if (showFullScreenImage && currentPrinter.snapshotUrl.isNotEmpty()) {
        Dialog(
            onDismissRequest = { showFullScreenImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                RefreshingImage(
                    url = currentPrinter.snapshotUrl,
                    cacheKey = currentPrinter.id,
                    skipInitialFetchIfCached = true,
                    refreshKey = snapshotTimestamp,
                    imageLoader = imageLoader,
                    contentDescription = stringResource(R.string.detail_full_camera_snapshot),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )

                IconButton(
                    onClick = { showFullScreenImage = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimens.Space.l)
                        .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.large),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.detail_close),
                        tint = Color.White,
                    )
                }
            }
        }
    }

    // Pause / Resume Confirmation Dialog
    if (showPauseResumeDialog) {
        val actionName = if (isPaused) "resume" else "pause"
        ConfirmDialog(
            title = if (isPaused) "Resume Print" else "Pause Print",
            confirmLabel = if (isPaused) "Resume" else "Pause",
            onConfirm = {
                showPauseResumeDialog = false
                if (isPaused) onResumeClick() else onPauseClick()
            },
            onCancel = { showPauseResumeDialog = false },
        ) {
            Text("Are you sure you want to $actionName print '${currentPrinter.printName ?: currentPrinter.name}'?")
        }
    }

    // Cancel Confirmation Dialog
    if (showCancelDialog) {
        ConfirmDialog(
            title = stringResource(R.string.detail_cancel_print),
            confirmLabel = stringResource(R.string.detail_cancel_print),
            cancelLabel = stringResource(R.string.alerts_back),
            destructive = true,
            onConfirm = {
                showCancelDialog = false
                onCancelClick()
            },
            onCancel = { showCancelDialog = false },
        ) {
            Text("Are you sure you want to cancel print '${currentPrinter.printName ?: currentPrinter.name}'?")
        }
    }

    // Speed Preset Dialog
    if (showSpeedPresetDialog) {
        // Preselect the printer's current speed (Standard if unknown).
        var selectedMode by remember(currentPrinter.speedLevel) { mutableIntStateOf(currentPrinter.speedLevel ?: 2) }
        ConfirmDialog(
            title = stringResource(R.string.detail_select_print_speed),
            confirmLabel = stringResource(R.string.detail_apply),
            onConfirm = {
                showSpeedPresetDialog = false
                onSetSpeed(selectedMode)
            },
            onCancel = { showSpeedPresetDialog = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
                val speeds = listOf(
                    1 to "Silent (50%)",
                    2 to "Standard (100%)",
                    3 to "Sport (124%)",
                    4 to "Ludicrous (166%)",
                )
                speeds.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { selectedMode = mode }
                            .padding(vertical = Dimens.Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (selectedMode == mode),
                            onClick = { selectedMode = mode },
                        )
                        Spacer(modifier = Modifier.width(Dimens.Space.s))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
