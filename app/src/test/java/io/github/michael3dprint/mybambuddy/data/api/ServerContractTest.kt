package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterStatusDto
import io.github.michael3dprint.mybambuddy.domain.mapper.mapToPrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.testutil.TestSettings
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests written against the REAL Bambuddy server contract (paths, units, fields, errors). */
class ServerContractTest {
    private lateinit var server: MockWebServer
    private lateinit var systemApi: SystemApi
    private lateinit var printerApi: PrinterApi
    private lateinit var controlApi: PrinterControlApi
    private lateinit var historyApi: HistoryApi
    private lateinit var cameraApi: CameraApi
    private lateinit var settings: TestSettings

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString()
        settings = TestSettings(baseUrl)
        val client = ApiClient.createOkHttpClient(settings)
        systemApi = ApiClient.createService(client, SystemApi::class.java, baseUrl = baseUrl)
        printerApi = ApiClient.createService(client, PrinterApi::class.java, baseUrl = baseUrl)
        controlApi = ApiClient.createService(client, PrinterControlApi::class.java, baseUrl = baseUrl)
        historyApi = ApiClient.createService(client, HistoryApi::class.java, baseUrl = baseUrl)
        cameraApi = ApiClient.createService(client, CameraApi::class.java, baseUrl = baseUrl)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Test
    fun `printers list keeps the trailing slash`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("printers.json")))
        val result = safeApiCall { printerApi.getPrinters() }
        assertTrue(result is ApiResult.Success)
        assertEquals("/api/v1/printers/", server.takeRequest().path)
        val printers = (result as ApiResult.Success).data.effectivePrinters
        assertEquals(1, printers.size)
        assertEquals("1", printers[0].id)
    }

    @Test
    fun `status path has no trailing slash`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        safeApiCall { printerApi.getPrinterStatus("5") }
        assertEquals("/api/v1/printers/5/status", server.takeRequest().path)
    }

    @Test
    fun `control endpoints use print prefix and stop`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true}""")) }
        safeUnitCall { controlApi.pausePrint("1") }
        safeUnitCall { controlApi.resumePrint("1") }
        safeUnitCall { controlApi.cancelPrint("1") }
        assertEquals("/api/v1/printers/1/print/pause", server.takeRequest().path)
        assertEquals("/api/v1/printers/1/print/resume", server.takeRequest().path)
        assertEquals("/api/v1/printers/1/print/stop", server.takeRequest().path)
    }

    @Test
    fun `empty 204 control response is success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(safeUnitCall { controlApi.pausePrint("1") } is ApiResult.Success)
    }

    @Test
    fun `403 maps to FORBIDDEN`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"detail":"Missing permission"}"""))
        val r = safeUnitCall { controlApi.pausePrint("1") } as ApiResult.Error
        assertEquals(ApiErrorType.FORBIDDEN, r.errorType)
    }

    @Test
    fun `400 keeps the server detail`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"detail":"Printer not connected"}"""))
        val r = safeUnitCall { controlApi.pausePrint("1") } as ApiResult.Error
        assertEquals(ApiErrorType.REJECTED, r.errorType)
        assertEquals("Printer not connected", r.serverDetail)
        assertEquals("Printer not connected", r.toUiErrorState().message)
    }

    @Test
    fun `503 maps to UNAVAILABLE`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        val r = safeApiCall { printerApi.getPrinterStatus("1") } as ApiResult.Error
        assertEquals(ApiErrorType.UNAVAILABLE, r.errorType)
    }

    @Test
    fun `archives use printer_id and trailing slash`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        val r = safeApiCall { historyApi.getArchives(limit = 50, offset = 0, printerId = 1) }
        assertTrue(r is ApiResult.Success)
        assertEquals("/api/v1/archives/?limit=50&offset=0&printer_id=1", server.takeRequest().path)
    }

    @Test
    fun `real status fixture parses`() {
        val dto = ApiClient.createMoshi().adapter(PrinterStatusDto::class.java).fromJson(fixture("status.json"))!!
        assertEquals("IDLE", dto.state)
        assertEquals(true, dto.connected)
        assertEquals(22f, dto.temperatures!!.nozzle)
        assertEquals(0f, dto.temperatures!!.nozzleTarget)
        assertEquals(21f, dto.temperatures!!.bed)
        assertEquals(2, dto.speedLevel)
        val status = mapToPrintStatus(dto)
        assertEquals(PrinterState.IDLE, status.state)
        assertEquals(1, status.hmsErrors.size)
        assertEquals("Error", status.hmsErrors[0].severityLabel)
    }

    @Test
    fun `remaining_time is minutes`() {
        val dto = ApiClient.createMoshi().adapter(PrinterStatusDto::class.java)
            .fromJson("""{"state":"RUNNING","remaining_time":72,"layer_num":12,"total_layers":100}""")!!
        val status = mapToPrintStatus(dto)
        assertEquals(PrinterState.PRINTING, status.state)
        assertEquals(4320L, status.remainingTimeSeconds)
        assertEquals("1h 12m left", status.remainingTimeFormatted)
        assertEquals(12, status.currentLayer)
    }

    @Test
    fun `state mapping`() {
        assertEquals(PrinterState.FINISHED, PrinterState.fromString("FINISH"))
        assertEquals(PrinterState.PRINTING, PrinterState.fromString("SLICING"))
        assertEquals(PrinterState.PAUSED, PrinterState.fromString("PAUSE"))
        // A failed print is its own state, not an error: the printer is ready for the next job (design 43).
        assertEquals(PrinterState.FAILED, PrinterState.fromString("FAILED"))
        assertEquals(PrinterState.ERROR, PrinterState.fromString("ERROR"))
        val offline = ApiClient.createMoshi().adapter(PrinterStatusDto::class.java)
            .fromJson("""{"connected":false,"state":"RUNNING"}""")!!
        assertEquals(PrinterState.OFFLINE, mapToPrintStatus(offline).state)
    }

    @Test
    fun `system info version is nested under app`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("system_info.json")))
        val r = safeApiCall { systemApi.getSystemInfo() } as ApiResult.Success
        assertEquals("1.3.0", r.data.effectiveVersion)
    }

    @Test
    fun `camera snapshot gets a stream token and a retry on 401`() = runBlocking {
        val provider = CameraTokenProvider(cameraApi)
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(DynamicUrlInterceptor(settings))
            .addInterceptor(CameraTokenInterceptor(provider))
            .build()
        server.enqueue(MockResponse().setBody("""{"token":"abc"}""")) // mint
        server.enqueue(MockResponse().setResponseCode(401)) // snapshot rejected
        server.enqueue(MockResponse().setBody("""{"token":"def"}""")) // re-mint
        server.enqueue(MockResponse().setBody("jpegbytes")) // snapshot ok
        val url = server.url("/api/v1/printers/1/camera/snapshot")
        val resp = client.newCall(okhttp3.Request.Builder().url(url).build()).execute()
        assertEquals(200, resp.code)
        assertEquals("/api/v1/printers/camera/stream-token", server.takeRequest().path)
        assertEquals("abc", server.takeRequest().requestUrl!!.queryParameter("token"))
        server.takeRequest()
        assertEquals("def", server.takeRequest().requestUrl!!.queryParameter("token"))
    }

    @Test
    fun `live stream requests also get a token`() = runBlocking {
        val provider = CameraTokenProvider(cameraApi)
        val client = okhttp3.OkHttpClient.Builder().addInterceptor(CameraTokenInterceptor(provider)).build()
        server.enqueue(MockResponse().setBody("""{"token":"abc"}"""))
        server.enqueue(MockResponse().setBody("--frame"))
        client.newCall(okhttp3.Request.Builder().url(server.url("/api/v1/printers/1/camera/stream?fps=5")).build()).execute().close()
        server.takeRequest()
        val streamReq = server.takeRequest().requestUrl!!
        assertEquals("abc", streamReq.queryParameter("token"))
        assertEquals("5", streamReq.queryParameter("fps"))
    }

    @Test
    fun `non snapshot requests never get a token`() {
        val provider = CameraTokenProvider(cameraApi)
        val client = okhttp3.OkHttpClient.Builder().addInterceptor(CameraTokenInterceptor(provider)).build()
        server.enqueue(MockResponse().setBody("x"))
        client.newCall(okhttp3.Request.Builder().url(server.url("/api/v1/archives/3/thumbnail")).build()).execute()
        assertNull(server.takeRequest().requestUrl!!.queryParameter("token"))
    }

    @Test
    fun `executeCommand validates the speed mode without a network call`() = runBlocking {
        val repo = io.github.michael3dprint.mybambuddy.data.repo.PrintControlRepositoryImpl(
            controlApi,
            io.github.michael3dprint.mybambuddy.testutil.AllowAllToolGate,
        )
        val bad1 = repo.executeCommand("1", io.github.michael3dprint.mybambuddy.domain.model.PrinterControlCommand.SetPrintSpeed(9))
        assertEquals(ApiErrorType.UNSUPPORTED, (bad1 as ApiResult.Error).errorType)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `executeCommand speed hits the right path`() = runBlocking {
        val repo = io.github.michael3dprint.mybambuddy.data.repo.PrintControlRepositoryImpl(
            controlApi,
            io.github.michael3dprint.mybambuddy.testutil.AllowAllToolGate,
        )
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        repo.executeCommand("1", io.github.michael3dprint.mybambuddy.domain.model.PrinterControlCommand.SetPrintSpeed(3))
        assertEquals("/api/v1/printers/1/print-speed?mode=3", server.takeRequest().path)
    }

    @Test
    fun `chamber light command posts on query`() = runBlocking {
        val repo = io.github.michael3dprint.mybambuddy.data.repo.PrintControlRepositoryImpl(
            controlApi,
            io.github.michael3dprint.mybambuddy.testutil.AllowAllToolGate,
        )
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        repo.executeCommand("1", io.github.michael3dprint.mybambuddy.domain.model.PrinterControlCommand.SetChamberLight(true))
        assertEquals("/api/v1/printers/1/chamber-light?on=true", server.takeRequest().path)
    }
}
