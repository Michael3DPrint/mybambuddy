package io.github.michael3dprint.mybambuddy

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.michael3dprint.mybambuddy.ui.common.rememberLargeFont

/** The bottom navigation bar of the main page: one item per [AppTab], with the selected tab highlighted. */
@Composable
fun AppBottomBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    // At large font sizes only the selected item shows its label, so five labels do not squeeze each other (design 34).
    val showAllLabels = !rememberLargeFont()
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        tabs.forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(imageVector = icon, contentDescription = label) },
                label = { Text(label) },
                alwaysShowLabel = showAllLabels,
            )
        }
    }
}

/** The navigation rail used on wide screens (tablets, unfolded foldables): the same tabs as [AppBottomBar], down the left side. */
@Composable
fun AppNavigationRail(selected: AppTab, onSelect: (AppTab) -> Unit) {
    val showAllLabels = !rememberLargeFont()
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        tabs.forEach { (tab, icon, label) ->
            NavigationRailItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(imageVector = icon, contentDescription = label) },
                label = { Text(label) },
                alwaysShowLabel = showAllLabels,
            )
        }
    }
}

private val tabs = listOf(
    Triple(AppTab.PRINTERS, Icons.Rounded.Print, "Printers"),
    Triple(AppTab.QUEUE, Icons.Rounded.Queue, "Queue"),
    Triple(AppTab.FILAMENT, Icons.Rounded.Inventory2, "Filament"),
    Triple(AppTab.HISTORY, Icons.Rounded.History, "History"),
    Triple(AppTab.SETTINGS, Icons.Rounded.Settings, "Settings"),
)
