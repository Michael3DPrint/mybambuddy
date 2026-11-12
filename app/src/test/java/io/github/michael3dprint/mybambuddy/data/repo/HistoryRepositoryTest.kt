package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.dto.ArchiveItemDto
import io.github.michael3dprint.mybambuddy.domain.mapper.toHistoryItem
import io.github.michael3dprint.mybambuddy.domain.model.HistoryStatus
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HistoryRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: HistoryRepositoryImpl

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString()
        val okHttpClient = OkHttpClient()
        val api = ApiClient.createService(
            okHttpClient,
            io.github.michael3dprint.mybambuddy.data.api.HistoryApi::class.java,
            baseUrl = baseUrl,
        )
        repository = HistoryRepositoryImpl(api)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `archive item dto correctly maps to history item`() {
        val dto = ArchiveItemDto(
            id = "arch_123",
            name = "TestPrint.gcode",
            printerName = "X1-Carbon",
            status = "completed",
            startTime = "2025-01-15T14:30:00Z",
            durationSeconds = 9600L,
            thumbnailUrl = "http://localhost/thumb.png",
        )

        val item = dto.toHistoryItem()

        assertEquals("arch_123", item.id)
        assertEquals("TestPrint.gcode", item.name)
        assertEquals("X1-Carbon", item.printerName)
        assertEquals(HistoryStatus.SUCCESS, item.status)
        assertEquals("2h 40m", item.formattedDuration)
        assertEquals("http://localhost/thumb.png", item.thumbnailUrl)
    }

    @Test
    fun `archive item status mapping handles cancelled and failed`() {
        val cancelledDto = ArchiveItemDto(status = "cancelled")
        val failedDto = ArchiveItemDto(status = "failed")
        val unknownDto = ArchiveItemDto(status = "random_state")

        assertEquals(HistoryStatus.CANCELLED, cancelledDto.toHistoryItem().status)
        assertEquals(HistoryStatus.FAILED, failedDto.toHistoryItem().status)
        assertEquals(HistoryStatus.UNKNOWN, unknownDto.toHistoryItem().status)
    }

    @Test
    fun `getHistory parses archives object response from server`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "archives": [
                        {
                          "id": "item1",
                          "name": "Benchy.gcode",
                          "printer_name": "Bambu P1S",
                          "status": "completed",
                          "duration_seconds": 3600
                        }
                      ],
                      "total": 1
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.getHistory(limit = 20, offset = 0)

        assertTrue(result is ApiResult.Success)
        val items = (result as ApiResult.Success).data
        assertEquals(1, items.size)
        assertEquals("item1", items.first().id)
        assertEquals("Benchy.gcode", items.first().name)
        assertEquals("Bambu P1S", items.first().printerName)
        assertEquals(HistoryStatus.SUCCESS, items.first().status)
        assertEquals("1h 0m", items.first().formattedDuration)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/archives/?limit=20&offset=0", recordedRequest.path)
    }

    @Test
    fun `getHistory parses raw list array response from server`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {
                        "id": "item2",
                        "filename": "Dragon.gcode",
                        "printer": "A1 Mini",
                        "status": "failed",
                        "duration": 1800
                      }
                    ]
                    """.trimIndent(),
                ),
        )

        val result = repository.getHistory(limit = 10, offset = 5)

        assertTrue(result is ApiResult.Success)
        val items = (result as ApiResult.Success).data
        assertEquals(1, items.size)
        assertEquals("item2", items.first().id)
        assertEquals("Dragon.gcode", items.first().name)
        assertEquals("A1 Mini", items.first().printerName)
        assertEquals(HistoryStatus.FAILED, items.first().status)
        assertEquals("30m 0s", items.first().formattedDuration)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/archives/?limit=10&offset=5", recordedRequest.path)
    }

    @Test
    fun `getHistory handles HTTP error`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": "Unauthorized"}"""),
        )

        val result = repository.getHistory(limit = 20, offset = 0)

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(401, error.code)
        assertEquals(ApiErrorType.UNAUTHORIZED, error.errorType)
    }
}
