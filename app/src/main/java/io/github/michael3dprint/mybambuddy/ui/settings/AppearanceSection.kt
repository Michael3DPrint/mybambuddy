package io.github.michael3dprint.mybambuddy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.data.prefs.ThemeMode
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** Light, dark or system theme, and the wallpaper-colours switch. */
@Composable
internal fun AppearanceSection(
    uiState: SettingsUiState,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
) {
    // Theme Selector Section
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
        Heading(
            text = stringResource(R.string.settings_app_theme_mode),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(
                ThemeMode.SYSTEM to "System Default",
                ThemeMode.LIGHT to "Light",
                ThemeMode.DARK to "Dark",
            ).forEach { (mode, label) ->
                val selected = uiState.themeMode == mode
                val icon = when (mode) {
                    ThemeMode.SYSTEM -> Icons.Rounded.Settings
                    ThemeMode.LIGHT -> Icons.Rounded.LightMode
                    ThemeMode.DARK -> Icons.Rounded.DarkMode
                }

                FilterChip(
                    selected = selected,
                    onClick = { onThemeModeChanged(mode) },
                    label = {
                        Text(
                            text = label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (selected) Icons.Rounded.CheckCircle else icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = uiState.dynamicColor,
                role = Role.Switch,
                onValueChange = onDynamicColorChanged,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_use_wallpaper_colours), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_off_keeps_the_app_s),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = uiState.dynamicColor, onCheckedChange = null)
        }
    }
}
