package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.MaintenanceApi
import io.github.michael3dprint.mybambuddy.data.api.dto.MaintenanceItemDto
import io.github.michael3dprint.mybambuddy.domain.model.DueState
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

class MaintenanceRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: MaintenanceRepositoryImpl

    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val url = server.url("/").toString()
        val client = ApiClient.createOkHttpClient(TestSettings(url))
        repo = MaintenanceRepositoryImpl(ApiClient.createService(client, MaintenanceApi::class.java, baseUrl = url))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `real overview maps to printers and items`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("maintenance_overview.json")))
        val printers = (repo.getOverview() as ApiResult.Success).data
        assertEquals("/api/v1/maintenance/overview", server.takeRequest().path)
        val printer = printers.single()
        assertEquals(6, printer.items.size)
        val rods = printer.items.first { it.name == "Lubricate Steel Rods" }
        assertEquals(DueState.OK, rods.dueState)
        assertEquals("50 h left", rods.remainingLabel)
        assertEquals(0f, rods.progress)
    }

    @Test
    fun `due soon overdue and clamping`() {
        val due = MaintenanceItemDto(id = 1, name = "A", isDue = true, intervalHours = 100.0, hoursSince = 130.0, hoursUntilDue = -30.0)
            .toDomain(1)!!
        assertEquals(DueState.DUE, due.dueState)
        assertEquals("30 h overdue", due.remainingLabel)
        assertEquals(1f, due.progress) // never above 1
        val soon = MaintenanceItemDto(
            id = 2,
            name = "B",
            isWarning = true,
            intervalHours = 100.0,
            hoursSince = 95.0,
            hoursUntilDue = 5.0,
        ).toDomain(1)!!
        assertEquals(DueState.SOON, soon.dueState)
        val days = MaintenanceItemDto(
            id = 3,
            intervalType = "days",
            intervalHours = 30.0,
            daysSince = 10.0,
            daysUntilDue = 20.0,
        ).toDomain(1)!!
        assertEquals("20 d left", days.remainingLabel)
        val negative = MaintenanceItemDto(id = 4, intervalHours = 100.0, hoursSince = -5.0).toDomain(1)!!
        assertEquals(0f, negative.progress)
        val zero = MaintenanceItemDto(id = 5, intervalHours = 0.0, hoursSince = 5.0).toDomain(1)!!
        assertEquals(0f, zero.progress) // no division by zero
        assertNull(MaintenanceItemDto(name = "no id").toDomain(1))
    }

    @Test
    fun `mark done posts an object body to the item`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        assertTrue(repo.markDone(7) is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/maintenance/items/7/perform", request.path)
        assertEquals("{}", request.body.readUtf8())
    }

    @Test
    fun `a refusal keeps the server message`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"Maintenance item not found"}"""))
        val error = repo.markDone(7) as ApiResult.Error
        assertEquals("Maintenance item not found", error.serverDetail)
    }
}
