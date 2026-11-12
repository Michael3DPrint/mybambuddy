package io.github.michael3dprint.mybambuddy.ui.readiness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.michael3dprint.mybambuddy.domain.model.CheckLevel
import io.github.michael3dprint.mybambuddy.domain.model.SlicingCheck
import io.github.michael3dprint.mybambuddy.domain.policy.SectionStatuses
import io.github.michael3dprint.mybambuddy.ui.common.ErrorNotice
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.common.StatusSection
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens
import java.text.DateFormat
import java.util.Date

/**
 * The Slicing section (design 37, from design 36): Ready, Ready with notes or Not ready at a glance; expanded, each check for this printer with
 * the steps to fix a failed one. [printerName] limits the per-printer preset check to this printer.
 */
@Composable
fun SlicingSection(state: SlicingState, printerName: String, onCheck: () -> Unit, modifier: Modifier = Modifier, nested: Boolean = false) {
    val done = state as? SlicingState.Done
    val readiness = done?.readiness?.forPrinter(printerName)
    StatusSection(SLICING, SectionStatuses.slicing(readiness, state is SlicingState.Error), modifier, nested) {
        when (state) {
            SlicingState.Loading -> Text("Checking slicing…", style = MaterialTheme.typography.bodyMedium)
            is SlicingState.Error -> ErrorNotice(state.error, onCheck)
            is SlicingState.Done -> if (readiness != null) {
                readiness.firstProblem?.let {
                    Spoken("Start with: ${it.title}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                readiness.checks.forEach { CheckRow(it) }
                Text(
                    "Checked ${DateFormat.getTimeInstance(
                        DateFormat.SHORT,
                    ).format(Date(state.checkedAtMillis))}. This reads what Bambuddy reports; it changes nothing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (readiness.ready) {
                    OutlinedButton(
                        onClick = onCheck,
                        modifier = Modifier.semantics {
                            contentDescription = "Check slicing again"
                        },
                    ) { Text("Check again") }
                } else {
                    Button(
                        onClick = onCheck,
                        modifier = Modifier.semantics {
                            contentDescription = "Check slicing again"
                        },
                    ) { Text("Check again") }
                }
            }
        }
    }
}

/** The section's title. */
const val SLICING = "Slicing"

@Composable
private fun CheckRow(check: SlicingCheck) {
    val mark = when {
        check.passed && check.level == CheckLevel.INFO -> "i"
        check.passed -> "✓"
        check.level == CheckLevel.WARNING -> "!"
        check.level == CheckLevel.INFO -> "i"
        else -> "✗"
    }
    val failed = !check.passed && check.level != CheckLevel.INFO
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
            Text(mark, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Column {
                Spoken(check.title, style = MaterialTheme.typography.bodyMedium)
                Spoken(check.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (check.fixSteps.isNotEmpty()) {
            Column(Modifier.padding(start = Dimens.Space.l), verticalArrangement = Arrangement.spacedBy(Dimens.Space.xs)) {
                Heading("How to fix", style = MaterialTheme.typography.labelLarge)
                check.fixSteps.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall) }
                io.github.michael3dprint.mybambuddy.ui.help.HelpButton(
                    io.github.michael3dprint.mybambuddy.domain.help.HelpTopicId.T_SLICER,
                    "Slicer help",
                )
            }
        }
    }
}
