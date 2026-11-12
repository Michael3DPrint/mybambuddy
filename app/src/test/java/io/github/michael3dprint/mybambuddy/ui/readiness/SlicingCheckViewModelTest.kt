package io.github.michael3dprint.mybambuddy.ui.readiness

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.CheckLevel
import io.github.michael3dprint.mybambuddy.domain.model.SlicingCheck
import io.github.michael3dprint.mybambuddy.domain.model.SlicingReadiness
import io.github.michael3dprint.mybambuddy.domain.repo.SlicingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SlicingCheckViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class Repo(var result: ApiResult<SlicingReadiness>) : SlicingRepository {
        var calls = 0

        override suspend fun check(): ApiResult<SlicingReadiness> {
            calls++
            return result
        }
    }

    private val ready = SlicingReadiness(listOf(SlicingCheck("switch", "on", true, CheckLevel.BLOCKER, "ok")))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts loading, then shows the result with the check time`() = runTest(dispatcher) {
        val vm = SlicingCheckViewModel(Repo(ApiResult.Success(ready))) { 1234L }
        assertEquals(SlicingState.Loading, vm.state.value)
        vm.check()
        advanceUntilIdle()
        assertEquals(SlicingState.Done(ready, 1234L), vm.state.value)
    }

    @Test
    fun `a failed read shows an error, and checking again reads again`() = runTest(dispatcher) {
        val repo = Repo(ApiResult.Error(null, "down", ApiErrorType.NETWORK_ERROR))
        val vm = SlicingCheckViewModel(repo)
        vm.check()
        advanceUntilIdle()
        assertTrue(vm.state.value is SlicingState.Error)
        repo.result = ApiResult.Success(ready)
        vm.check()
        advanceUntilIdle()
        assertTrue(vm.state.value is SlicingState.Done)
        assertEquals(2, repo.calls)
    }
}
