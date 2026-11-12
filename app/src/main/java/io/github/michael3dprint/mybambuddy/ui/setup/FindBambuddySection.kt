package io.github.michael3dprint.mybambuddy.ui.setup

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.michael3dprint.mybambuddy.data.discovery.OkHttpHealthProbe
import io.github.michael3dprint.mybambuddy.domain.network.BambuddyFinder
import io.github.michael3dprint.mybambuddy.platform.network.AndroidNetworkInfo
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val LOCAL_NETWORK_SDK = 37

/** The welcome and checks view model, built with the phone's real network reader and a plain health probe. */
@Composable
fun rememberWelcomeViewModel(): WelcomeViewModel {
    val app = LocalContext.current.applicationContext
    val factory = remember { WelcomeViewModel.Factory(AndroidNetworkInfo(app), BambuddyFinder(OkHttpHealthProbe())) }
    return viewModel(factory = factory)
}

/**
 * The Find Bambuddy button and its result: asks for Android's local-network permission first where it applies, sweeps the phone's own
 * network, and offers each server found through [onUseHost]. Used on the welcome screen and in Settings.
 */
@Composable
fun FindBambuddySection(viewModel: WelcomeViewModel, onUseHost: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.find(it) }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
        Button(
            onClick = {
                val needsPermission = Build.VERSION.SDK_INT >= LOCAL_NETWORK_SDK &&
                    ContextCompat.checkSelfPermission(context, LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) launcher.launch(LOCAL_NETWORK) else viewModel.find(true)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Find Bambuddy on this network") }
        when (val find = state.find) {
            FindState.Idle -> Unit
            is FindState.Scanning -> {
                Text("Looking for Bambuddy: ${find.done} of ${find.total} addresses checked.")
                OutlinedButton(onClick = viewModel::stopFind) { Text("Stop") }
            }
            is FindState.Found -> find.hosts.forEach { host ->
                Button(onClick = { onUseHost(host) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Use http://$host:${BambuddyFinder.DEFAULT_PORT}")
                }
            }
            FindState.NotFound -> Text(
                "Bambuddy was not found on port ${BambuddyFinder.DEFAULT_PORT} of this network. " +
                    "It only finds servers on the Wi-Fi you are on now; a server reached over Tailscale is not found this way. Type the address instead.",
            )
            is FindState.CouldNotScan -> Text(find.reason)
        }
    }
}
