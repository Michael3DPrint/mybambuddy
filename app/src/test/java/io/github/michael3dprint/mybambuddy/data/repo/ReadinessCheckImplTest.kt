package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.PrinterApi
import io.github.michael3dprint.mybambuddy.domain.model.ToolingMode
import io.github.michael3dprint.mybambuddy.testutil.TestSettings
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReadinessCheckImplTest {
    private lateinit var server: MockWebServer
    private lateinit var check: ReadinessCheckImpl

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val url = server.url("/").toString()
        check =
            ReadinessCheckImpl(
                ApiClient.createService(ApiClient.createOkHttpClient(TestSettings(url)), PrinterApi::class.java, baseUrl = url),
            )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun body(s: String) = server.enqueue(MockResponse().setBody(s))

    @Test
    fun `checkAll evaluates every printer and sends only GET requests`() = runBlocking {
        body("""{"printers":[{"id":"1","name":"X2D"},{"id":"2","name":"P1S"}],"count":2}""")
        body("""{"connected":true,"state":"IDLE","developer_mode":true}""")
        body("""{"connected":true,"state":"IDLE","developer_mode":false}""")
        val reports = (check.checkAll() as ApiResult.Success).data
        assertEquals(listOf(ToolingMode.FULL, ToolingMode.LIMITED), reports.map { it.mode })
        assertEquals(listOf("X2D", "P1S"), reports.map { it.printerName })
        repeat(3) { assertEquals("GET", server.takeRequest().method) }
    }

    @Test
    fun `a printer whose status cannot be read is UNKNOWN, not full`() = runBlocking {
        body("""{"printers":[{"id":"1","name":"X2D"}],"count":1}""")
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(ToolingMode.UNKNOWN, (check.checkAll() as ApiResult.Success).data.single().mode)
    }

    @Test
    fun `a failing printer list is an error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(check.checkAll() is ApiResult.Error)
    }

    @Test
    fun `printer names from the server are sanitised and capped`() = runBlocking {
        body("""{"printers":[{"id":"1","name":"A\u0007${"B".repeat(100)}"}],"count":1}""")
        body("""{"connected":true,"state":"IDLE","developer_mode":true}""")
        val name = (check.checkAll() as ApiResult.Success).data.single().printerName
        assertEquals(40, name.length)
        assertTrue(name.none { it.isISOControl() })
    }

    @Test
    fun `check reads one fresh status`() = runBlocking {
        body("""{"connected":true,"state":"RUNNING","developer_mode":false}""")
        val r = (check.check("7", "X2D") as ApiResult.Success).data
        assertEquals(ToolingMode.LIMITED, r.mode)
        assertEquals("/api/v1/printers/7/status", server.takeRequest().path)
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(check.check("7") is ApiResult.Error)
    }
}
