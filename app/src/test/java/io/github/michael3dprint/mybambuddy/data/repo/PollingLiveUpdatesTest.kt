package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.PrinterApi
import io.github.michael3dprint.mybambuddy.domain.policy.Backoff
import io.github.michael3dprint.mybambuddy.testutil.TestSettings
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PollingLiveUpdatesTest {
    private lateinit var server: MockWebServer
    private lateinit var updates: PollingLiveUpdates

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val url = server.url("/").toString()
        val api = ApiClient.createService(ApiClient.createOkHttpClient(TestSettings(url)), PrinterApi::class.java, baseUrl = url)
        // A tiny backoff so the test does not wait seconds between polls.
        updates = PollingLiveUpdates(api, Backoff(maxMillis = 5, jitter = 0.0))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `each poll emits the printers list`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":"1","name":"A"}]"""))
        server.enqueue(MockResponse().setBody("""[{"id":"1","name":"A"},{"id":"2","name":"B"}]"""))
        val results = updates.pollPrinters(1).take(2).toList()
        assertEquals(1, (results[0] as ApiResult.Success).data.size)
        assertEquals(2, (results[1] as ApiResult.Success).data.size)
    }

    @Test
    fun `a failed poll is emitted as an error and the stream carries on`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("""[{"id":"1","name":"A"}]"""))
        val results = updates.pollPrinters(1).take(2).toList()
        assertTrue(results[0] is ApiResult.Error)
        assertTrue(results[1] is ApiResult.Success)
    }

    @Test
    fun `a reply with no list gives an empty list, not a failure`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"count":0}"""))
        val first = updates.pollPrinters(1).take(1).toList().single()
        assertEquals(emptyList<Any>(), (first as ApiResult.Success).data)
    }
}
