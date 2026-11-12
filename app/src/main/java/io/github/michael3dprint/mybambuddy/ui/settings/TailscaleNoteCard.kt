package io.github.michael3dprint.mybambuddy.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** The note at the top of Settings reminding the user that the server address is a private tailnet address. */
@Composable
internal fun TailscaleNoteCard(serverUrl: String, connected: Boolean?) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val verdict = androidx.compose.runtime.remember(serverUrl, connected) {
        val info = io.github.michael3dprint.mybambuddy.platform.network.AndroidNetworkInfo(context)
        io.github.michael3dprint.mybambuddy.domain.network.NetworkVerdictRule.decide(
            host = io.github.michael3dprint.mybambuddy.domain.network.SubnetRules.hostOf(serverUrl),
            tailscaleInstalled = info.isInstalled(io.github.michael3dprint.mybambuddy.domain.network.NetworkVerdictRule.TAILSCALE_PACKAGE),
            vpnActive = info.vpnActive(),
            reachable = connected,
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(Dimens.Space.m))
        androidx.compose.foundation.layout.Column {
            Text(
                text = "Away from home, use Tailscale: the address should be your server's private tailnet name " +
                    "(for example http://my-host.tailnet-name.ts.net:8000).",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(text = verdict.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
