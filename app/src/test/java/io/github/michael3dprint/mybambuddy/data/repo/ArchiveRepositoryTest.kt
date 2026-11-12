package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.HistoryApi
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

class ArchiveRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: ArchiveRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        repo = ArchiveRepositoryImpl(ApiClient.createService(OkHttpClient(), HistoryApi::class.java, baseUrl = server.url("/").toString()))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `a page is read with its paging and printer filter and keeps the printer id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":"1","print_name":"Ornament","printer_id":"2","status":"cancelled"}]"""))
        val items = (repo.page(20, 40, 2) as ApiResult.Success).data
        assertEquals("/archives/?limit=20&offset=40&printer_id=2", server.takeRequest().path)
        assertEquals("Ornament", items.single().name)
        assertEquals(2, items.single().printerId)
        assertEquals(HistoryStatus.CANCELLED, items.single().status)
    }

    @Test
    fun `without a printer the filter is left out and a picture comes from the archive's thumbnail`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        repo.page(20, 0, null)
        assertEquals("/archives/?limit=20&offset=0", server.takeRequest().path)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(1, 2))))
        val bytes = (repo.picture(7) as ApiResult.Success).data
        assertEquals("/archives/7/thumbnail", server.takeRequest().path)
        assertEquals(2, bytes.size)
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(repo.picture(8) is ApiResult.Error)
    }

    @Test
    fun `numeric ids as the real server sends them are read`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""[{"id":1,"print_name":"Ornament","printer_id":1,"status":"cancelled","filename":"a.gcode.3mf"}]"""),
        )
        val item = (repo.page(20, 0, null) as ApiResult.Success).data.single()
        assertEquals("1", item.id)
        assertEquals(1, item.printerId)
    }
}
