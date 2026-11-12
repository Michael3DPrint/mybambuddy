package io.github.michael3dprint.mybambuddy.ui.queue

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.HistoryItem
import io.github.michael3dprint.mybambuddy.domain.model.HistoryStatus
import io.github.michael3dprint.mybambuddy.domain.policy.PreviousPrintFilter
import io.github.michael3dprint.mybambuddy.domain.repo.ArchiveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreviousPrintsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private fun print(id: Int, status: HistoryStatus) = HistoryItem(id.toString(), "p$id", "X2D", status, "12 Nov", "1 h", printerId = 1)

    private class FakeArchives(private val all: List<HistoryItem>) : ArchiveRepository {
        val pages = mutableListOf<Triple<Int, Int, Int?>>()
        var failFirst = false

        override suspend fun page(limit: Int, offset: Int, printerId: Int?): ApiResult<List<HistoryItem>> {
            pages += Triple(limit, offset, printerId)
            if (failFirst) {
                failFirst = false
                return ApiResult.Error(message = "down", errorType = ApiErrorType.NETWORK_ERROR)
            }
            return ApiResult.Success(all.drop(offset).take(limit))
        }

        override suspend fun picture(archiveId: Int): ApiResult<ByteArray> = if (archiveId % 2 ==
            0
        ) {
            ApiResult.Success(byteArrayOf(1))
        } else {
            ApiResult.Error(message = "none", errorType = ApiErrorType.NOT_FOUND)
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the first page loads, pictures are fetched and a missing one is skipped`() = runTest(dispatcher) {
        val archives = FakeArchives(List(3) { print(it + 1, HistoryStatus.CANCELLED) })
        val vm = PreviousPrintsViewModel(archives, printerId = 1)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(3, s.items.size)
        assertTrue(s.endReached)
        assertEquals(setOf(2), s.pictures.keys)
        assertEquals(listOf(Triple(20, 0, 1)), archives.pages)
    }

    @Test
    fun `more pages are loaded on demand until a short page ends the list`() = runTest(dispatcher) {
        val archives = FakeArchives(List(25) { print(it + 1, HistoryStatus.SUCCESS) })
        val vm = PreviousPrintsViewModel(archives, printerId = null)
        advanceUntilIdle()
        assertEquals(20, vm.state.value.items.size)
        assertFalse(vm.state.value.endReached)
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(25, vm.state.value.items.size)
        assertTrue(vm.state.value.endReached)
        assertEquals(listOf(0, 20), archives.pages.map { it.second })
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(2, archives.pages.size)
    }

    @Test
    fun `the filter shows only its statuses and never a running print`() = runTest(dispatcher) {
        val archives = FakeArchives(
            listOf(
                print(1, HistoryStatus.SUCCESS),
                print(2, HistoryStatus.CANCELLED),
                print(3, HistoryStatus.FAILED),
                print(4, HistoryStatus.IN_PROGRESS),
            ),
        )
        val vm = PreviousPrintsViewModel(archives, null)
        advanceUntilIdle()
        assertEquals(listOf("1", "2", "3"), vm.state.value.visible.map { it.id })
        vm.setFilter(PreviousPrintFilter.CANCELLED)
        assertEquals(listOf("2"), vm.state.value.visible.map { it.id })
        vm.setFilter(PreviousPrintFilter.FAILED)
        assertEquals(listOf("3"), vm.state.value.visible.map { it.id })
    }

    @Test
    fun `a failed read shows the reason and a retry loads again`() = runTest(dispatcher) {
        val archives = FakeArchives(listOf(print(1, HistoryStatus.SUCCESS))).apply { failFirst = true }
        val vm = PreviousPrintsViewModel(archives, null)
        advanceUntilIdle()
        assertTrue(vm.state.value.error != null)
        assertTrue(vm.state.value.items.isEmpty())
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)
        assertEquals(null, vm.state.value.error)
    }
}
