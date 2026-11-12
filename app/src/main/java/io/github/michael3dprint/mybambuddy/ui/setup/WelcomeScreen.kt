package io.github.michael3dprint.mybambuddy.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michael3dprint.mybambuddy.domain.policy.StatusLevel
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.help.LocalOpenHelpTab
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** The first-launch welcome: what is needed, three read-only checks of this phone, Find Bambuddy, and links to the guides. */
@Composable
fun WelcomeScreen(viewModel: WelcomeViewModel, onUseHost: (String) -> Unit, onContinue: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openHelp = LocalOpenHelpTab.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = Dimens.Space.xl).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space.l),
    ) {
        Heading("Welcome to MyBambuddy", style = MaterialTheme.typography.headlineSmall)
        Text("This app controls your Bambu printer through your own Bambuddy server. Installing that server is done on a computer.")
        Heading("You need", style = MaterialTheme.typography.titleMedium)
        Text(
            "A running Bambuddy server.\nThe printer on, with Developer Mode on and an SD card in.\nYour home network, or Tailscale. There is no HTTPS, so keep it private.",
        )
        Heading("This phone", style = MaterialTheme.typography.titleMedium)
        Text(checkRows(state.checks).joinToString("\n") { it.first })
        FindBambuddySection(viewModel, onUseHost)
        OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("I have an address and key") }
        OutlinedButton(onClick = {
            openHelp(io.github.michael3dprint.mybambuddy.domain.help.HelpTab.GUIDE)
        }, modifier = Modifier.fillMaxWidth()) { Text("I need to set it up: guides") }
    }
}

/** The three phone checks as words with a level, so a row shows an icon shape as well as the words. */
internal fun checkRows(c: WelcomeChecks): List<Pair<String, StatusLevel>> = listOf(
    "Wi-Fi: " + (c.wifi?.let { "connected" } ?: "not connected, or could not be read") to
        if (c.wifi != null) StatusLevel.OK else StatusLevel.NEUTRAL,
    when (c.vpnActive) {
        true -> "VPN: a VPN is on" to StatusLevel.OK
        false -> "VPN: no VPN is on" to StatusLevel.NEUTRAL
        null -> "VPN: could not be read" to StatusLevel.NEUTRAL
    },
    when (c.tailscaleInstalled) {
        true -> "Tailscale app: installed" to StatusLevel.OK
        false -> "Tailscale app: not installed" to StatusLevel.NEUTRAL
        null -> "Tailscale app: could not be checked" to StatusLevel.NEUTRAL
    },
)
