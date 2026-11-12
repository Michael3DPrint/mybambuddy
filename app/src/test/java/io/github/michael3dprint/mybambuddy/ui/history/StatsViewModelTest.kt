package io.github.michael3dprint.mybambuddy.ui.history

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.PrintStats
import io.github.michael3dprint.mybambuddy.domain.policy.StatsPeriod
import io.github.michael3dprint.mybambuddy.domain.repo.StatsRepository
import kotlinx.coroutines.CompletableDeferred
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
class StatsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private fun stats(total: Int) = PrintStats(total, total, 0, 0, 1.0, 1.0, emptyList(), emptyList())

    private class Repo : StatsRepository {
        val asked = mutableListOf<StatsPeriod>()
        val gates = mutableMapOf<StatsPeriod, CompletableDeferred<ApiResult<PrintStats>>>()

        override suspend fun getStats(period: StatsPeriod): ApiResult<PrintStats> {
            asked += period
            return gates.getValue(period).await()
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `it starts on this month and requests nothing until asked`() = runTest(dispatcher) {
        val repo = Repo()
        val vm = StatsViewModel(repo)
        advanceUntilIdle()
        assertEquals(StatsPeriod.THIS_MONTH, vm.period.value)
        assertTrue(repo.asked.isEmpty())
    }

    @Test
    fun `changing the period loads that period`() = runTest(dispatcher) {
        val repo = Repo()
        repo.gates[StatsPeriod.ALL_TIME] = CompletableDeferred(ApiResult.Success(stats(7)))
        val vm = StatsViewModel(repo)
        vm.select(StatsPeriod.ALL_TIME)
        advanceUntilIdle()
        assertEquals(StatsPeriod.ALL_TIME, vm.period.value)
        assertEquals(StatsState.Success(stats(7)), vm.state.value)
    }

    @Test
    fun `a slow reply for an older period cannot overwrite a newer one`() = runTest(dispatcher) {
        val repo = Repo()
        val slow = CompletableDeferred<ApiResult<PrintStats>>()
        repo.gates[StatsPeriod.THIS_MONTH] = slow
        repo.gates[StatsPeriod.LAST_30_DAYS] = CompletableDeferred(ApiResult.Success(stats(30)))
        val vm = StatsViewModel(repo)
        vm.refresh()
        advanceUntilIdle()
        vm.select(StatsPeriod.LAST_30_DAYS)
        advanceUntilIdle()
        slow.complete(ApiResult.Success(stats(1)))
        advanceUntilIdle()
        assertEquals(StatsState.Success(stats(30)), vm.state.value)
    }

    @Test
    fun `an error is shown and retry loads again`() = runTest(dispatcher) {
        val repo = Repo()
        repo.gates[StatsPeriod.THIS_MONTH] = CompletableDeferred(ApiResult.Error(message = "down", errorType = ApiErrorType.NETWORK_ERROR))
        val vm = StatsViewModel(repo)
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.state.value is StatsState.Error)
        repo.gates[StatsPeriod.THIS_MONTH] = CompletableDeferred(ApiResult.Success(stats(2)))
        vm.refresh()
        advanceUntilIdle()
        assertEquals(StatsState.Success(stats(2)), vm.state.value)
    }
}
