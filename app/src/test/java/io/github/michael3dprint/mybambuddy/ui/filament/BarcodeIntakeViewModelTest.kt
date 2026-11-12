package io.github.michael3dprint.mybambuddy.ui.filament

import io.github.michael3dprint.mybambuddy.domain.barcode.BarcodeOutcome
import io.github.michael3dprint.mybambuddy.domain.barcode.BarcodePrefs
import io.github.michael3dprint.mybambuddy.domain.barcode.BarcodeResolver
import io.github.michael3dprint.mybambuddy.domain.barcode.BarcodeScanner
import io.github.michael3dprint.mybambuddy.domain.barcode.EanLookup
import io.github.michael3dprint.mybambuddy.domain.barcode.EanLookupResult
import io.github.michael3dprint.mybambuddy.domain.barcode.OnlineLookupConsent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class BarcodeIntakeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class Scanner(var next: String?) : BarcodeScanner {
        override suspend fun scan() = next
    }

    private class Lookup(var result: EanLookupResult = EanLookupResult.Found("Bambu Lab PLA Basic Blue")) : EanLookup {
        val asked = mutableListOf<String>()

        override suspend fun lookup(ean: String): EanLookupResult {
            asked += ean
            return result
        }
    }

    private class Prefs(override var onlineLookup: OnlineLookupConsent = OnlineLookupConsent.ASK) : BarcodePrefs

    private val lookup = Lookup()
    private val prefs = Prefs()
    private lateinit var scanner: Scanner

    private fun vm(raw: String?): BarcodeIntakeViewModel {
        scanner = Scanner(raw)
        return BarcodeIntakeViewModel(scanner, BarcodeResolver(lookup, prefs))
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun collect(scope: kotlinx.coroutines.test.TestScope, vm: BarcodeIntakeViewModel, into: MutableList<BarcodeOutcome>) =
        scope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { vm.outcomes.collect { into += it } }

    @Test
    fun `plain text becomes a catalogue search and nothing is sent online`() = runTest(dispatcher) {
        val vm = vm("Polymaker PLA Pro")
        val seen = mutableListOf<BarcodeOutcome>()
        val job = collect(this, vm, seen)
        vm.scan()
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf(BarcodeOutcome.Search("Polymaker PLA Pro")), seen)
        assertTrue(lookup.asked.isEmpty())
        assertEquals(IntakeState.Idle, vm.state.value)
    }

    @Test
    fun `a cancelled scan ends quietly`() = runTest(dispatcher) {
        val vm = vm(null)
        val seen = mutableListOf<BarcodeOutcome>()
        val job = collect(this, vm, seen)
        vm.scan()
        advanceUntilIdle()
        job.cancel()
        assertTrue(seen.isEmpty())
        assertEquals(IntakeState.Idle, vm.state.value)
    }

    @Test
    fun `an EAN asks for consent first and sends nothing until the user agrees`() = runTest(dispatcher) {
        val vm = vm("6901234567890")
        vm.scan()
        advanceUntilIdle()
        assertEquals(IntakeState.AskConsent("6901234567890"), vm.state.value)
        assertTrue(lookup.asked.isEmpty())
    }

    @Test
    fun `agreeing looks the digits up and turns the title into a search`() = runTest(dispatcher) {
        val vm = vm("6901234567890")
        val seen = mutableListOf<BarcodeOutcome>()
        val job = collect(this, vm, seen)
        vm.scan()
        advanceUntilIdle()
        vm.answerConsent("6901234567890", allow = true)
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf("6901234567890"), lookup.asked)
        assertEquals(listOf<BarcodeOutcome>(BarcodeOutcome.Search("Bambu Lab PLA Basic Blue")), seen)
        assertEquals(IntakeState.Idle, vm.state.value)
    }

    @Test
    fun `refusing sends nothing and points to the catalogue`() = runTest(dispatcher) {
        val vm = vm("6901234567890")
        val seen = mutableListOf<BarcodeOutcome>()
        val job = collect(this, vm, seen)
        vm.scan()
        advanceUntilIdle()
        vm.answerConsent("6901234567890", allow = false)
        advanceUntilIdle()
        job.cancel()
        assertTrue(lookup.asked.isEmpty())
        assertTrue(seen.single() is BarcodeOutcome.NotFound)
    }

    @Test
    fun `a lasting answer is remembered and ask-once is not`() = runTest(dispatcher) {
        val vm = vm("6901234567890")
        vm.answerConsent("6901234567890", allow = true, consent = OnlineLookupConsent.ASK)
        advanceUntilIdle()
        assertEquals(OnlineLookupConsent.ASK, prefs.onlineLookup)
        vm.answerConsent("6901234567890", allow = true, consent = OnlineLookupConsent.ALWAYS)
        advanceUntilIdle()
        assertEquals(OnlineLookupConsent.ALWAYS, prefs.onlineLookup)
    }

    @Test
    fun `an unavailable service is reported, not crashed on`() = runTest(dispatcher) {
        lookup.result = EanLookupResult.Unavailable("rate limited")
        val vm = vm("6901234567890")
        val seen = mutableListOf<BarcodeOutcome>()
        val job = collect(this, vm, seen)
        vm.answerConsent("6901234567890", allow = true)
        advanceUntilIdle()
        job.cancel()
        assertTrue(seen.single() is BarcodeOutcome.NotFound)
    }
}
