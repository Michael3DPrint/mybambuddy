package io.github.michael3dprint.mybambuddy.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.domain.policy.SectionStatus
import io.github.michael3dprint.mybambuddy.domain.policy.StatusLevel
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens
import io.github.michael3dprint.mybambuddy.ui.theme.LocalStatusColors

/** The icon for a status: a check, an exclamation, a cross or a dash, tinted from the status colours. Decorative: the status words are read instead. */
@Composable
fun StatusMark(level: StatusLevel, modifier: Modifier = Modifier) {
    Icon(
        imageVector = when (level) {
            StatusLevel.OK -> Icons.Default.CheckCircle
            StatusLevel.WARN -> Icons.Default.Warning
            StatusLevel.ERROR -> Icons.Default.Error
            StatusLevel.NEUTRAL -> Icons.Default.Remove
        },
        contentDescription = null,
        tint = statusTint(level),
        modifier = modifier.size(20.dp),
    )
}

/** The colour of a status icon. It sits on the card surface, and a build check proves it reaches 3:1 there in light and dark. */
@Composable
fun statusTint(level: StatusLevel): Color {
    val c = LocalStatusColors.current
    return when (level) {
        StatusLevel.OK -> c.printing.content
        StatusLevel.WARN -> c.paused.content
        StatusLevel.ERROR -> c.error.content
        StatusLevel.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * A Tools section (design 37): a heading, a status with its own icon and words, and a body that expands when the header is tapped. The header
 * is one button at least 48 dp tall that reads "<title>, <status>" and says Expanded or Collapsed; it is a disclosure, not a menu.
 */
@Composable
fun StatusSection(
    title: String,
    status: SectionStatus,
    modifier: Modifier = Modifier,
    nested: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    // A nested section sits inside another one (the Diagnostics group): no card of its own, a thin line above it, and a lighter heading.
    val frame: @Composable (@Composable () -> Unit) -> Unit = { inner ->
        if (nested) {
            Column(modifier.fillMaxWidth()) {
                HorizontalDivider()
                inner()
            }
        } else {
            Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { inner() }
        }
    }
    frame {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .clearAndSetSemantics {
                        heading()
                        role = Role.Button
                        contentDescription = "$title, ${status.text}"
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                        onClick(label = if (expanded) "Collapse" else "Expand") {
                            expanded = !expanded
                            true
                        }
                    }
                    .padding(horizontal = Dimens.Space.l, vertical = Dimens.Space.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s),
            ) {
                Text(
                    title,
                    Modifier.weight(1f),
                    style = if (nested) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleSmall,
                )
                StatusMark(status.level)
                Text(status.text, style = MaterialTheme.typography.labelLarge)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(expanded) {
                Column(
                    Modifier.padding(start = Dimens.Space.l, end = Dimens.Space.l, bottom = Dimens.Space.l),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space.s),
                    content = content,
                )
            }
        }
    }
}
