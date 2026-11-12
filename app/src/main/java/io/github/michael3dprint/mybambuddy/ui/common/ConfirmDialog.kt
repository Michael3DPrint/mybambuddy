package io.github.michael3dprint.mybambuddy.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How a confirmation dialog's buttons are laid out (design 33). The gap is twice Google's 8 dp minimum between adjacent
 * targets and every button is at least Google's recommended 48 dp tall; both are checked by a test against those minimums.
 */
object DialogSpacing {
    /** The space between the Cancel and the confirm button. */
    val ActionGap: Dp = 16.dp

    /** The smallest height of a dialog button. */
    val MinButtonHeight: Dp = 48.dp

    /** The smallest width of a dialog button, so short labels still make a comfortable target. */
    val MinButtonWidth: Dp = 96.dp
}

/** Cancel is an outlined button beside a normal confirm, and a plain text button beside a destructive (red) one. */
fun cancelIsOutlined(destructive: Boolean): Boolean = !destructive

/**
 * The one confirmation dialog for the app. Buttons are end-aligned with [DialogSpacing.ActionGap] between them: Cancel on the left (outlined,
 * or text when the confirm is [destructive]) and the confirm on the right, filled, in the error colour when destructive. With a null
 * [confirmLabel] only Cancel shows. The body scrolls when it is tall, so the buttons always stay on screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = "Cancel",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onCancel, modifier = modifier) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(Modifier.padding(DIALOG_PADDING)) {
                Heading(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
                Spacer(Modifier.height(DIALOG_PADDING))
                // A FlowRow: on one line as before, but the buttons wrap onto a second line at large font sizes instead of overflowing.
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DialogSpacing.ActionGap, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    val size = Modifier.defaultMinSize(minWidth = DialogSpacing.MinButtonWidth, minHeight = DialogSpacing.MinButtonHeight)
                    if (cancelIsOutlined(destructive)) {
                        OutlinedButton(onClick = onCancel, modifier = size) { Text(cancelLabel) }
                    } else {
                        TextButton(onClick = onCancel, modifier = size) { Text(cancelLabel) }
                    }
                    if (confirmLabel != null) {
                        Button(
                            onClick = onConfirm,
                            enabled = confirmEnabled,
                            modifier = size,
                            colors = if (destructive) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                )
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                        ) { Text(confirmLabel) }
                    }
                }
            }
        }
    }
}

private val DIALOG_PADDING = 24.dp
