package io.github.michael3dprint.mybambuddy.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** The tab row used by every screen with peer parts (design 48, pattern P2): text labels, and it scrolls at large font sizes. */
@Composable
fun AppTabRow(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val tabs: @Composable () -> Unit = {
        titles.forEachIndexed { i, title -> Tab(selected = i == selected, onClick = { onSelect(i) }, text = { Text(title) }) }
    }
    if (rememberLargeFont()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selected,
            containerColor = MaterialTheme.colorScheme.surface,
            edgePadding = 0.dp,
            tabs = tabs,
        )
    } else {
        PrimaryTabRow(selectedTabIndex = selected, containerColor = MaterialTheme.colorScheme.surface, tabs = tabs)
    }
}
