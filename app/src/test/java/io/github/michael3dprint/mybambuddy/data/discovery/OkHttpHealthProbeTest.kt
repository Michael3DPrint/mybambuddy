package io.github.michael3dprint.mybambuddy.data.discovery

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpHealthProbeTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `a Bambuddy health reply is recognised, with no key and only a GET of the health path`() = runTest {
        server.enqueue(MockResponse().setBody("{\"status\":\"healthy\"}"))
        assertTrue(OkHttpHealthProbe().isBambuddy(server.hostName, server.port))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/health", request.path)
        assertNull(request.getHeader("X-API-Key"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `another web server or an error is not Bambuddy`() = runTest {
        server.enqueue(MockResponse().setBody("<html>hello</html>"))
        assertFalse(OkHttpHealthProbe().isBambuddy(server.hostName, server.port))
        server.enqueue(MockResponse().setResponseCode(500).setBody("{\"status\":\"healthy\"}"))
        assertFalse(OkHttpHealthProbe().isBambuddy(server.hostName, server.port))
    }

    @Test
    fun `a redirect is not followed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://${server.hostName}:${server.port}/other"))
        assertFalse(OkHttpHealthProbe().isBambuddy(server.hostName, server.port))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an address that refuses the connection is simply false`() = runTest {
        val port = server.port
        server.shutdown()
        assertFalse(OkHttpHealthProbe().isBambuddy("127.0.0.1", port))
    }
}
