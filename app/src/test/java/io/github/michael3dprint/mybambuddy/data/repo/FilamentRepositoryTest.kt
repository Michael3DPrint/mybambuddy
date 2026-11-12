package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.InventoryApi
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.testutil.TestSettings
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FilamentRepositoryTest {
    private lateinit var bambuddy: MockWebServer
    private lateinit var spoolman: MockWebServer
    private lateinit var spools: SpoolRepositoryImpl
    private lateinit var catalog: CatalogRepositoryImpl

    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Before
    fun setUp() {
        bambuddy = MockWebServer().also { it.start() }
        spoolman = MockWebServer().also { it.start() }
        val url = bambuddy.url("/").toString()
        val settings = TestSettings(url)
        val api = ApiClient.createService(ApiClient.createOkHttpClient(settings), InventoryApi::class.java, baseUrl = url)
        val mode = InventoryModeProvider(api)
        spools =
            SpoolRepositoryImpl(
                api,
                mode,
                ApiClient.createService(
                    ApiClient.createOkHttpClient(settings),
                    io.github.michael3dprint.mybambuddy.data.api.SpoolUsageApi::class.java,
                    baseUrl = url,
                ),
            )
        catalog = CatalogRepositoryImpl(mode, settings) { ApiClient.createSpoolmanDbApi(spoolman.url("/").toString()) }
    }

    @After
    fun tearDown() {
        bambuddy.shutdown()
        spoolman.shutdown()
    }

    private fun statusJson(enabled: Boolean, connected: Boolean = true) =
        """{"enabled":$enabled,"connected":$connected,"url":"${spoolman.url("/")}","native_tags":true}"""

    @Test
    fun `uses the spoolman passthrough when spoolman is enabled`() = runBlocking {
        bambuddy.enqueue(MockResponse().setBody(statusJson(true)))
        bambuddy.enqueue(MockResponse().setBody(fixture("spools.json")))
        val r = spools.getSpools() as ApiResult.Success
        assertEquals("/api/v1/spoolman/status", bambuddy.takeRequest().path)
        assertEquals("/api/v1/spoolman/inventory/spools", bambuddy.takeRequest().path)
        val spool = r.data.single()
        assertEquals(2, spool.id)
        assertEquals("Bambu Lab PLA Metal", spool.title)
        assertEquals(630, spool.remainingGrams)
        assertEquals(63, spool.remainingPercent)
    }

    @Test
    fun `falls back to the built in inventory when spoolman is off`() = runBlocking {
        bambuddy.enqueue(MockResponse().setBody(statusJson(false, connected = false)))
        bambuddy.enqueue(MockResponse().setBody("[]"))
        assertTrue(spools.getSpools() is ApiResult.Success)
        bambuddy.takeRequest()
        assertEquals("/api/v1/inventory/spools", bambuddy.takeRequest().path)
    }

    @Test
    fun `createSpool posts only the fields that were filled in`() = runBlocking {
        bambuddy.enqueue(MockResponse().setBody(statusJson(true)))
        bambuddy.enqueue(MockResponse().setBody("""{"id":7,"material":"PETG","brand":"Acme","label_weight":750,"weight_used":0.0}"""))
        val r = spools.createSpool(SpoolDraft(brand = "Acme", material = "PETG", colorHex = "#FF0000", labelWeightGrams = 750))
        assertTrue(r is ApiResult.Success)
        bambuddy.takeRequest()
        val post = bambuddy.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/api/v1/spoolman/inventory/spools", post.path)
        val body = post.body.readUtf8()
        assertTrue(
            body,
            body.contains("\"material\":\"PETG\"") && body.contains("\"brand\":\"Acme\"") && body.contains("\"rgba\":\"FF0000\""),
        )
        assertTrue(body, !body.contains("subtype") && !body.contains("core_weight"))
    }

    @Test
    fun `a repeated catalog search is answered from memory`() = runBlocking {
        bambuddy.enqueue(MockResponse().setBody(statusJson(true)))
        spoolman.enqueue(MockResponse().setBody(fixture("external_filament_search.json")))
        val first = catalog.search("Bambu PLA") as ApiResult.Success
        val again = catalog.search("  bambu pla ") as ApiResult.Success
        assertEquals(first.data, again.data)
        assertEquals(1, spoolman.requestCount)
    }

    @Test
    fun `a failed status probe is not remembered and the last known mode is kept`() = runBlocking {
        var now = 0L
        val api = ApiClient.createService(
            ApiClient.createOkHttpClient(TestSettings(bambuddy.url("/").toString())),
            InventoryApi::class.java,
            baseUrl = bambuddy.url("/").toString(),
        )
        val provider = InventoryModeProvider(api, clock = { now })
        bambuddy.enqueue(MockResponse().setBody(statusJson(true)))
        assertTrue(provider.current().useSpoolman)
        now += 61_000
        bambuddy.enqueue(MockResponse().setResponseCode(503))
        assertTrue("last known mode survives a failed probe", provider.current().useSpoolman)
        bambuddy.enqueue(MockResponse().setBody(statusJson(false, connected = false)))
        assertEquals("the failed probe was retried at once, not cached", false, provider.current().useSpoolman)
    }

    @Test
    fun `with no history a failed probe assumes the built in inventory but does not cache that guess`() = runBlocking {
        val api = ApiClient.createService(
            ApiClient.createOkHttpClient(TestSettings(bambuddy.url("/").toString())),
            InventoryApi::class.java,
            baseUrl = bambuddy.url("/").toString(),
        )
        val provider = InventoryModeProvider(api)
        bambuddy.enqueue(MockResponse().setResponseCode(503))
        assertEquals(false, provider.current().useSpoolman)
        bambuddy.enqueue(MockResponse().setBody(statusJson(true)))
        assertTrue(provider.current().useSpoolman)
    }

    @Test
    fun `catalog search reads the spoolman server whose address comes from bambuddy`() = runBlocking {
        bambuddy.enqueue(MockResponse().setBody(statusJson(true)))
        spoolman.enqueue(MockResponse().setBody(fixture("external_filament_search.json")))
        val r = catalog.search("bambu pla") as ApiResult.Success
        assertEquals("/api/v1/external/filament/search?query=bambu%20pla&limit=40&offset=0", spoolman.takeRequest().path)
        val item = r.data.single()
        assertEquals("Bambu Lab PLA Iron Gray Metallic", item.title)
        assertEquals("43403D", item.colorHex)
        assertEquals(250, item.spoolWeightGrams)
    }

    @Test
    fun `usage history is mapped sorted sanitised and bounded`() = runBlocking {
        bambuddy.enqueue(MockResponse().setBody(statusJson(true)))
        bambuddy.enqueue(
            MockResponse().setBody(
                """[{"id":1,"print_name":"old\u0000print","weight_used":12.6,"percent_used":1,"status":"completed","created_at":"2026-11-01T10:00:00"},
                {"id":2,"print_name":null,"weight_used":-5,"status":"failed","created_at":"2026-11-08T10:00:00"}]""",
            ),
        )
        val entries = (spools.getUsage(5) as ApiResult.Success).data
        assertEquals("/api/v1/spoolman/inventory/spools/5/usage", bambuddy.takeRequest().let { bambuddy.takeRequest().path })
        assertEquals(listOf("2026-11-08", "2026-11-01"), entries.map { it.date })
        assertEquals("Print", entries[0].printName)
        assertEquals(0, entries[0].grams) // negative weight never shown
        assertEquals("oldprint", entries[1].printName)
        assertEquals(12, entries[1].grams)
    }

    @Test
    fun `catalog search is unavailable when bambuddy has no spoolman`() = runBlocking {
        bambuddy.enqueue(MockResponse().setResponseCode(404))
        val r = catalog.search("pla") as ApiResult.Error
        assertEquals(ApiErrorType.UNSUPPORTED, r.errorType)
    }

    @Test
    fun `spoolman address is swapped to the bambuddy host when it points at localhost`() {
        assertEquals(
            "http://100.64.0.10:7912/",
            CatalogRepositoryImpl.reachableSpoolmanUrl("http://localhost:7912/", "http://100.64.0.10:8000"),
        )
        assertEquals(
            "http://100.64.0.10:7912/",
            CatalogRepositoryImpl.reachableSpoolmanUrl("http://spoolman:7912/", "100.64.0.10:8000"),
        )
        assertEquals(
            "http://100.64.0.10:7912/",
            CatalogRepositoryImpl.reachableSpoolmanUrl("http://100.64.0.10:7912/", "http://other.example:8000"),
        )
    }

    @Test
    fun `weigh patches the weight route with the reading in the body`() = runBlocking {
        bambuddy.enqueue(MockResponse().setBody(statusJson(true)))
        bambuddy.enqueue(MockResponse().setBody("""{"status":"ok","weight_used":188.0}"""))
        assertTrue(spools.updateWeight(5, 812.5) is ApiResult.Success)
        bambuddy.takeRequest()
        val patch = bambuddy.takeRequest()
        assertEquals("PATCH", patch.method)
        assertEquals("/api/v1/spoolman/inventory/spools/5/weight", patch.path)
        assertEquals("""{"weight_grams":812.5}""", patch.body.readUtf8())
    }

    @Test
    fun `weigh in the built in inventory is refused on the phone and sends nothing`() = runBlocking {
        bambuddy.enqueue(MockResponse().setBody(statusJson(false, connected = false)))
        val result = spools.updateWeight(5, 812.5) as ApiResult.Error
        assertEquals(ApiErrorType.UNSUPPORTED, result.errorType)
        assertEquals(1, bambuddy.requestCount) // only the mode probe
        assertEquals(false, spools.canWeigh())
    }
}
