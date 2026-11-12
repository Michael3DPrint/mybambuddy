package io.github.michael3dprint.mybambuddy.data.net

import java.io.IOException
import java.io.InterruptedIOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Retries a read (GET or HEAD) once when the network drops it, which is how a connection the server already closed shows up
 * (`unexpected end of stream`). The client turns OkHttp's own retry off so a control command can never be sent twice; this puts back the
 * recovery for requests that are safe to repeat and nothing else. Timeouts are not retried: they would double the wait.
 * It must sit closest to the network so it only ever sees network failures.
 */
class ReadRetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" && request.method != "HEAD") return chain.proceed(request)
        return try {
            chain.proceed(request)
        } catch (e: InterruptedIOException) {
            throw e
        } catch (_: IOException) {
            chain.proceed(request)
        }
    }
}
