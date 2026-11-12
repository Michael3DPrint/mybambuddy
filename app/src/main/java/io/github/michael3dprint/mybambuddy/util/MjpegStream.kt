package io.github.michael3dprint.mybambuddy.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import okhttp3.OkHttpClient
import okhttp3.Request

/** Reads Bambuddy's multipart/x-mixed-replace MJPEG stream and emits one bitmap per frame. */
object MjpegStream {
    private const val MAX_FRAME_BYTES = 8L * 1024 * 1024

    fun frames(client: OkHttpClient, url: String): Flow<ImageBitmap> = flow {
        val call = client.newCall(Request.Builder().url(url).build())
        // Closing the connection is what tells the server to stop the camera feed.
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val source = response.body.source()
            while (currentCoroutineContext().isActive) {
                var length = -1
                while (true) {
                    val line = source.readUtf8LineStrict()
                    if (line.isEmpty()) {
                        if (length >= 0) break else continue
                    }
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        length = line.substringAfter(':').trim().toIntOrNull() ?: -1
                    }
                }
                if (length <= 0 || length > MAX_FRAME_BYTES) throw IOException("Bad frame size $length")
                val bytes = source.readByteArray(length.toLong())
                SafeBitmap.decode(bytes)?.let { emit(it.asImageBitmap()) }
            }
        }
    }.flowOn(Dispatchers.IO)
}
