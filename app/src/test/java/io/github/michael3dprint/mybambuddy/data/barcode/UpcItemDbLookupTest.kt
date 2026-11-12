package io.github.michael3dprint.mybambuddy.data.barcode

import io.github.michael3dprint.mybambuddy.domain.barcode.EanLookupResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpcItemDbLookupTest {
    private lateinit var server: MockWebServer
    private lateinit var lookup: UpcItemDbLookup

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        lookup = UpcItemDbLookup(baseUrl = server.url("/prod/trial/lookup").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `finds a title and sends only the digits`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":"OK","items":[{"title":"Bambu PLA Basic Jade White","brand":"Bambu Lab"}]}"""))
        val result = lookup.lookup("6975337030163")
        assertEquals(EanLookupResult.Found("Bambu PLA Basic Jade White"), result)
        assertEquals("/prod/trial/lookup?upc=6975337030163", server.takeRequest().path)
    }

    @Test
    fun `non digit input is refused before any request is made`() = runBlocking {
        listOf("1;DROP", "../x", "6975337030163&admin=1", "", "123").forEach {
            assertTrue(lookup.lookup(it) is EanLookupResult.Unavailable)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `rate limit server errors and garbage are handled`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("not json"))
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))
        assertEquals(EanLookupResult.Unavailable("daily limit reached"), lookup.lookup("6975337030163"))
        assertTrue(lookup.lookup("6975337030163") is EanLookupResult.Unavailable)
        assertTrue(lookup.lookup("6975337030163") is EanLookupResult.Unavailable)
        assertEquals(EanLookupResult.NotFound, lookup.lookup("6975337030163"))
    }

    @Test
    fun `oversized and hostile titles are capped and cleaned`() = runBlocking {
        val title = "A".repeat(5000) + "\u0000\u001b"
        server.enqueue(MockResponse().setBody("""{"items":[{"title":"$title"}]}"""))
        val found = lookup.lookup("6975337030163") as? EanLookupResult.Found
        assertTrue(found == null || found.title.length <= 120)
    }
}
