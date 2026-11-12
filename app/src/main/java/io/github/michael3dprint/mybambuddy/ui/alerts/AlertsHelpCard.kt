package io.github.michael3dprint.mybambuddy.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * Explains how to get instant alerts from Bambuddy (ntfy or Pushover) in four steps, with a button that opens Bambuddy's own web page
 * ([webAddress], null disables the button) and a Hide button. When [hidden] only a small link to bring it back is shown.
 */
@Composable
fun AlertsHelpCard(hidden: Boolean, webAddress: String?, onHiddenChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    if (hidden) {
        TextButton(onClick = { onHiddenChange(false) }, modifier = modifier) { Text(stringResource(R.string.alerts_help_show)) }
        return
    }
    val uriHandler = LocalUriHandler.current
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(Dimens.Space.l), verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
            Heading(stringResource(R.string.alerts_help_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.alerts_help_intro), style = MaterialTheme.typography.bodySmall)
            listOf(
                R.string.alerts_help_step1,
                R.string.alerts_help_step2,
                R.string.alerts_help_step3,
                R.string.alerts_help_step4,
            ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
                Button(onClick = { webAddress?.let(uriHandler::openUri) }, enabled = webAddress != null) {
                    Text(stringResource(R.string.alerts_help_open))
                }
                TextButton(onClick = { onHiddenChange(true) }) { Text(stringResource(R.string.alerts_help_hide)) }
            }
        }
    }
}
