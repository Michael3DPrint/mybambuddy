package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.HmsStatus
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.policy.HmsExplainer
import io.github.michael3dprint.mybambuddy.domain.policy.SectionStatuses
import io.github.michael3dprint.mybambuddy.ui.common.ConfirmDialog
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.common.StatusSection
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * The Printer Status section (design 37): No errors with a check, or how many errors. Expanded, it lists the errors in one line each and
 * offers Clear HMS errors, which is off with a reason when the tools rule says clearing is not available and asks before it sends.
 */
@Composable
fun PrinterStatusSection(
    errors: List<HmsStatus>,
    state: PrinterState,
    clearEnabled: Boolean,
    reason: String?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirm by remember { mutableStateOf(false) }
    StatusSection(PRINTER_STATUS, SectionStatuses.printerStatus(errors, state), modifier) {
        if (errors.isEmpty()) {
            Text(
                when (state) {
                    PrinterState.ERROR -> "The printer is in an error state but reports no error codes."
                    PrinterState.FAILED ->
                        "The last print failed or was stopped. There are no errors to clear; the printer is ready for the next print."
                    else -> "The printer reports no errors."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.Space.xs)) {
                errors.take(MAX_LISTED).forEach {
                    val a = HmsExplainer.explain(it)
                    Spoken(collapsedLine(severityLabel(a.severity), a.area, a.title), style = MaterialTheme.typography.bodyMedium)
                }
                if (errors.size > MAX_LISTED) Text("+ ${errors.size - MAX_LISTED} more", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { confirm = true },
                enabled = clearEnabled,
                modifier = Modifier.semantics { contentDescription = "Clear HMS errors" },
            ) { Text(stringResource(R.string.detail_clear_hms_errors)) }
            if (!clearEnabled && !reason.isNullOrBlank()) {
                Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirm) {
        ClearErrorsDialog(onConfirm = {
            confirm = false
            onClear()
        }, onCancel = { confirm = false })
    }
}

/** The confirmation before printer errors are cleared; the Tools section and the overview's error card both use it so they say the same. */
@Composable
internal fun ClearErrorsDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.detail_clear_hms_errors),
        confirmLabel = stringResource(R.string.detail_clear),
        onConfirm = onConfirm,
        onCancel = onCancel,
    ) {
        Text(stringResource(R.string.detail_clear_hms_confirm))
    }
}

/** The section's title; the printer card and this section both call the part "Printer Status". */
const val PRINTER_STATUS = "Printer Status"

private const val MAX_LISTED = 5
