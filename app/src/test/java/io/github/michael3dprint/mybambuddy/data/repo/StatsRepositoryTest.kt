package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.StatsApi
import io.github.michael3dprint.mybambuddy.data.api.dto.ArchiveStatsDto
import io.github.michael3dprint.mybambuddy.domain.model.StatBar
import io.github.michael3dprint.mybambuddy.domain.policy.StatsPeriod
import io.github.michael3dprint.mybambuddy.testutil.TestSettings
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StatsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: StatsRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val url = server.url("/").toString()
        val api = ApiClient.createService(ApiClient.createOkHttpClient(TestSettings(url)), StatsApi::class.java, baseUrl = url)
        repo = StatsRepositoryImpl(api, { LocalDate.of(2026, 11, 11) }, { ZoneId.of("America/New_York") })
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `this month asks for the month's dates in the device time zone`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        repo.getStats(StatsPeriod.THIS_MONTH)
        val path = server.takeRequest().path!!
        assertTrue(path, path.startsWith("/api/v1/archives/stats?"))
        assertTrue(path, path.contains("date_from=2026-11-01") && path.contains("date_to=2026-11-11"))
        assertTrue(path, path.contains("tz=America%2FNew_York") || path.contains("tz=America/New_York"))
    }

    @Test
    fun `all time sends no dates`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        repo.getStats(StatsPeriod.ALL_TIME)
        val path = server.takeRequest().path!!
        assertTrue(path, !path.contains("date_from") && !path.contains("date_to"))
    }

    @Test
    fun `a real shaped reply maps with printer names, top bars and the success rate`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"total_prints":24,"successful_prints":22,"failed_prints":2,"cancelled_prints":1,"total_print_time_hours":31.25,
                "total_filament_grams":1234.5,"total_cost":9.9,"prints_by_filament_type":{"PLA":20,"PETG":4,"ABS":0},
                "prints_by_printer":{"1":18,"2":6,"9":1},"printer_names":{"1":"X2D","2":"P1S"},"something_new":true}""",
            ),
        )
        val stats = (repo.getStats(StatsPeriod.THIS_MONTH) as ApiResult.Success).data
        assertEquals(24, stats.totalPrints)
        assertEquals(91, stats.successPercent)
        assertEquals(listOf(StatBar("X2D", 18), StatBar("P1S", 6), StatBar("Printer 9", 1)), stats.byPrinter)
        assertEquals(listOf(StatBar("PLA", 20), StatBar("PETG", 4)), stats.byMaterial)
        assertEquals(31.25, stats.totalHours, 0.0)
    }

    @Test
    fun `missing fields are zero and zero prints give no percentage`() {
        val stats = ArchiveStatsDto().toDomain()
        assertEquals(0, stats.totalPrints)
        assertNull(stats.successPercent)
        assertTrue(stats.byPrinter.isEmpty() && stats.byMaterial.isEmpty())
    }

    @Test
    fun `only the four largest bars are kept`() {
        val dto = ArchiveStatsDto(byMaterial = mapOf("A" to 1.0, "B" to 5.0, "C" to 3.0, "D" to 2.0, "E" to 4.0))
        assertEquals(listOf("B", "E", "C", "D"), dto.toDomain().byMaterial.map { it.label })
    }

    @Test
    fun `an error is passed on`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(repo.getStats(StatsPeriod.ALL_TIME) is ApiResult.Error)
    }
}
