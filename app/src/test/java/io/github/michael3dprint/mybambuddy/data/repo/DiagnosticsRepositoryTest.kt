package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.DiagnosticApi
import io.github.michael3dprint.mybambuddy.data.api.dto.DiagnosticCheckDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDiagnosticDto
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticOverall
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticStatus
import io.github.michael3dprint.mybambuddy.testutil.TestSettings
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: DiagnosticsRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val url = server.url("/").toString()
        val api = ApiClient.createService(ApiClient.createOkHttpClient(TestSettings(url)), DiagnosticApi::class.java, baseUrl = url)
        repo = DiagnosticsRepositoryImpl(api)
    }

    @After
    fun tearDown() = server.shutdown()

    // Shape taken from Bambuddy v1.3.0 backend/app/schemas/printer.py (PrinterDiagnosticResult), not from a live reply.
    private val reply = """
        {"printer_id":1,"ip_address":"192.168.1.20","overall":"problems","checks":[
          {"id":"port_mqtt","status":"pass","params":{}},
          {"id":"developer_mode","status":"fail","params":{"reason":"x"}},
          {"id":"port_rtsps","status":"warn","params":{"port":322,"protocol":"rtsps"}},
          {"id":"subnet","status":"skip"}]}
    """.trimIndent()

    @Test
    fun `it asks the diagnostic route for that printer and maps the reply`() = runBlocking {
        server.enqueue(MockResponse().setBody(reply))
        val report = (repo.run(1) as ApiResult.Success).data
        assertEquals("/api/v1/printers/1/diagnostic", server.takeRequest().path)
        assertEquals(DiagnosticOverall.PROBLEMS, report.overall)
        assertEquals(
            listOf(DiagnosticStatus.PASS, DiagnosticStatus.FAIL, DiagnosticStatus.WARN, DiagnosticStatus.SKIP),
            report.checks.map { it.status },
        )
        assertEquals(1, report.problems)
        assertEquals(1, report.warnings)
    }

    @Test
    fun `the printer address in the reply is not kept`() = runBlocking {
        server.enqueue(MockResponse().setBody(reply))
        val report = (repo.run(1) as ApiResult.Success).data
        assertFalse(report.toString().contains("192.168"))
    }

    @Test
    fun `a missing printer and a server error come back as errors`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"Printer not found"}"""))
        val notFound = repo.run(9) as ApiResult.Error
        assertEquals(ApiErrorType.NOT_FOUND, notFound.errorType)
        assertEquals("Printer not found", notFound.serverDetail)
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(repo.run(1) is ApiResult.Error)
    }

    @Test
    fun `unknown statuses and ids, blank ids and too many checks are handled`() {
        val dto = PrinterDiagnosticDto(
            overall = "something new",
            checks = List(30) { DiagnosticCheckDto(id = "check_$it", status = if (it == 0) "weird" else "pass") } +
                DiagnosticCheckDto(id = "  ", status = "pass"),
        )
        val report = dto.toReport()
        assertEquals(DiagnosticOverall.UNKNOWN, report.overall)
        assertEquals(20, report.checks.size)
        assertEquals(DiagnosticStatus.UNKNOWN, report.checks.first().status)
    }

    @Test
    fun `an empty or missing check list gives an empty report`() {
        assertEquals(0, PrinterDiagnosticDto().toReport().checks.size)
        assertEquals(DiagnosticOverall.OK, PrinterDiagnosticDto(overall = "ok", checks = emptyList()).toReport().overall)
    }
}
