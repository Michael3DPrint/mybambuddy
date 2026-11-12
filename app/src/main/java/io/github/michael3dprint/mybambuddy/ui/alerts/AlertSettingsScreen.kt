package io.github.michael3dprint.mybambuddy.ui.alerts

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.alerts.AlertSettings
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.SwitchRow
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * Settings for the app's background alerts: which events notify, with the notification permission asked for when alerts are
 * switched on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertSettingsScreen(viewModel: AlertSettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val helpHidden by viewModel.helpHidden.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.update { it.copy(enabled = granted) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Heading(stringResource(R.string.alerts_alerts)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.alerts_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Dimens.Space.l).verticalScroll(rememberScrollState())) {
            SwitchRow("Alerts", settings.enabled) { on ->
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.update { it.copy(enabled = on) }
                }
            }
            AlertsHelpCard(
                hidden = helpHidden,
                webAddress = viewModel.webAddress,
                onHiddenChange = viewModel::setHelpHidden,
                modifier = Modifier.padding(bottom = Dimens.Space.l),
            )
            Category("Print finished", settings.printFinished, settings, viewModel) { s, v -> s.copy(printFinished = v) }
            Category("Print failed or stopped", settings.printFailed, settings, viewModel) { s, v -> s.copy(printFailed = v) }
            Category("New printer error", settings.newError, settings, viewModel) { s, v -> s.copy(newError = v) }
            Category("Printer went offline", settings.printerOffline, settings, viewModel) { s, v -> s.copy(printerOffline = v) }
            Category("Filament running low", settings.lowFilament, settings, viewModel) { s, v -> s.copy(lowFilament = v) }
            Category("Maintenance due", settings.maintenanceDue, settings, viewModel) { s, v -> s.copy(maintenanceDue = v) }
        }
    }
}

@Composable
private fun Category(
    label: String,
    value: Boolean,
    settings: AlertSettings,
    viewModel: AlertSettingsViewModel,
    change: (AlertSettings, Boolean) -> AlertSettings,
) {
    SwitchRow(label, value, enabled = settings.enabled) { on -> viewModel.update { change(it, on) } }
}
