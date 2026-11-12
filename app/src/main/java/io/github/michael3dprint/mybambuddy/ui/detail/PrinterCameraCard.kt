package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.ui.common.RefreshingImage
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** The live camera or snapshot card, with the full-screen viewer trigger. */
@Composable
internal fun PrinterCameraCard(
    currentPrinter: Printer,
    imageLoader: ImageLoader?,
    cameraClient: okhttp3.OkHttpClient?,
    snapshotTimestamp: Long,
    onRefreshSnapshot: () -> Unit,
    liveViewState: MutableState<Boolean>,
    showFullScreenImageState: MutableState<Boolean>,
) {
    var liveView by liveViewState
    var showFullScreenImage by showFullScreenImageState
    // Camera Snapshot Card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = currentPrinter.snapshotUrl.isNotEmpty(), role = Role.Button) {
                showFullScreenImage = true
            },
    ) {
        if (liveView && cameraClient != null && currentPrinter.snapshotUrl.isNotEmpty()) {
            LiveCameraView(
                client = cameraClient,
                url = currentPrinter.snapshotUrl.replace("/camera/snapshot", "/camera/stream") + "?fps=5",
                modifier = Modifier.fillMaxSize(),
            )
        } else if (currentPrinter.snapshotUrl.isNotEmpty()) {
            RefreshingImage(
                url = currentPrinter.snapshotUrl,
                cacheKey = currentPrinter.id,
                refreshKey = snapshotTimestamp,
                imageLoader = imageLoader,
                contentDescription = "Camera snapshot for ${currentPrinter.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Dimens.Space.s),
            ) {
                Text(
                    text = stringResource(R.string.detail_tap_to_view_full_screen),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = Dimens.Space.s, vertical = Dimens.Space.xs),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(modifier = Modifier.height(Dimens.Space.s))
                Text(
                    text = stringResource(R.string.detail_no_camera_stream_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!liveView && currentPrinter.snapshotUrl.isNotEmpty()) {
            FilledTonalIconButton(
                onClick = onRefreshSnapshot,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Dimens.Space.s),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.detail_take_new_photo))
            }
        }
        if (cameraClient != null && currentPrinter.snapshotUrl.isNotEmpty()) {
            FilledTonalButton(
                onClick = { liveView = !liveView },
                contentPadding = PaddingValues(horizontal = Dimens.Space.m, vertical = 0.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.Space.s),
            ) {
                Text(if (liveView) "LIVE \u25CF  tap to stop" else "LIVE")
            }
        }
    }
}
