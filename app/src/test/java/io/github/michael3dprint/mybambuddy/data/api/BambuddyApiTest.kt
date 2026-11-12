package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BambuddyApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var systemApi: SystemApi
    private lateinit var printerApi: PrinterApi
    private lateinit var controlApi: PrinterControlApi
    private lateinit var historyApi: HistoryApi
    private lateinit var cameraApi: CameraApi

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString()
        fakeSettingsRepository = FakeSettingsRepository(
            initialServerUrl = baseUrl,
            initialApiKey = "my-secret-key-123",
        )

        val okHttpClient = ApiClient.createOkHttpClient(fakeSettingsRepository)
        val client = okHttpClient
        systemApi = ApiClient.createService(client, SystemApi::class.java, baseUrl = baseUrl)
        printerApi = ApiClient.createService(client, PrinterApi::class.java, baseUrl = baseUrl)
        controlApi = ApiClient.createService(client, PrinterControlApi::class.java, baseUrl = baseUrl)
        historyApi = ApiClient.createService(client, HistoryApi::class.java, baseUrl = baseUrl)
        cameraApi = ApiClient.createService(client, CameraApi::class.java, baseUrl = baseUrl)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getHealth sends X-API-Key header and parses health response`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status": "ok", "message": "Server running"}"""),
        )

        val result = safeApiCall { systemApi.getHealth() }

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals("ok", success.data.status)
        assertEquals("Server running", success.data.message)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/v1/health", recordedRequest.path)
        assertEquals("my-secret-key-123", recordedRequest.getHeader("X-API-Key"))
    }

    @Test
    fun `getHealth handles plain text OK response without failing`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("OK"),
        )

        val result = safeApiCall { systemApi.getHealth() }

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals("OK", success.data.status)
        assertEquals(true, success.data.ok)
    }

    @Test
    fun `getHealth handles plain text healthy response`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("healthy"),
        )

        val result = safeApiCall { systemApi.getHealth() }

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals("healthy", success.data.status)
        assertEquals(true, success.data.ok)
    }

    @Test
    fun `getHealth handles boolean true response`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("true"),
        )

        val result = safeApiCall { systemApi.getHealth() }

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals("ok", success.data.status)
        assertEquals(true, success.data.ok)
    }

    @Test
    fun `returns helpful error when server responds with HTML`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("<!DOCTYPE html><html><body>Login Page</body></html>"),
        )

        val result = safeApiCall { systemApi.getHealth() }

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(ApiErrorType.PARSE_ERROR, error.errorType)
        assertTrue(error.message.contains("Received invalid JSON response from server"))
    }

    @Test
    fun `url normalization handles various base url formats`() = runBlocking {
        val serverPort = mockWebServer.port
        val formats = listOf(
            "http://localhost:$serverPort",
            "http://localhost:$serverPort/",
            "http://localhost:$serverPort/api/v1",
            "http://localhost:$serverPort/api/v1/",
            "localhost:$serverPort",
        )

        for (urlFormat in formats) {
            fakeSettingsRepository.saveServerUrl(urlFormat)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"status": "ok"}"""),
            )

            val result = safeApiCall { systemApi.getHealth() }
            assertTrue("Failed for URL format: $urlFormat", result is ApiResult.Success)

            val recordedRequest = mockWebServer.takeRequest()
            assertEquals("/api/v1/health", recordedRequest.path)
        }
    }

    @Test
    fun `getSystemInfo returns system info successfully`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"version": "1.2.3", "system": "Linux", "appName": "PrintDeck"}"""),
        )

        val result = safeApiCall { systemApi.getSystemInfo() }

        assertTrue(result is ApiResult.Success)
        val sysInfo = (result as ApiResult.Success).data
        assertEquals("1.2.3", sysInfo.version)
        assertEquals("Linux", sysInfo.system)
        assertEquals("PrintDeck", sysInfo.appName)
    }

    @Test
    fun `getSystemInfo handles plain text response`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("BambuddyServer"),
        )

        val result = safeApiCall { systemApi.getSystemInfo() }

        assertTrue(result is ApiResult.Success)
        val sysInfo = (result as ApiResult.Success).data
        assertEquals("BambuddyServer", sysInfo.appName)
        assertEquals("ok", sysInfo.status)
    }

    @Test
    fun `getSystemInfo handles boolean true response`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("true"),
        )

        val result = safeApiCall { systemApi.getSystemInfo() }

        assertTrue(result is ApiResult.Success)
        val sysInfo = (result as ApiResult.Success).data
        assertEquals("ok", sysInfo.status)
    }

    @Test
    fun `getPrinters parses list of printers`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"printers": [{"id": "p1", "name": "Bambu X1C", "model": "X1C", "state": "idle"}], "count": 1}"""),
        )

        val result = safeApiCall { printerApi.getPrinters() }

        assertTrue(result is ApiResult.Success)
        val printersResponse = (result as ApiResult.Success).data
        assertEquals(1, printersResponse.count)
        assertNotNull(printersResponse.printers)
        assertEquals(1, printersResponse.printers?.size)
        assertEquals("Bambu X1C", printersResponse.printers?.first()?.name)
    }

    @Test
    fun `getPrinter sends GET request to correct path`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id": "p123", "name": "Lab X1C"}"""),
        )

        val result = safeApiCall { printerApi.getPrinter("p123") }

        assertTrue(result is ApiResult.Success)
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("/api/v1/printers/p123", recordedRequest.path)
    }

    @Test
    fun `getPrinterStatus sends GET request to correct path`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"state": "RUNNING"}"""),
        )

        val result = safeApiCall { printerApi.getPrinterStatus("p123") }

        assertTrue(result is ApiResult.Success)
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("/api/v1/printers/p123/status", recordedRequest.path)
    }

    @Test
    fun `refreshStatus sends POST request to correct path`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val result = safeApiCall { controlApi.refreshStatus("p123") }

        assertTrue(result is ApiResult.Success)
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/v1/printers/p123/refresh-status", recordedRequest.path)
    }

    @Test
    fun `clearHms sends POST request to correct path`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val result = safeApiCall { controlApi.clearHms("p123") }

        assertTrue(result is ApiResult.Success)
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/v1/printers/p123/hms/clear", recordedRequest.path)
    }

    @Test
    fun `clearPlate sends POST request to correct path`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val result = safeApiCall { controlApi.clearPlate("p123") }

        assertTrue(result is ApiResult.Success)
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/v1/printers/p123/clear-plate", recordedRequest.path)
    }

    @Test
    fun `setPrintSpeed sends POST request with mode query param`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val result = safeApiCall { controlApi.setPrintSpeed("p123", mode = 2) }

        assertTrue(result is ApiResult.Success)
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/api/v1/printers/p123/print-speed?mode=2", recordedRequest.path)
    }

    @Test
    fun `amsLoad and amsUnload send POST requests with the tray number`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        assertTrue(safeApiCall { controlApi.amsLoad("p123", trayId = 6) } is ApiResult.Success)
        assertTrue(safeApiCall { controlApi.amsUnload("p123", trayId = 6) } is ApiResult.Success)

        val load = mockWebServer.takeRequest()
        assertEquals("POST", load.method)
        assertEquals("/api/v1/printers/p123/ams/load?tray_id=6", load.path)
        val unload = mockWebServer.takeRequest()
        assertEquals("POST", unload.method)
        assertEquals("/api/v1/printers/p123/ams/unload?tray_id=6", unload.path)
    }

    @Test
    fun `getArchives sends GET request with all query parameters`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"archives": [], "count": 0}"""),
        )

        val result = safeApiCall {
            historyApi.getArchives(limit = 10, offset = 5, printerId = 7)
        }

        assertTrue(result is ApiResult.Success)
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("/api/v1/archives/?limit=10&offset=5&printer_id=7", recordedRequest.path)
    }

    @Test
    fun `401 Unauthorized returns UNAUTHORIZED ApiResult Error`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": "Unauthorized key"}"""),
        )

        val result = safeApiCall { systemApi.getHealth() }

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(401, error.code)
        assertEquals(ApiErrorType.UNAUTHORIZED, error.errorType)
        assertEquals("Invalid API key (HTTP 401).", error.message)
    }

    @Test
    fun `404 Not Found returns NOT_FOUND ApiResult Error`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error": "Not Found"}"""),
        )

        val result = safeApiCall { printerApi.getPrinter("unknown_id") }

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(404, error.code)
        assertEquals(ApiErrorType.NOT_FOUND, error.errorType)
        assertEquals("Endpoint not found on server (HTTP 404).", error.message)
    }

    @Test
    fun `getPrinters parses raw JSON list of printers`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"id": "p1", "name": "Bambu P1P", "model": "P1P"}]"""),
        )

        val result = safeApiCall { printerApi.getPrinters() }

        assertTrue(result is ApiResult.Success)
        val printersResponse = (result as ApiResult.Success).data
        assertEquals(1, printersResponse.printers?.size)
        assertEquals("Bambu P1P", printersResponse.printers?.first()?.name)
    }

    @Test
    fun `getPrinters parses object with data key`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data": [{"id": "p2", "name": "Bambu A1", "model": "A1"}]}"""),
        )

        val result = safeApiCall { printerApi.getPrinters() }

        assertTrue(result is ApiResult.Success)
        val printersResponse = (result as ApiResult.Success).data
        assertEquals(1, printersResponse.printers?.size)
        assertEquals("Bambu A1", printersResponse.printers?.first()?.name)
    }

    @Test
    fun `getPrinterStatus parses status wrapper object`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status": {"state": "RUNNING", "progress": 75.0}}"""),
        )

        val result = safeApiCall { printerApi.getPrinterStatus("p1") }

        assertTrue(result is ApiResult.Success)
        val statusDto = (result as ApiResult.Success).data
        assertEquals("RUNNING", statusDto.effectiveState)
        assertEquals(75.0f, statusDto.progress)
    }

    @Test
    fun `getPrinterStatus parses data wrapper object`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data": {"state": "PAUSED", "progress": 20.0}}"""),
        )

        val result = safeApiCall { printerApi.getPrinterStatus("p1") }

        assertTrue(result is ApiResult.Success)
        val statusDto = (result as ApiResult.Success).data
        assertEquals("PAUSED", statusDto.effectiveState)
        assertEquals(20.0f, statusDto.progress)
    }

    @Test
    fun `malformed JSON returns plain language user error message`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{ malformed json }"),
        )

        val result = safeApiCall { printerApi.getPrinters() }

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(ApiErrorType.PARSE_ERROR, error.errorType)
        assertEquals(
            "Received invalid JSON response from server. Verify your server URL (e.g. http://host:8000) and API key.",
            error.message,
        )
    }

    private class FakeSettingsRepository(initialServerUrl: String, initialApiKey: String) : SettingsRepository {
        private val _serverUrlFlow = MutableStateFlow(initialServerUrl)
        private val _apiKeyFlow = MutableStateFlow(initialApiKey)
        private val _pollingIntervalFlow = MutableStateFlow(5)
        private val _themeModeFlow = MutableStateFlow(io.github.michael3dprint.mybambuddy.data.prefs.ThemeMode.SYSTEM)

        override val serverUrl: Flow<String> = _serverUrlFlow.asStateFlow()
        override val apiKey: Flow<String> = _apiKeyFlow.asStateFlow()
        override val pollingIntervalSeconds: Flow<Int> = _pollingIntervalFlow.asStateFlow()
        override val themeMode: Flow<io.github.michael3dprint.mybambuddy.data.prefs.ThemeMode> = _themeModeFlow.asStateFlow()

        override fun getServerUrlSync(): String = _serverUrlFlow.value
        override fun getApiKeySync(): String = _apiKeyFlow.value
        override suspend fun saveServerUrl(url: String) {
            _serverUrlFlow.value = url
        }

        override suspend fun saveApiKey(key: String, forceDelete: Boolean) {
            _apiKeyFlow.value = key
        }

        override suspend fun savePollingInterval(seconds: Int) {
            _pollingIntervalFlow.value = seconds
        }

        override suspend fun saveThemeMode(mode: io.github.michael3dprint.mybambuddy.data.prefs.ThemeMode) {
            _themeModeFlow.value = mode
        }

        override suspend fun saveSettings(url: String, key: String) {
            _serverUrlFlow.value = url
            _apiKeyFlow.value = key
        }
    }
}
