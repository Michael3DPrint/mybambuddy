package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.AmsApi
import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.InventoryApi
import io.github.michael3dprint.mybambuddy.data.api.PrinterApi
import io.github.michael3dprint.mybambuddy.domain.model.DryPhase
import io.github.michael3dprint.mybambuddy.domain.model.DryingPresets
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterToolGate
import io.github.michael3dprint.mybambuddy.testutil.AllowAllToolGate
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

class AmsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: AmsRepositoryImpl

    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val url = server.url("/").toString()
        val client = ApiClient.createOkHttpClient(TestSettings(url))
        val inventoryApi = ApiClient.createService(client, InventoryApi::class.java, baseUrl = url)
        repo = AmsRepositoryImpl(
            ApiClient.createService(client, PrinterApi::class.java, baseUrl = url),
            ApiClient.createService(client, AmsApi::class.java, baseUrl = url),
            InventoryModeProvider(inventoryApi),
            AllowAllToolGate,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    /** A status payload shaped like the live one, with the fields the safety checks read. */
    private fun status(
        state: String? = "IDLE",
        developerMode: Boolean? = true,
        connected: Boolean = true,
        rfidTrays: Set<Int> = emptySet(),
    ) = MockResponse().setBody(
        buildString {
            append("""{"connected":$connected,""")
            if (state != null) append(""""state":"$state",""")
            if (developerMode != null) append(""""developer_mode":$developerMode,""")
            append(""""ams_exists":true,"ams":[{"id":0,"tray":[""")
            append(
                (0..3).joinToString(",") { id ->
                    val tag = if (id in rfidTrays) ""","tag_uid":"A1B2C3D4"""" else ""
                    """{"id":$id,"tray_type":"PLA","tray_color":"FFFFFFFF"$tag}"""
                },
            )
            append("]}]}")
        },
    )

    private fun statusOff() = MockResponse().setBody("""{"enabled":false,"connected":false,"url":null}""")

    @Test
    fun `reads ams units with humidity drying state trays and loaded spools`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("printers.json")))
        server.enqueue(statusOff())
        server.enqueue(MockResponse().setBody("""[{"spool_id":9,"printer_id":1,"ams_id":0,"tray_id":2}]"""))
        server.enqueue(MockResponse().setBody(fixture("status_ams.json")))

        val units = (repo.getAms() as ApiResult.Success).data
        val ams = units.single()
        assertEquals("AMS 1", ams.name)
        assertEquals(23, ams.humidityLevel)
        assertTrue(ams.supportsDrying)
        assertEquals(DryPhase.OFF, ams.dryPhase)
        assertEquals(4, ams.trays.size)
        assertEquals(9, ams.trays[2].loadedSpoolId)
        assertNull(ams.trays[3].loadedSpoolId)
        assertEquals("PLA Basic", ams.trays[2].label)
        assertEquals(75, ams.trays[2].remainPercent)
        assertNull(ams.trays[0].remainPercent) // remain = -1 means unknown
    }

    @Test
    fun `loadSpool uses the inventory assignment endpoint when spoolman is off`() = runBlocking {
        server.enqueue(status())
        server.enqueue(statusOff())
        server.enqueue(MockResponse().setBody("""{"configured":true,"pending_config":false}"""))
        val r = repo.loadSpool(5, 1, 0, 2) as ApiResult.Success
        assertEquals(false, r.data.pendingConfig)
        server.takeRequest()
        server.takeRequest()
        val post = server.takeRequest()
        assertEquals("/api/v1/inventory/assignments", post.path)
        val body = post.body.readUtf8()
        assertTrue(body, body.contains("\"spool_id\":5") && body.contains("\"tray_id\":2") && body.contains("\"move\":true"))
    }

    @Test
    fun `a pending configuration from the server is passed on`() = runBlocking {
        server.enqueue(status())
        server.enqueue(statusOff())
        server.enqueue(MockResponse().setBody("""{"configured":false,"pending_config":true}"""))
        assertEquals(true, (repo.loadSpool(5, 1, 0, 2) as ApiResult.Success).data.pendingConfig)
    }

    @Test
    fun `an empty or odd response body still counts as saved`() = runBlocking {
        server.enqueue(status())
        server.enqueue(statusOff())
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(null, (repo.loadSpool(5, 1, 0, 2) as ApiResult.Success).data.pendingConfig)
    }

    @Test
    fun `a reply that breaks off mid-body does not turn a saved assignment into a failure`() = runBlocking {
        server.enqueue(status())
        server.enqueue(statusOff())
        server.enqueue(
            MockResponse().setBody("""{"configured":true,"pending_config":false,"padding":"${"x".repeat(2000)}"}""")
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        val r = repo.loadSpool(5, 1, 0, 2)
        assertTrue(r.toString(), r is ApiResult.Success)
        assertEquals(null, (r as ApiResult.Success).data.pendingConfig)
    }

    private suspend fun assertBlocked(response: MockResponse, expected: String) {
        server.enqueue(response)
        val r = repo.loadSpool(5, 1, 0, 2) as ApiResult.Error
        assertTrue(r.message, r.message.contains(expected))
        assertEquals("only the status read was sent", 1, server.requestCount)
    }

    @Test
    fun `printing paused preparing and slicing printers are refused`() = runBlocking {
        for (state in listOf("RUNNING", "PAUSE", "PREPARE", "SLICING")) {
            val before = server.requestCount
            server.enqueue(status(state = state))
            val r = repo.loadSpool(5, 1, 0, 2) as ApiResult.Error
            assertTrue("$state: ${r.message}", r.message.contains("printing"))
            assertEquals(before + 1, server.requestCount)
        }
    }

    @Test
    fun `a refusal names the printer it was given, and falls back to a generic name`() = runBlocking {
        server.enqueue(status(state = "RUNNING"))
        val named = repo.loadSpool(5, 1, 0, 2, "X2D") as ApiResult.Error
        assertTrue(named.message, named.message.startsWith("X2D"))
        server.enqueue(status(state = "RUNNING"))
        val generic = repo.loadSpool(5, 1, 0, 2, "  ") as ApiResult.Error
        assertTrue(generic.message, generic.message.startsWith("The printer"))
    }

    @Test
    fun `getTray makes one status request for one printer and returns the slot`() = runBlocking {
        server.enqueue(status(state = "IDLE"))
        val r = repo.getTray(1, 0, 2) as ApiResult.Success
        assertEquals(1, server.requestCount)
        assertEquals(2, r.data?.trayId)
    }

    @Test
    fun `getTray returns null for a slot that is not there and the error when the read fails`() = runBlocking {
        server.enqueue(status(state = "IDLE"))
        assertEquals(null, (repo.getTray(1, 0, 9) as ApiResult.Success).data)
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(repo.getTray(1, 0, 2) is ApiResult.Error)
    }

    @Test
    fun `idle finished and failed printers are allowed`() = runBlocking {
        // The inventory mode is read once and then remembered, so only the first load asks for it.
        for ((i, state) in listOf("IDLE", "FINISH", "FAILED").withIndex()) {
            server.enqueue(status(state = state))
            if (i == 0) server.enqueue(statusOff())
            server.enqueue(MockResponse().setBody("{}"))
            assertTrue(state, repo.loadSpool(5, 1, 0, 2) is ApiResult.Success)
        }
    }

    @Test
    fun `a missing state is refused as not safe`() = runBlocking { assertBlocked(status(state = null), "Can't tell") }

    @Test
    fun `developer mode off is refused with the fix`() = runBlocking { assertBlocked(status(developerMode = false), "Developer LAN mode") }

    @Test
    fun `an offline printer is refused`() = runBlocking { assertBlocked(status(connected = false), "offline") }

    @Test
    fun `an RFID spool in the slot is refused but other slots are fine`() = runBlocking {
        assertBlocked(status(rfidTrays = setOf(2)), "RFID")
        server.enqueue(status(rfidTrays = setOf(0, 1)))
        server.enqueue(statusOff())
        server.enqueue(MockResponse().setBody("{}"))
        assertTrue(repo.loadSpool(5, 1, 0, 2) is ApiResult.Success)
    }

    @Test
    fun `an unknown developer mode is allowed`() = runBlocking {
        server.enqueue(status(developerMode = null))
        server.enqueue(statusOff())
        server.enqueue(MockResponse().setBody("{}"))
        assertTrue(repo.loadSpool(5, 1, 0, 2) is ApiResult.Success)
    }

    @Test
    fun `a status read that fails blocks the change and sends nothing else`() = runBlocking {
        assertBlocked(MockResponse().setResponseCode(500), "Can't check")
    }

    @Test
    fun `a slot that is not on the printer is refused`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"connected":true,"state":"IDLE","developer_mode":true,"ams":[{"id":0,"tray":[{"id":0}]}]}"""),
        )
        val r = repo.loadSpool(5, 1, 0, 2) as ApiResult.Error
        assertTrue(r.message, r.message.contains("not found"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `drying is refused by the tool gate without any request`() = runBlocking {
        val url = server.url("/").toString()
        val client = ApiClient.createOkHttpClient(TestSettings(url))
        val refusing = object : PrinterToolGate {
            override suspend fun refusal(
                printerId: String,
                tool: io.github.michael3dprint.mybambuddy.domain.model.PrinterTool,
                printerName: String,
            ) = "Developer LAN mode off"
        }
        val gated = AmsRepositoryImpl(
            ApiClient.createService(client, PrinterApi::class.java, baseUrl = url),
            ApiClient.createService(client, AmsApi::class.java, baseUrl = url),
            InventoryModeProvider(ApiClient.createService(client, InventoryApi::class.java, baseUrl = url)),
            refusing,
        )
        assertEquals("Developer LAN mode off", (gated.startDrying(1, 0, 55, 8, "PLA") as ApiResult.Error).message)
        assertEquals("Developer LAN mode off", (gated.stopDrying(1, 0) as ApiResult.Error).message)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `loadSpool with invalid ids returns an error and sends no request`() = runBlocking {
        assertTrue(repo.loadSpool(0, 1, 0, 2) is ApiResult.Error)
        assertTrue(repo.loadSpool(5, 0, 0, 2) is ApiResult.Error)
        assertTrue(repo.loadSpool(5, 1, 0, 4) is ApiResult.Error)
        assertTrue(repo.loadSpool(5, 1, 0, -1) is ApiResult.Error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `loadSpool and unloadSpool use the spoolman endpoints when spoolman is on`() = runBlocking {
        server.enqueue(status())
        server.enqueue(MockResponse().setBody("""{"enabled":true,"connected":true,"url":"http://x:7912/"}"""))
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        repo.loadSpool(5, 1, 0, 2)
        repo.unloadSpool(5, 1, 0, 2)
        server.takeRequest()
        server.takeRequest()
        val assign = server.takeRequest()
        assertEquals("/api/v1/spoolman/inventory/slot-assignments", assign.path)
        assertTrue(assign.body.readUtf8().contains("\"spoolman_spool_id\":5"))
        val unassign = server.takeRequest()
        assertEquals("DELETE", unassign.method)
        assertEquals("/api/v1/spoolman/inventory/slot-assignments/5", unassign.path)
    }

    @Test
    fun `drying start and stop hit the real endpoints`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"drying_started"}"""))
        server.enqueue(MockResponse().setBody("""{"status":"drying_stopped"}"""))
        assertTrue(repo.startDrying(1, 0, 55, 8, "PLA") is ApiResult.Success)
        assertTrue(repo.stopDrying(1, 0) is ApiResult.Success)
        val start = server.takeRequest()
        assertEquals("POST", start.method)
        assertEquals("/api/v1/printers/1/drying/start?ams_id=0&temp=55&duration=8&filament=PLA", start.path)
        assertEquals("/api/v1/printers/1/drying/stop?ams_id=0", server.takeRequest().path)
    }

    @Test
    fun `drying limits are checked before any request is sent`() = runBlocking {
        val tooHot = repo.startDrying(1, 0, 90, 8, "") as ApiResult.Error
        val tooLong = repo.startDrying(1, 0, 55, 30, "") as ApiResult.Error
        assertEquals(ApiErrorType.UNSUPPORTED, tooHot.errorType)
        assertEquals(ApiErrorType.UNSUPPORTED, tooLong.errorType)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `server refusal to start drying keeps the server message`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"AMS is busy"}"""))
        val r = repo.startDrying(1, 0, 55, 8, "PLA") as ApiResult.Error
        assertEquals("AMS is busy", r.serverDetail)
    }

    @Test
    fun `link tag normalises the uid`() = runBlocking {
        val api = ApiClient.createService(
            ApiClient.createOkHttpClient(TestSettings(server.url("/").toString())),
            InventoryApi::class.java,
            baseUrl = server.url("/").toString(),
        )
        val usageApi = ApiClient.createService(
            ApiClient.createOkHttpClient(TestSettings(server.url("/").toString())),
            io.github.michael3dprint.mybambuddy.data.api.SpoolUsageApi::class.java,
            baseUrl = server.url("/").toString(),
        )
        val spools = SpoolRepositoryImpl(api, InventoryModeProvider(api), usageApi)
        server.enqueue(statusOff())
        server.enqueue(MockResponse().setBody("{}"))
        spools.linkTag(3, "04:a2:3b:1c:55:80:90")
        server.takeRequest()
        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method)
        assertEquals("/api/v1/inventory/spools/3/link-tag", patch.path)
        assertTrue(patch.body.readUtf8().contains("04A23B1C558090"))
    }

    @Test
    fun `drying presets follow the material`() {
        assertEquals(55, DryingPresets.forMaterial("PLA").tempC)
        assertEquals(65, DryingPresets.forMaterial("PETG").tempC)
        assertEquals(80, DryingPresets.forMaterial("ABS").tempC)
        assertEquals(85, DryingPresets.forMaterial("PA-CF").tempC)
    }
}
