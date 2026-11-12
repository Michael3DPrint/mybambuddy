package io.github.michael3dprint.mybambuddy.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.michael3dprint.mybambuddy.domain.connectivity.OfflineState
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens
import java.text.DateFormat
import java.util.Date

/** One app-wide strip shown while the server can't be reached (with saved data on screen when there is a copy). */
@Composable
fun OfflineBanner(state: OfflineState, modifier: Modifier = Modifier) {
    val time = state.dataFrom?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
    AnimatedVisibility(
        visible = state.isOffline,
        enter = slideInVertically(tween(200)) { -it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(150)) { -it } + fadeOut(tween(150)),
    ) {
        // Below the status bar: the app draws edge to edge, so without this the strip sits under the clock and battery icons.
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            // A live region so the change to offline is announced (design 34).
            modifier = modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                text = if (time != null) {
                    "Offline — showing saved data from $time. Controls are disabled."
                } else {
                    "Can't reach the server and there is no saved data. Controls are disabled."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = Dimens.Space.l, vertical = Dimens.Space.s),
            )
        }
    }
}
