package io.github.michael3dprint.mybambuddy.ui.history

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.ErrorKind
import io.github.michael3dprint.mybambuddy.domain.model.HistoryItem
import io.github.michael3dprint.mybambuddy.domain.model.HistoryStatus
import io.github.michael3dprint.mybambuddy.domain.repo.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeHistoryRepository: FakeHistoryRepository
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeHistoryRepository = FakeHistoryRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load fetches first page and updates uiState`() = runTest(testDispatcher) {
        val sampleItems = (1..20).map { i ->
            HistoryItem(
                id = "item_$i",
                name = "Print $i",
                printerName = "Printer X",
                status = HistoryStatus.SUCCESS,
                formattedDate = "Jan 15, 2025",
                formattedDuration = "1h",
            )
        }
        fakeHistoryRepository.resultPages[0] = ApiResult.Success(sampleItems)

        viewModel = HistoryViewModel(fakeHistoryRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(20, state.items.size)
        assertTrue(state.canLoadMore)
        assertNull(state.errorState)
    }

    @Test
    fun `loadNextPage appends items and sets canLoadMore false when items count less than page size`() = runTest(testDispatcher) {
        val page1 = (1..20).map { i ->
            HistoryItem(
                id = "item_$i",
                name = "Print $i",
                printerName = "Printer X",
                status = HistoryStatus.SUCCESS,
                formattedDate = "Jan 15, 2025",
                formattedDuration = "1h",
            )
        }
        val page2 = (21..25).map { i ->
            HistoryItem(
                id = "item_$i",
                name = "Print $i",
                printerName = "Printer X",
                status = HistoryStatus.CANCELLED,
                formattedDate = "Jan 14, 2025",
                formattedDuration = "30m",
            )
        }
        fakeHistoryRepository.resultPages[0] = ApiResult.Success(page1)
        fakeHistoryRepository.resultPages[20] = ApiResult.Success(page2)

        viewModel = HistoryViewModel(fakeHistoryRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(20, viewModel.uiState.value.items.size)
        assertTrue(viewModel.uiState.value.canLoadMore)

        viewModel.loadNextPage()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoadingNextPage)
        assertEquals(25, state.items.size)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun `refresh resets items and reloads from offset 0`() = runTest(testDispatcher) {
        val initialItems = listOf(
            HistoryItem(
                id = "old_1",
                name = "Old Print",
                printerName = "Printer 1",
                status = HistoryStatus.SUCCESS,
                formattedDate = "Jan 10, 2025",
                formattedDuration = "2h",
            ),
        )
        fakeHistoryRepository.resultPages[0] = ApiResult.Success(initialItems)

        viewModel = HistoryViewModel(fakeHistoryRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val refreshedItems = listOf(
            HistoryItem(
                id = "new_1",
                name = "New Print",
                printerName = "Printer 1",
                status = HistoryStatus.SUCCESS,
                formattedDate = "Jan 16, 2025",
                formattedDuration = "45m",
            ),
        )
        fakeHistoryRepository.resultPages[0] = ApiResult.Success(refreshedItems)

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertEquals(1, state.items.size)
        assertEquals("new_1", state.items.first().id)
    }

    @Test
    fun `repository error sets error state in uiState`() = runTest(testDispatcher) {
        fakeHistoryRepository.resultPages[0] = ApiResult.Error(
            code = 500,
            message = "Server internal error",
            errorType = ApiErrorType.SERVER_ERROR,
        )

        viewModel = HistoryViewModel(fakeHistoryRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ErrorKind.SERVER_ERROR, state.errorState?.kind)
        assertTrue(state.items.isEmpty())
    }

    private class FakeHistoryRepository : HistoryRepository {
        val resultPages = mutableMapOf<Int, ApiResult<List<HistoryItem>>>()

        override suspend fun getHistory(limit: Int, offset: Int): ApiResult<List<HistoryItem>> =
            resultPages[offset] ?: ApiResult.Success(emptyList())
    }
}
