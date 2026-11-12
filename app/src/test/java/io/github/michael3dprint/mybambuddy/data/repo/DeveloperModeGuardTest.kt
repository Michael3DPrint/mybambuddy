package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.PrinterApi
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
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

class DeveloperModeGuardTest {
    private lateinit var server: MockWebServer
    private lateinit var guard: DeveloperModeGuard

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val url = server.url("/").toString()
        guard = DeveloperModeGuard(
            ReadinessCheckImpl(
                ApiClient.createService(ApiClient.createOkHttpClient(TestSettings(url)), PrinterApi::class.java, baseUrl = url),
            ),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun status(body: String) = server.enqueue(MockResponse().setBody(body))

    @Test
    fun `developer mode off refuses a tool that needs it, with the fix`() = runBlocking {
        status("""{"connected":true,"state":"IDLE","developer_mode":false}""")
        val msg = guard.refusal("1", PrinterTool.CHAMBER_LIGHT, "X2D")!!
        assertTrue(msg, msg.contains("X2D") && msg.contains("Developer LAN mode") && msg.contains("network settings"))
    }

    @Test
    fun `developer mode on or unknown allows`() = runBlocking {
        status("""{"connected":true,"state":"IDLE","developer_mode":true}""")
        assertNull(guard.refusal("1", PrinterTool.PAUSE_RESUME))
        status("""{"connected":true,"state":"IDLE"}""")
        assertNull(guard.refusal("1", PrinterTool.PAUSE_RESUME))
    }

    @Test
    fun `tools that do not need developer mode cost no request`() = runBlocking {
        assertNull(guard.refusal("1", PrinterTool.CLEAR_PLATE))
        assertNull(guard.refusal("1", PrinterTool.REFRESH_STATUS))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an unreadable status does not block the command`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertNull(guard.refusal("1", PrinterTool.PAUSE_RESUME))
        status("not json")
        assertNull(guard.refusal("1", PrinterTool.PAUSE_RESUME))
    }

    @Test
    fun `an offline printer is refused`() = runBlocking {
        status("""{"connected":false,"developer_mode":true}""")
        assertTrue(guard.refusal("1", PrinterTool.CANCEL_PRINT)!!.contains("offline"))
    }

    @Test
    fun `drying is refused when the AMS cannot dry`() = runBlocking {
        status(
            """{"connected":true,"state":"IDLE","developer_mode":true,"ams_exists":true,"supports_drying":false,"drying_screen_only":true}""",
        )
        assertTrue(guard.refusal("1", PrinterTool.DRYING)!!.contains("printer's screen"))
    }

    @Test
    fun `drying is refused while printing when the printer cannot dry during a print`() = runBlocking {
        status(
            """{"connected":true,"state":"RUNNING","developer_mode":true,"ams_exists":true,"supports_drying":true,"supports_drying_while_printing":false}""",
        )
        assertTrue(guard.refusal("1", PrinterTool.DRYING)!!.contains("can't dry while a print is running"))
        status(
            """{"connected":true,"state":"RUNNING","developer_mode":true,"ams_exists":true,"supports_drying":true,"supports_drying_while_printing":true}""",
        )
        assertNull(guard.refusal("1", PrinterTool.DRYING))
    }
}
