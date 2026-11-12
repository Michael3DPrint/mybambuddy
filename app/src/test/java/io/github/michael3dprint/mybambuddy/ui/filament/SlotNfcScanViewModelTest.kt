package io.github.michael3dprint.mybambuddy.ui.filament

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.DryPhase
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.domain.model.UsageEntry
import io.github.michael3dprint.mybambuddy.domain.nfc.DetectedTag
import io.github.michael3dprint.mybambuddy.domain.nfc.NfcGateway
import io.github.michael3dprint.mybambuddy.domain.nfc.TagWriteOutcome
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
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
class SlotNfcScanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val spool = Spool(5, "Bambu Lab", "PLA", "Basic", "Blue", "307FE2", 1000, 0, "04AABBCCDD", null)
    private val unit = AmsUnit(1, "X2D", 0, false, null, null, false, DryPhase.OFF, 0, null, emptyList())
    private val target = SlotTarget(unit, 2)

    private class Gateway(override val isAvailable: Boolean = true, override val isEnabled: Boolean = true) : NfcGateway {
        val flow = MutableSharedFlow<DetectedTag>(extraBufferCapacity = 4)
        val listening = mutableListOf<Boolean>()
        override val tags: SharedFlow<DetectedTag> = flow

        override fun setListening(on: Boolean) {
            listening += on
        }
    }

    private class Tag(override val uidHex: String) : DetectedTag {
        override suspend fun readMimePayload(mimeType: String): ByteArray? = null

        override suspend fun writeMimeRecord(mimeType: String, payload: ByteArray): TagWriteOutcome = error("a scan never writes")
    }

    private class Spools(val known: List<Spool>) : SpoolRepository {
        override suspend fun getSpools() = ApiResult.Success(known)

        override suspend fun createSpool(draft: SpoolDraft): ApiResult<Spool> = error("unused")

        override suspend fun linkTag(spoolId: Int, tagUid: String): ApiResult<Unit> = error("a scan never links")

        override suspend fun getUsage(spoolId: Int): ApiResult<List<UsageEntry>> = error("unused")

        override suspend fun canWeigh() = false

        override suspend fun updateWeight(spoolId: Int, grossGrams: Double): ApiResult<Unit> = error("a scan never weighs")
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a known tag is handed on with its slot and listening stops`() = runTest(dispatcher) {
        val gateway = Gateway()
        val vm = SlotNfcScanViewModel(gateway, Spools(listOf(spool)))
        val found = mutableListOf<SlotScanResult>()
        val job = launch { vm.found.collect { found += it } }
        vm.scan(target)
        advanceUntilIdle()
        assertEquals(SlotScanState.Waiting(target), vm.state.value)
        gateway.flow.emit(Tag("04aabbccdd"))
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf(SlotScanResult(spool, target)), found)
        assertEquals(SlotScanState.Idle, vm.state.value)
        assertEquals(listOf(true, false), gateway.listening)
    }

    @Test
    fun `an unknown tag shows a problem and hands nothing on`() = runTest(dispatcher) {
        val gateway = Gateway()
        val vm = SlotNfcScanViewModel(gateway, Spools(listOf(spool)))
        val found = mutableListOf<SlotScanResult>()
        val job = launch { vm.found.collect { found += it } }
        vm.scan(target)
        advanceUntilIdle()
        gateway.flow.emit(Tag("DEADBEEF"))
        advanceUntilIdle()
        job.cancel()
        assertTrue(found.isEmpty())
        assertTrue((vm.state.value as SlotScanState.Problem).message.contains("No spool"))
    }

    @Test
    fun `no NFC or NFC turned off is reported and nothing listens`() = runTest(dispatcher) {
        val none = Gateway(isAvailable = false)
        val vmNone = SlotNfcScanViewModel(none, Spools(emptyList()))
        vmNone.scan(target)
        assertTrue((vmNone.state.value as SlotScanState.Problem).message.contains("no NFC"))
        val off = Gateway(isEnabled = false)
        val vmOff = SlotNfcScanViewModel(off, Spools(emptyList()))
        vmOff.scan(target)
        assertTrue((vmOff.state.value as SlotScanState.Problem).message.contains("turned off"))
        assertTrue(none.listening.isEmpty() && off.listening.isEmpty())
    }

    @Test
    fun `cancel stops listening and goes back to idle`() = runTest(dispatcher) {
        val gateway = Gateway()
        val vm = SlotNfcScanViewModel(gateway, Spools(listOf(spool)))
        vm.scan(target)
        advanceUntilIdle()
        vm.cancel()
        assertEquals(SlotScanState.Idle, vm.state.value)
        assertEquals(false, gateway.listening.last())
    }
}
