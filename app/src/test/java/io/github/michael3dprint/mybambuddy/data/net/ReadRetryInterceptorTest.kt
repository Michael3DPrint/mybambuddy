package io.github.michael3dprint.mybambuddy.data.net

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReadRetryInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .addInterceptor(ReadRetryInterceptor())
            .build()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `a read that is dropped by the network is repeated once and succeeds`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody("ok"))
        val response = client.newCall(Request.Builder().url(server.url("/x")).build()).execute()
        assertEquals("ok", response.body.string())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a write that is dropped is never repeated`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody("should not be reached"))
        val request = Request.Builder().url(server.url("/x")).post("{}".toRequestBody()).build()
        val failed = runCatching { client.newCall(request).execute() }
        assertTrue(failed.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a timeout is not retried`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.enqueue(MockResponse().setBody("late"))
        val failed = runCatching { client.newCall(Request.Builder().url(server.url("/x")).build()).execute() }
        assertTrue(failed.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an error status is returned as is and not retried`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("ok"))
        val response = client.newCall(Request.Builder().url(server.url("/x")).build()).execute()
        assertEquals(500, response.code)
        assertEquals(1, server.requestCount)
    }
}
