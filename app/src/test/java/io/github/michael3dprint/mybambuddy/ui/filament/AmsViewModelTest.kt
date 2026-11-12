package io.github.michael3dprint.mybambuddy.ui.filament

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
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
class AmsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val unit = testAmsUnit
    private val spool = testSpool
    private val ams = FakeAms()
    private lateinit var vm: AmsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = AmsViewModel(ams, FakeSpools(listOf(spool)))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `refresh shows units and a failed refresh keeps the last data`() = runTest(dispatcher) {
        ams.units = ApiResult.Success(listOf(unit))
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.state.value is AmsListState.Success)

        ams.units = ApiResult.Error(message = "down", errorType = ApiErrorType.NETWORK_ERROR)
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.state.value is AmsListState.Success)
    }

    @Test
    fun `choosing a slot loads the spool choices and dismissing clears the target`() = runTest(dispatcher) {
        vm.chooseSlot(unit, 2)
        advanceUntilIdle()
        assertEquals(listOf(spool), vm.spoolChoices.value)
        assertEquals(SlotTarget(unit, 2), vm.slotTarget.value)

        vm.dismissSlot()
        assertEquals(null, vm.slotTarget.value)
    }
}
