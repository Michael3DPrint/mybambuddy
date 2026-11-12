package io.github.michael3dprint.mybambuddy.ui.readiness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticReport
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticStatus
import io.github.michael3dprint.mybambuddy.domain.policy.DiagnosticAdvice
import io.github.michael3dprint.mybambuddy.domain.policy.SectionStatuses
import io.github.michael3dprint.mybambuddy.ui.common.StatusSection
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * The Connection diagnostics section (design 37): Not run, All passed or the number of problems at a glance; expanded, what it does, the Run
 * button and the result rows. It only runs when Run is tapped. The visible word is short; the spoken name is the full one.
 */
@Composable
fun DiagnosticsSection(state: DiagnosticsState?, onRun: () -> Unit, modifier: Modifier = Modifier, nested: Boolean = false) {
    val report = (state as? DiagnosticsState.Done)?.report
    val status = SectionStatuses.diagnostics(state is DiagnosticsState.Running, state is DiagnosticsState.Failed, report)
    StatusSection(CONNECTION_DIAGNOSTICS, status, modifier, nested) {
        Text(
            stringResource(R.string.readiness_bambuddy_tests_its_connection_to),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            null -> RunButton("Run", onRun)
            DiagnosticsState.Running -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.readiness_running), style = MaterialTheme.typography.bodyMedium)
            }
            is DiagnosticsState.Failed -> {
                Text(
                    "Couldn't run the diagnostics: ${state.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                RunButton("Try again", onRun)
            }
            is DiagnosticsState.Done -> {
                ReportRows(state.report)
                RunButton("Run again", onRun)
            }
        }
    }
}

@Composable
private fun RunButton(label: String, onRun: () -> Unit) {
    OutlinedButton(onClick = onRun, modifier = Modifier.semantics { contentDescription = "$label connection diagnostics" }) { Text(label) }
}

/** The section's title. */
const val CONNECTION_DIAGNOSTICS = "Connection diagnostics"

@Composable
private fun ReportRows(report: DiagnosticReport) {
    Text(summary(report), style = MaterialTheme.typography.titleSmall)
    if (report.checks.isEmpty()) {
        Text(stringResource(R.string.readiness_bambuddy_returned_no_checks), style = MaterialTheme.typography.bodySmall)
    }
    report.checks.forEach { check ->
        val advice = DiagnosticAdvice.forCheck(check.id, check.status)
        val (mark, color) = when (check.status) {
            DiagnosticStatus.PASS -> "✓" to MaterialTheme.colorScheme.onSurface
            DiagnosticStatus.WARN -> "!" to MaterialTheme.colorScheme.tertiary
            DiagnosticStatus.FAIL -> "✗" to MaterialTheme.colorScheme.error
            DiagnosticStatus.SKIP -> "–" to MaterialTheme.colorScheme.onSurfaceVariant
            DiagnosticStatus.UNKNOWN -> "?" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
                Text(mark, color = color)
                Text(advice.title, color = color, style = MaterialTheme.typography.bodyMedium)
            }
            advice.fix?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp),
                )
            }
        }
    }
}

/** The one-line verdict above the rows, from the counts so it never disagrees with them. */
internal fun summary(report: DiagnosticReport): String = when {
    report.problems > 0 || report.warnings > 0 ->
        listOfNotNull(
            report.problems.takeIf { it > 0 }?.let { if (it == 1) "1 problem" else "$it problems" },
            report.warnings.takeIf { it > 0 }?.let { if (it == 1) "1 warning" else "$it warnings" },
        ).joinToString(", ")
    report.checks.isNotEmpty() -> "All checks passed"
    else -> "No result"
}
