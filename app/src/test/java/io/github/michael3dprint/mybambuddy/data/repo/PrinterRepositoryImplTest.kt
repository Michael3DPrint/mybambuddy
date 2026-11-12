package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.PrinterApi
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDto
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.repo.LiveUpdates
import io.github.michael3dprint.mybambuddy.testutil.TestSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrinterRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: PrinterRepositoryImpl
    private var statusFails = false

    // Answers by path, so the repository's parallel status reads do not depend on request order.
    private val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty().substringBefore('?')
            return when {
                path.endsWith("/status") ->
                    if (statusFails) MockResponse().setResponseCode(500) else MockResponse().setBody(statusJson(path))
                path.endsWith("/printers/1") -> MockResponse().setBody("""{"id":"1","name":"X2D","model":"X2D"}""")
                path.endsWith("/printers/") || path.endsWith("/printers") ->
                    MockResponse().setBody("""[{"id":"1","name":"X2D","model":"X2D"},{"id":"2","name":"P1S","model":"P1S"}]""")
                else -> MockResponse().setResponseCode(404)
            }
        }

        private fun statusJson(path: String) = if ("/printers/1/" in
            path
        ) {
            """{"connected":true,"state":"RUNNING","progress":42.0}"""
        } else {
            """{"connected":true,"state":"IDLE"}"""
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer().also {
            it.dispatcher = dispatcher
            it.start()
        }
        val url = server.url("/").toString()
        val settings = TestSettings(url)
        val api = ApiClient.createService(ApiClient.createOkHttpClient(settings), PrinterApi::class.java, baseUrl = url)
        repo = PrinterRepositoryImpl(api, settings)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `fetchPrinters returns every printer with its own status`() = runBlocking {
        val printers = (repo.fetchPrinters() as ApiResult.Success).data
        assertEquals(listOf("1", "2"), printers.map { it.id }.sorted())
        assertEquals(PrinterState.PRINTING, printers.first { it.id == "1" }.state)
        assertEquals(42, printers.first { it.id == "1" }.progress)
        assertEquals(PrinterState.IDLE, printers.first { it.id == "2" }.state)
    }

    @Test
    fun `a printer whose status cannot be read is still listed`() = runBlocking {
        statusFails = true
        val printers = (repo.fetchPrinters() as ApiResult.Success).data
        assertEquals(2, printers.size)
    }

    @Test
    fun `fetchPrinter combines the row and the status, and survives a status failure`() = runBlocking {
        val ok = (repo.fetchPrinter("1") as ApiResult.Success).data
        assertEquals("X2D", ok.name)
        assertEquals(PrinterState.PRINTING, ok.state)
        statusFails = true
        val degraded = (repo.fetchPrinter("1") as ApiResult.Success).data
        assertEquals("X2D", degraded.name)
    }

    @Test
    fun `a missing printer is an error`() = runBlocking {
        assertTrue(repo.fetchPrinter("9") is ApiResult.Error)
    }

    @Test
    fun `fetchPrinterStatus maps the status and passes errors through`() = runBlocking {
        val status = (repo.fetchPrinterStatus("1") as ApiResult.Success).data
        assertEquals(PrinterState.PRINTING, status.state)
        statusFails = true
        assertTrue(repo.fetchPrinterStatus("1") is ApiResult.Error)
    }

    @Test
    fun `the stream maps each polled list and passes errors through`() = runBlocking {
        val url = server.url("/").toString()
        val settings = TestSettings(url)
        val api = ApiClient.createService(ApiClient.createOkHttpClient(settings), PrinterApi::class.java, baseUrl = url)
        val live = object : LiveUpdates {
            override fun pollPrinters(periodMillis: Long): Flow<ApiResult<List<PrinterDto>>> = flowOf(
                ApiResult.Success(listOf(PrinterDto(id = "1", name = "X2D"))),
                ApiResult.Error(message = "down", errorType = io.github.michael3dprint.mybambuddy.data.api.ApiErrorType.NETWORK_ERROR),
            )
        }
        val results = PrinterRepositoryImpl(api, settings, live).getPrintersFlow(1).toList()
        assertEquals(PrinterState.PRINTING, (results[0] as ApiResult.Success).data.single().state)
        assertTrue(results[1] is ApiResult.Error)
    }
}
