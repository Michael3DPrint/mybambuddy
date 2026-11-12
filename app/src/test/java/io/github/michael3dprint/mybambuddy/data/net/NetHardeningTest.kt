package io.github.michael3dprint.mybambuddy.data.net

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetHardeningTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = OkHttpClient.Builder()
            .addInterceptor(ResponseSizeLimitInterceptor(maxBodyBytes = 1000, maxImageBytes = 5000))
            .retryOnConnectionFailure(false)
            .build()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun get(path: String = "/x") = client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Test
    fun `small responses pass untouched`() {
        server.enqueue(MockResponse().setBody("ok"))
        assertEquals("ok", get().use { it.body.string() })
    }

    @Test
    fun `a declared oversize body is refused before it is read`() {
        server.enqueue(MockResponse().setBody("x".repeat(1001)))
        val thrown = runCatching { get() }.exceptionOrNull()
        assertTrue(thrown is IOException)
    }

    @Test
    fun `a chunked body that grows past the cap is cut off`() {
        server.enqueue(MockResponse().setChunkedBody("y".repeat(3000), 500))
        val thrown = runCatching { get().use { it.body.string() } }.exceptionOrNull()
        assertTrue(thrown is IOException)
    }

    @Test
    fun `images get the larger cap and streams are not capped`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody("z".repeat(4000)))
        assertEquals(4000, get().use { it.body.bytes().size })
        server.enqueue(MockResponse().setHeader("Content-Type", "multipart/x-mixed-replace; boundary=frame").setBody("f".repeat(9000)))
        assertEquals(9000, get().use { it.body.bytes().size })
    }

    @Test
    fun `a dropped connection is not retried`() {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertTrue(runCatching { get() }.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `tokens and keys are redacted from log lines`() {
        assertEquals(
            "--> GET http://h/api/v1/printers/1/camera/snapshot?token=*** http/1.1",
            LogRedaction.redact("--> GET http://h/api/v1/printers/1/camera/snapshot?token=ZBzSqEv2zA6l http/1.1"),
        )
        assertEquals("GET /a?x=1&api_key=***&y=2", LogRedaction.redact("GET /a?x=1&api_key=SECRET&y=2"))
        assertEquals("GET /a?name=nozzle", LogRedaction.redact("GET /a?name=nozzle"))
    }

    @Test
    fun `redaction leaves normal text alone`() {
        assertFalse(LogRedaction.redact("<-- 200 OK http://h/api/v1/printers/ (50ms)").contains("***"))
    }
}
