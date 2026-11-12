package io.github.michael3dprint.mybambuddy.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The spoken action of a navigation row: what happens when it is activated (design 48, pattern P4). */
internal fun navRowAction(title: String): String = "Open $title"

/**
 * A row that opens another page (design 48, pattern P4): leading icon, title, one line of supporting text and a trailing chevron. The whole
 * row is one button, at least 56 dp tall, and it announces what it opens. Only a row with a chevron is tappable anywhere in the app.
 */
@Composable
fun NavRow(title: String, supporting: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(supporting, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        modifier = modifier
            .heightIn(min = NAV_ROW_MIN_HEIGHT)
            .clickable(onClickLabel = navRowAction(title), role = Role.Button, onClick = onClick),
    )
}

private val NAV_ROW_MIN_HEIGHT = 56.dp
