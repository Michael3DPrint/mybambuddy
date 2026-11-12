package io.github.michael3dprint.mybambuddy.ui.common

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.util.SnapshotCache

/**
 * Shows the newest image from [url]. The previous image stays on screen until the new one has
 * finished loading, and stays if a refresh fails.
 *
 * - [cacheKey]: the last good image is stored under this key (memory + disk) and shown again
 *   immediately next time, even after leaving the screen or restarting the app.
 * - [refreshKey]: change it to fetch the SAME url again.
 * - [skipInitialFetchIfCached]: when true, opening the screen does NOT fetch if a stored image exists
 *   (only a later change of [refreshKey] fetches).
 */
@Composable
fun RefreshingImage(
    url: String,
    imageLoader: ImageLoader?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cacheKey: String? = null,
    refreshKey: Any? = null,
    skipInitialFetchIfCached: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Painter = painterResource(R.drawable.placeholder),
) {
    val context = LocalContext.current
    val loader = imageLoader ?: remember(context) { ImageLoader(context) }
    var shown by remember(cacheKey) { mutableStateOf<ImageBitmap?>(cacheKey?.let { SnapshotCache.getMemory(it) }) }
    var firstRun by remember(cacheKey) { mutableStateOf(true) }

    LaunchedEffect(url, refreshKey) {
        val isFirst = firstRun
        firstRun = false
        if (isFirst && shown == null && cacheKey != null) {
            shown = SnapshotCache.loadFromDisk(cacheKey)
        }
        if (isFirst && skipInitialFetchIfCached && shown != null) return@LaunchedEffect

        val request = ImageRequest.Builder(context)
            .data(url)
            // Coil's own caches are off: the same url is fetched again whenever we ask.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .allowHardware(false)
            .build()
        val result = loader.execute(request)
        if (result is SuccessResult) {
            val bitmap = result.drawable.toBitmap()
            shown = bitmap.asImageBitmap()
            if (cacheKey != null) SnapshotCache.put(cacheKey, bitmap)
        }
    }

    val current = shown
    if (current != null) {
        Image(bitmap = current, contentDescription = contentDescription, contentScale = contentScale, modifier = modifier)
    } else {
        Image(painter = placeholder, contentDescription = contentDescription, contentScale = contentScale, modifier = modifier)
    }
}
