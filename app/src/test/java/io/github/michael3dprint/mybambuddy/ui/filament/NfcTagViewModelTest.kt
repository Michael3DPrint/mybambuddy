package io.github.michael3dprint.mybambuddy.ui.filament

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.domain.model.UsageEntry
import io.github.michael3dprint.mybambuddy.domain.nfc.DetectedTag
import io.github.michael3dprint.mybambuddy.domain.nfc.NfcGateway
import io.github.michael3dprint.mybambuddy.domain.nfc.OpenTag3d
import io.github.michael3dprint.mybambuddy.domain.nfc.TagWriteOutcome
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class NfcTagViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val spool = Spool(5, "Bambu Lab", "PLA", "Basic", "Blue", "307FE2", 1000, 0, "04AABBCCDD", null)

    private class Gateway(override val isAvailable: Boolean = true, override val isEnabled: Boolean = true) : NfcGateway {
        val flow = MutableSharedFlow<DetectedTag>(extraBufferCapacity = 4)
        val listening = mutableListOf<Boolean>()
        override val tags: SharedFlow<DetectedTag> = flow

        override fun setListening(on: Boolean) {
            listening += on
        }
    }

    private class Tag(override val uidHex: String, var write: TagWriteOutcome = TagWriteOutcome.Success, var payload: ByteArray? = null) :
        DetectedTag {
        var written: ByteArray? = null

        override suspend fun readMimePayload(mimeType: String) = payload

        override suspend fun writeMimeRecord(mimeType: String, payload: ByteArray): TagWriteOutcome {
            written = payload
            return write
        }
    }

    private class Spools(val known: List<Spool>, var link: ApiResult<Unit> = ApiResult.Success(Unit)) : SpoolRepository {
        val linked = mutableListOf<Pair<Int, String>>()

        override suspend fun getSpools() = ApiResult.Success(known)

        override suspend fun createSpool(draft: SpoolDraft): ApiResult<Spool> = ApiResult.Success(known.first())

        override suspend fun linkTag(spoolId: Int, tagUid: String): ApiResult<Unit> {
            linked += spoolId to tagUid
            return link
        }

        override suspend fun getUsage(spoolId: Int): ApiResult<List<UsageEntry>> = ApiResult.Success(emptyList())

        override suspend fun canWeigh(): Boolean = false

        override suspend fun updateWeight(spoolId: Int, grossGrams: Double): ApiResult<Unit> =
            ApiResult.Error(message = "n/a", errorType = ApiErrorType.UNSUPPORTED)
    }

    private val gateway = Gateway()
    private val spools = Spools(listOf(spool))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `no NFC hardware or NFC switched off gives a problem and never starts listening`() = runTest(dispatcher) {
        val noNfc = NfcTagViewModel(Gateway(isAvailable = false), spools)
        noNfc.startWrite(spool)
        assertTrue((noNfc.dialog.value as NfcDialogState.Problem).message.contains("no NFC"))
        val off = Gateway(isEnabled = false)
        val vm = NfcTagViewModel(off, spools)
        vm.startRead()
        assertTrue((vm.dialog.value as NfcDialogState.Problem).message.contains("turned off"))
        assertTrue(off.listening.isEmpty())
    }

    @Test
    fun `writing a tag writes the payload, links the UID and stops listening`() = runTest(dispatcher) {
        val vm = NfcTagViewModel(gateway, spools)
        vm.startWrite(spool)
        advanceUntilIdle()
        assertEquals(NfcDialogState.WaitingToWrite(spool), vm.dialog.value)
        val tag = Tag("04AABBCCDD")
        gateway.flow.tryEmit(tag)
        advanceUntilIdle()
        assertTrue(tag.written != null && tag.written!!.size == OpenTag3d.PAYLOAD_SIZE)
        assertEquals(listOf(5 to "04AABBCCDD"), spools.linked)
        assertTrue(vm.dialog.value is NfcDialogState.Done)
        assertFalse(gateway.listening.last())
    }

    @Test
    fun `a successful link tells the screen to reload, a failed one does not`() = runTest(dispatcher) {
        val vm = NfcTagViewModel(gateway, spools)
        var linkedCount = 0
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.linked.collect { linkedCount++ } }
        vm.startWrite(spool)
        advanceUntilIdle()
        gateway.flow.tryEmit(Tag("04AABBCCDD"))
        advanceUntilIdle()
        assertEquals(1, linkedCount)

        spools.link = ApiResult.Error(message = "m", errorType = ApiErrorType.REJECTED, serverDetail = "no")
        vm.startWrite(spool)
        advanceUntilIdle()
        gateway.flow.tryEmit(Tag("04AABBCCDD"))
        advanceUntilIdle()
        job.cancel()
        assertEquals(1, linkedCount)
    }

    @Test
    fun `a tag that is too small or read-only is explained and nothing is linked`() = runTest(dispatcher) {
        val vm = NfcTagViewModel(gateway, spools)
        vm.startWrite(spool)
        advanceUntilIdle()
        gateway.flow.tryEmit(Tag("04AA", TagWriteOutcome.TooSmall(48)))
        advanceUntilIdle()
        assertTrue((vm.dialog.value as NfcDialogState.Problem).message.contains("48"))
        gateway.flow.tryEmit(Tag("04BB", TagWriteOutcome.ReadOnly))
        advanceUntilIdle()
        assertTrue((vm.dialog.value as NfcDialogState.Problem).message.contains("read-only"))
        assertTrue(spools.linked.isEmpty())
    }

    @Test
    fun `when the tag is written but linking fails the user is told which part failed`() = runTest(dispatcher) {
        spools.link = ApiResult.Error(message = "m", errorType = ApiErrorType.REJECTED, serverDetail = "Tag belongs to another spool")
        val vm = NfcTagViewModel(gateway, spools)
        vm.startWrite(spool)
        advanceUntilIdle()
        gateway.flow.tryEmit(Tag("04AABBCCDD"))
        advanceUntilIdle()
        val message = (vm.dialog.value as NfcDialogState.Problem).message
        assertTrue(message, message.startsWith("Tag written, but linking failed") && message.contains("another spool"))
    }

    @Test
    fun `reading finds the spool by UID, case-insensitively, and decodes the tag`() = runTest(dispatcher) {
        val vm = NfcTagViewModel(gateway, spools)
        vm.startRead()
        advanceUntilIdle()
        gateway.flow.tryEmit(Tag("04aabbccdd", payload = OpenTag3d.encode(spool.toTagData())))
        advanceUntilIdle()
        val scanned = vm.dialog.value as NfcDialogState.Scanned
        assertEquals(spool, scanned.spool)
        assertEquals("PLA", scanned.fromTag?.material)
        assertFalse(gateway.listening.last())
    }

    @Test
    fun `a tag tapped outside the app opens its spool and writes nothing`() = runTest(dispatcher) {
        val tag = Tag("04AABBCCDD", payload = OpenTag3d.encode(spool.toTagData()))
        val vm = NfcTagViewModel(gateway, spools)
        vm.openFromTag(tag)
        advanceUntilIdle()
        assertEquals(spool, (vm.dialog.value as NfcDialogState.Scanned).spool)
        assertEquals(null, tag.written)
        assertTrue(spools.linked.isEmpty())
    }

    @Test
    fun `a tapped tag with an oversized payload is shown by UID only`() = runTest(dispatcher) {
        val vm = NfcTagViewModel(gateway, spools)
        vm.openFromTag(Tag("0499999999", payload = ByteArray(OpenTag3d.MAX_PAYLOAD_SIZE + 1)))
        advanceUntilIdle()
        val scanned = vm.dialog.value as NfcDialogState.Scanned
        assertEquals(null, scanned.fromTag)
        assertEquals(null, scanned.spool)
    }

    @Test
    fun `an unknown tag is reported with no spool`() = runTest(dispatcher) {
        val vm = NfcTagViewModel(gateway, spools)
        vm.startRead()
        advanceUntilIdle()
        gateway.flow.tryEmit(Tag("0499999999"))
        advanceUntilIdle()
        val scanned = vm.dialog.value as NfcDialogState.Scanned
        assertEquals(null, scanned.spool)
        assertEquals(null, scanned.fromTag)
    }

    @Test
    fun `closing stops listening and asking to load closes the dialog and emits the spool`() = runTest(dispatcher) {
        val vm = NfcTagViewModel(gateway, spools)
        val requested = mutableListOf<Spool>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.loadRequests.collect { requested += it } }
        vm.startRead()
        advanceUntilIdle()
        vm.requestLoad(spool)
        advanceUntilIdle()
        job.cancel()
        assertEquals(NfcDialogState.Closed, vm.dialog.value)
        assertFalse(gateway.listening.last())
        assertEquals(listOf(spool), requested)
    }

    @Test
    fun `the tag data written for a spool carries its material, brand and colour`() {
        val data = spool.toTagData()
        assertEquals("PLA", data.material)
        assertEquals("Bambu Lab", data.manufacturer)
        assertEquals("307FE2FF", data.rgba)
        assertEquals(1000, data.weightGrams)
    }
}
