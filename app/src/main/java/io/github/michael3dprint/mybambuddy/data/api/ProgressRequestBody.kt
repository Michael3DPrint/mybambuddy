package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.domain.print.PrintSource
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/** Streams a [PrintSource] to the server and reports progress, so a large file never sits in memory. */
class ProgressRequestBody(private val source: PrintSource, private val onProgress: (Float) -> Unit) : RequestBody() {
    override fun contentType(): MediaType = "application/octet-stream".toMediaType()

    override fun contentLength(): Long = source.size ?: -1L

    override fun writeTo(sink: BufferedSink) {
        val total = source.size
        var sent = 0L
        source.open().source().use { input ->
            val buffer = okio.Buffer()
            while (true) {
                val read = input.read(buffer, CHUNK)
                if (read == -1L) break
                sink.write(buffer, read)
                sent += read
                if (total != null && total > 0) onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
            }
        }
    }

    private companion object {
        const val CHUNK = 64L * 1024
    }
}
