package io.github.michael3dprint.mybambuddy.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.data.prefs.ThemeMode
import io.github.michael3dprint.mybambuddy.domain.help.HelpTab
import io.github.michael3dprint.mybambuddy.ui.common.AppTabRow
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.NavRow
import io.github.michael3dprint.mybambuddy.ui.common.politeLive
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens
import io.github.michael3dprint.mybambuddy.ui.theme.MyBambuddyTheme

/** Settings: server address and key, polling interval, theme and alerts. Connects the view model to [SettingsContent]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onSettingsSaved: () -> Unit = {},
    onOpenAlerts: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsContent(
        uiState = uiState,
        onServerUrlChanged = viewModel::onServerUrlChanged,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onToggleApiKeyVisibility = viewModel::toggleApiKeyVisibility,
        onPollingIntervalChanged = viewModel::onPollingIntervalChanged,
        onThemeModeChanged = viewModel::onThemeModeChanged,
        onDynamicColorChanged = viewModel::onDynamicColorChanged,
        onTestConnection = viewModel::testConnection,
        onOpenAlerts = onOpenAlerts,
        onSaveSettings = {
            viewModel.saveSettings()
            onSettingsSaved()
        },
        modifier = modifier,
    )
}

/** Draws the Settings screen from its state; every change is a callback and nothing is saved until the Save button. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onServerUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onToggleApiKeyVisibility: () -> Unit,
    onPollingIntervalChanged: (Int) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onSaveSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAlerts: () -> Unit = {},
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(Dimens.Space.m))
                        Heading(
                            text = stringResource(R.string.printers_settings),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        var tab by rememberSaveable { mutableStateOf(SettingsTab.CONNECTION) }
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AppTabRow(SettingsTab.entries.map { it.title }, tab.ordinal) { tab = SettingsTab.entries[it] }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = Dimens.Space.l),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space.l),
            ) {
                when (tab) {
                    SettingsTab.CONNECTION -> {
                        val openHelp = io.github.michael3dprint.mybambuddy.ui.help.LocalOpenHelpTab.current
                        Column {
                            NavRow(
                                "Setup help",
                                "Install guides, fixes for errors, find Bambuddy",
                                Icons.AutoMirrored.Rounded.HelpOutline,
                            ) {
                                openHelp(HelpTab.GUIDE)
                            }
                            NavRow("Find Bambuddy", "Look for a server on this Wi-Fi", Icons.Rounded.Search) { openHelp(HelpTab.PHONE) }
                        }
                        ConnectionSection(uiState, onServerUrlChanged, onApiKeyChanged, onToggleApiKeyVisibility, onTestConnection)
                        TailscaleNoteCard(uiState.serverUrl, uiState.testResultSuccess)
                    }
                    SettingsTab.APP -> {
                        NavRow(
                            "Alerts",
                            stringResource(R.string.settings_alerts_print_finished_errors_low),
                            Icons.Rounded.Notifications,
                            onClick = onOpenAlerts,
                        )
                        PollingSection(uiState, onPollingIntervalChanged)
                        AppearanceSection(uiState, onThemeModeChanged, onDynamicColorChanged)
                    }
                }
            }
            SaveBar(uiState, keyboardController?.let { { it.hide() } }, onSaveSettings)
        }
    }
}

private enum class SettingsTab(val title: String) {
    CONNECTION("Connection"),
    APP("App"),
}

/** The Save button and its confirmation, pinned under the tabs so Save is in reach on both. */
@Composable
private fun SaveBar(uiState: SettingsUiState, beforeSave: (() -> Unit)?, onSaveSettings: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = Dimens.Space.m),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s),
        ) {
            AnimatedVisibility(visible = uiState.isSaved && uiState.saveMessage != null) {
                uiState.saveMessage?.let { message ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.politeLive()) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.settings_saved),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(Dimens.Space.m))
                        Text(text = message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Button(
                onClick = {
                    beforeSave?.invoke()
                    onSaveSettings()
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(Dimens.touchTarget),
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(Dimens.Space.s))
                Text(
                    stringResource(R.string.settings_save_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Android Studio preview of the Settings screen with sample data. */
@Preview(showBackground = true)
@Composable
fun SettingsContentPreview() {
    MyBambuddyTheme {
        SettingsContent(
            uiState = SettingsUiState(
                serverUrl = "http://100.64.0.1:8000",
                apiKey = "example",
                pollingIntervalSeconds = 5,
                themeMode = ThemeMode.SYSTEM,
                testResultSuccess = true,
                testResultMessage = "Connected to Bambuddy Server (v1.0) successfully!",
                isSaved = true,
                saveMessage = "Settings saved successfully.",
            ),
            onServerUrlChanged = {},
            onApiKeyChanged = {},
            onToggleApiKeyVisibility = {},
            onPollingIntervalChanged = {},
            onThemeModeChanged = {},
            onDynamicColorChanged = {},
            onTestConnection = {},
            onSaveSettings = {},
        )
    }
}
