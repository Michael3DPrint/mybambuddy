package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.QueueApi
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.model.QueueStatus
import io.github.michael3dprint.mybambuddy.domain.model.TriState
import io.github.michael3dprint.mybambuddy.domain.policy.JobChange
import io.github.michael3dprint.mybambuddy.domain.policy.QueueOrdering
import io.github.michael3dprint.mybambuddy.testutil.TestSettings
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QueueRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: QueueRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val url = server.url("/").toString()
        repo =
            QueueRepositoryImpl(
                ApiClient.createService(ApiClient.createOkHttpClient(TestSettings(url)), QueueApi::class.java, baseUrl = url),
                io.github.michael3dprint.mybambuddy.testutil.AllowAllToolGate,
            )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun job(id: Int, position: Int, printer: Int? = 1, status: QueueStatus = QueueStatus.PENDING) =
        QueueItem(id, printer, "P", "job$id", status, position, null, false)

    @Test
    fun `queue is read with the trailing slash and mapped safely`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":1,"printer_id":1,"position":1,"status":"pending","archive_name":"benchy.3mf","waiting_reason":"Waiting for printer",
                "library_file_id":4,"print_time_seconds":4496,"filament_used_grams":31.2,"filament_type":"PLA",
                "plate_id":2,"ams_mapping":[5,-1],"bed_levelling":"off","flow_cali":true,"timelapse":true,"use_ams":false},
                {"id":2,"printer_id":null,"position":2,"status":"weird","archive_name":null},{"printer_id":3}]""",
            ),
        )
        val items = (repo.getQueue() as ApiResult.Success).data
        assertEquals("/api/v1/queue/", server.takeRequest().path)
        assertEquals(2, items.size) // the row without an id is dropped
        assertEquals("benchy.3mf", items[0].name)
        assertEquals(4, items[0].libraryFileId)
        assertEquals(4496, items[0].seconds)
        assertEquals("PLA", items[0].filamentType)
        assertEquals(2, items[0].plateId)
        assertEquals(listOf(5, -1), items[0].amsMapping)
        assertEquals(TriState.OFF, items[0].options.bedLevelling)
        assertEquals(TriState.ON, items[0].options.flowCalibration)
        assertTrue(items[0].options.timelapse)
        assertFalse(items[0].options.useAms)
        assertTrue(items[0].options.vibrationCalibration)
        assertEquals(QueueStatus.PENDING, items[0].status)
        assertEquals("Unassigned", items[1].printerName)
        assertEquals(QueueStatus.UNKNOWN, items[1].status)
        assertEquals("Print job #2", items[1].name)
    }

    @Test
    fun `actions use the right method and path`() = runBlocking {
        repeat(5) { server.enqueue(MockResponse().setBody("{}")) }
        repo.start(4, printerId = 1)
        repo.cancel(4)
        repo.remove(4)
        repo.resumeQueue(1)
        repo.reorder(listOf(4 to 1, 5 to 2))
        assertEquals("POST /api/v1/queue/4/start?skip_filament_check=false", server.takeRequest().let { "${it.method} ${it.path}" })
        assertEquals("POST /api/v1/queue/4/cancel", server.takeRequest().let { "${it.method} ${it.path}" })
        assertEquals("DELETE /api/v1/queue/4", server.takeRequest().let { "${it.method} ${it.path}" })
        assertEquals("POST /api/v1/queue/printer/1/resume", server.takeRequest().let { "${it.method} ${it.path}" })
        val reorder = server.takeRequest()
        assertEquals("/api/v1/queue/reorder", reorder.path)
        assertEquals("""{"items":[{"id":4,"position":1},{"id":5,"position":2}]}""", reorder.body.readUtf8())
    }

    @Test
    fun `reorder with duplicate positions never reaches the server`() = runBlocking {
        val r = repo.reorder(listOf(1 to 1, 2 to 1)) as ApiResult.Error
        assertEquals(ApiErrorType.UNSUPPORTED, r.errorType)
        assertTrue(repo.reorder(emptyList()) is ApiResult.Error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `start without a printer is refused with no request`() = runBlocking {
        val r = repo.start(4) as ApiResult.Error
        assertEquals(ApiErrorType.UNSUPPORTED, r.errorType)
        assertEquals("Choose a printer for this job in Bambuddy first.", r.message)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `server refusal text is kept`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"detail":"Printer is busy"}"""))
        assertEquals("Printer is busy", (repo.start(1, printerId = 1) as ApiResult.Error).serverDetail)
    }

    @Test
    fun `moving a job swaps neighbours and keeps positions unique`() {
        val items = listOf(job(1, 1), job(2, 2), job(3, 3), job(9, 1, printer = 2))
        val down = QueueOrdering.move(items, 1, up = false)!!
        assertEquals(listOf(2 to 1, 1 to 2, 3 to 3), down)
        val up = QueueOrdering.move(items, 3, up = true)!!
        assertEquals(listOf(1 to 1, 3 to 2, 2 to 3), up)
        assertEquals(up.map { it.second }.toSet().size, up.size)
    }

    @Test
    fun `ends of the queue and non waiting jobs cannot be moved`() {
        val items = listOf(job(1, 1), job(2, 2), job(3, 3, status = QueueStatus.PRINTING))
        assertNull(QueueOrdering.move(items, 1, up = true))
        assertNull(QueueOrdering.move(items, 2, up = false))
        assertNull(QueueOrdering.move(items, 3, up = true))
        assertNull(QueueOrdering.move(items, 99, up = true))
    }

    @Test
    fun `start is refused without a request when the printer cannot take it`() = runBlocking {
        val refusing = object : io.github.michael3dprint.mybambuddy.domain.repo.PrinterToolGate {
            override suspend fun refusal(
                printerId: String,
                tool: io.github.michael3dprint.mybambuddy.domain.model.PrinterTool,
                printerName: String,
            ) = "Developer LAN mode is off"
        }
        val url = server.url("/").toString()
        val gated = QueueRepositoryImpl(
            ApiClient.createService(ApiClient.createOkHttpClient(TestSettings(url)), QueueApi::class.java, baseUrl = url),
            refusing,
        )
        val r = gated.start(4, printerId = 1) as ApiResult.Error
        assertEquals("Developer LAN mode is off", r.message)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a job's picture comes from its library file or its archive, else there is none`() = runBlocking {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(1, 2, 3))))
        val lib = (repo.picture(job(1, 1).copy(libraryFileId = 4)) as ApiResult.Success).data
        assertEquals("/api/v1/library/files/4/thumbnail", server.takeRequest().path)
        assertEquals(3, lib.size)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(9))))
        repo.picture(job(2, 1).copy(archiveId = 7))
        assertEquals("/api/v1/archives/7/thumbnail", server.takeRequest().path)
        assertTrue(repo.picture(job(3, 1)) is ApiResult.Error)
        assertEquals(0, server.requestCount - 2)
    }

    @Test
    fun `an update sends only the changed fields once, and an empty change sends nothing`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        repo.update(4, JobChange(printerId = 2, bedLevelling = TriState.OFF, timelapse = true))
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("PATCH /api/v1/queue/4", "${request.method} ${request.path}")
        assertEquals("""{"printer_id":2,"bed_levelling":"off","timelapse":true}""", body)
        val none = repo.update(4, JobChange()) as ApiResult.Error
        assertEquals(ApiErrorType.UNSUPPORTED, none.errorType)
        assertEquals(1, server.requestCount)
    }
}
