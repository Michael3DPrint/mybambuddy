package io.github.michael3dprint.mybambuddy.data.net

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

/**
 * Refuses responses larger than a hard cap so a faulty or hostile server cannot exhaust the phone's memory.
 * JSON/other: [maxBodyBytes]; images: [maxImageBytes]. Live MJPEG streams (multipart/x-mixed-replace) are unbounded by design
 * and capped per frame by the stream reader instead.
 */
class ResponseSizeLimitInterceptor(private val maxBodyBytes: Long = 5L * 1024 * 1024, private val maxImageBytes: Long = 8L * 1024 * 1024) :
    Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body
        val type = body.contentType()
        if (type?.subtype == "x-mixed-replace") return response
        val limit = if (type?.type == "image") maxImageBytes else maxBodyBytes
        if (body.contentLength() > limit) {
            response.close()
            throw IOException("Response too large (${body.contentLength()} bytes, limit $limit)")
        }
        val limited = object : ForwardingSource(body.source()) {
            private var total = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read > 0) total += read
                if (total > limit) throw IOException("Response too large (limit $limit bytes)")
                return read
            }
        }.buffer()
        return response.newBuilder().body(limited.asResponseBody(type, body.contentLength())).build()
    }
}
