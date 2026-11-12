package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.util.MjpegStream
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient

/** Live MJPEG view. Streams only while the screen is visible (STARTED); leaving closes the connection. */
@Composable
fun LiveCameraView(client: OkHttpClient, url: String, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var frame by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            failed = false
            try {
                MjpegStream.frames(client, url).collect { frame = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val current = frame
        when {
            current != null -> Image(
                bitmap = current,
                contentDescription = stringResource(R.string.detail_live_camera),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            failed -> Text(
                stringResource(R.string.detail_live_view_unavailable_tap_live),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> CircularProgressIndicator()
        }
    }
}
