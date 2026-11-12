package io.github.michael3dprint.mybambuddy.ui.print

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The picture of the model on its plate, decoded off the main thread; says so in words when the file has none. */
@Composable
internal fun PrintPreview(bytes: ByteArray?, modifier: Modifier = Modifier, emptyText: String = "No preview in the file.") {
    val image by produceState<ImageBitmap?>(initialValue = null, bytes) {
        value = bytes?.let { data ->
            withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap() }
        }
    }
    val shown = image
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = "Preview of the model on its plate",
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxWidth().heightIn(max = PREVIEW_MAX_HEIGHT),
        )
    } else {
        Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val PREVIEW_MAX_HEIGHT = androidx.compose.ui.unit.Dp(220f)
