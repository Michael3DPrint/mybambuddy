package io.github.michael3dprint.mybambuddy.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.help.ErrorHelp
import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState
import io.github.michael3dprint.mybambuddy.ui.help.HelpButton
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * The one way a screen shows a failed load (design 48): the title as a heading, the message, a Retry button when [onRetry] is given, and the
 * Help link for this kind of error. The message is announced when it appears.
 */
@Composable
fun ErrorNotice(error: UiErrorState, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.politeLive(), verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
        Heading(error.title, style = MaterialTheme.typography.titleMedium)
        Text(error.message, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
            onRetry?.let { Button(onClick = it) { Text(stringResource(R.string.filament_retry)) } }
            HelpButton(ErrorHelp.topicFor(error.kind))
        }
    }
}
