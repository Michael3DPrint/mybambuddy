package io.github.michael3dprint.mybambuddy.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.domain.model.PrinterReadiness
import io.github.michael3dprint.mybambuddy.domain.model.ToolingMode
import io.github.michael3dprint.mybambuddy.domain.policy.Availability
import io.github.michael3dprint.mybambuddy.domain.policy.RemoteControlWords
import io.github.michael3dprint.mybambuddy.domain.policy.ToolAvailability
import io.github.michael3dprint.mybambuddy.domain.policy.ToolStatus
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * The Remote control section (design 37): Full or Limited at a glance, and, when expanded, why and which controls depend on it. Only the
 * controls that need Developer LAN mode are listed, each with a check or a cross and the reason; always-available actions are left out.
 */
@Composable
fun RemoteControlSection(report: PrinterReadiness, modifier: Modifier = Modifier, nested: Boolean = false) {
    StatusSection(RemoteControlWords.NAME, RemoteControlWords.status(report.mode), modifier, nested) {
        Text(RemoteControlWords.explain(report.mode), style = MaterialTheme.typography.bodyMedium)
        if (report.capabilities.isKnown && report.mode != ToolingMode.OFFLINE) {
            report.tools.filter { it.tool.needsDeveloperMode }.forEach { ToolRow(it) }
        }
    }
}

@Composable
private fun ToolRow(status: ToolStatus) {
    val (mark, color) = when (status.availability) {
        Availability.AVAILABLE -> "✓" to MaterialTheme.colorScheme.onSurface
        Availability.LIMITED -> "!" to MaterialTheme.colorScheme.tertiary
        Availability.UNAVAILABLE -> "✗" to MaterialTheme.colorScheme.error
    }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
            Text(mark, color = color)
            Text(status.tool.label, color = color, style = MaterialTheme.typography.bodyMedium)
        }
        status.reason?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp),
            )
        }
    }
}

/** One line shown only when something is limited: names the printers and what to do. */
@Composable
fun ToolsBanner(printerNames: List<String>, modifier: Modifier = Modifier) {
    if (printerNames.isEmpty()) return
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            "Developer LAN mode is off on ${printerNames.joinToString()}. Pause, cancel, light, speed, drying and AMS filament " +
                "changes are switched off. ${ToolAvailability.DEVELOPER_MODE_FIX}",
            Modifier.padding(Dimens.Space.m),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
