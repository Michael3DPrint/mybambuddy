package io.github.michael3dprint.mybambuddy.ui.readiness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.domain.model.PrinterReadiness
import io.github.michael3dprint.mybambuddy.domain.model.ToolingMode
import io.github.michael3dprint.mybambuddy.domain.policy.RemoteControlWords
import io.github.michael3dprint.mybambuddy.ui.common.StatusMark
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * One line on a printer's card, "Remote control: Full" (design 37): the same words as the Remote control section in the Tools tab, with a
 * check or an exclamation mark beside them. Tapping it opens that printer's Tools tab. It is one button for a screen reader.
 */
@Composable
fun RemoteControlLine(report: PrinterReadiness, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val container = when (report.mode) {
        ToolingMode.FULL -> MaterialTheme.colorScheme.surfaceVariant
        ToolingMode.LIMITED, ToolingMode.OFFLINE -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val line = RemoteControlWords.line(report)
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.small,
        color = container,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).clearAndSetSemantics {
            role = Role.Button
            contentDescription = "$line. Opens the Tools tab."
            onClick(label = "Open Tools") {
                onOpen()
                true
            }
        },
    ) {
        Row(
            Modifier.padding(horizontal = Dimens.Space.m, vertical = Dimens.Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s),
        ) {
            StatusMark(RemoteControlWords.status(report.mode).level)
            Text(line, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
