package io.github.michael3dprint.mybambuddy.ui.filament

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.CatalogFilament
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.domain.model.UsageEntry
import io.github.michael3dprint.mybambuddy.domain.repo.CatalogRepository
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class FilamentViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val spool = Spool(5, "Bambu Lab", "PLA", "Basic", "Blue", "307FE2", 1000, 0, null, null)
    private val item = CatalogFilament("c1", "Polymaker", "Teal", "PLA", "00A0A0", 750, 200, 215)

    private class Spools : SpoolRepository {
        var list: ApiResult<List<Spool>> = ApiResult.Success(emptyList())
        var create: ApiResult<Spool>? = null
        var usage: ApiResult<List<UsageEntry>> = ApiResult.Success(emptyList())
        val created = mutableListOf<SpoolDraft>()

        override suspend fun getSpools() = list

        override suspend fun createSpool(draft: SpoolDraft): ApiResult<Spool> {
            created += draft
            return create ?: ApiResult.Error(message = "no", errorType = ApiErrorType.UNKNOWN)
        }

        override suspend fun linkTag(spoolId: Int, tagUid: String): ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun getUsage(spoolId: Int) = usage

        override suspend fun canWeigh(): Boolean = false

        override suspend fun updateWeight(spoolId: Int, grossGrams: Double): ApiResult<Unit> =
            ApiResult.Error(message = "n/a", errorType = ApiErrorType.UNSUPPORTED)
    }

    private class Catalog : CatalogRepository {
        var result: ApiResult<List<CatalogFilament>> = ApiResult.Success(emptyList())
        val queries = mutableListOf<String>()

        override suspend fun search(query: String): ApiResult<List<CatalogFilament>> {
            queries += query
            return result
        }
    }

    private val spools = Spools()
    private val catalog = Catalog()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `it loads the list on creation, and a failed refresh keeps what is shown`() = runTest(dispatcher) {
        spools.list = ApiResult.Success(listOf(spool))
        val vm = FilamentViewModel(spools, catalog)
        advanceUntilIdle()
        assertEquals(SpoolListState.Success(listOf(spool)), vm.list.value)

        spools.list = ApiResult.Error(message = "m", errorType = ApiErrorType.NETWORK_ERROR)
        vm.refresh()
        advanceUntilIdle()
        assertTrue(
            "an error replaces the list only through the state, with the printer-free wording",
            vm.list.value is SpoolListState.Error,
        )
        val error = (vm.list.value as SpoolListState.Error).error
        assertFalse(error.message.contains("printer", ignoreCase = true))
    }

    @Test
    fun `usage loads, and a failure is a message`() = runTest(dispatcher) {
        val entries = listOf(UsageEntry("2026-11-09", "Benchy", 12, "completed"))
        spools.usage = ApiResult.Success(entries)
        val vm = FilamentViewModel(spools, catalog)
        vm.loadUsage(5)
        advanceUntilIdle()
        assertEquals(UsageState.Loaded(entries), vm.usage.value)
        spools.usage = ApiResult.Error(message = "m", errorType = ApiErrorType.NOT_FOUND)
        vm.loadUsage(5)
        advanceUntilIdle()
        assertTrue(vm.usage.value is UsageState.Failed)
    }

    @Test
    fun `the add form opens with a prefill and closes back to empty`() = runTest(dispatcher) {
        val vm = FilamentViewModel(spools, catalog)
        vm.openAdd(SpoolDraft(material = "PETG"))
        assertTrue(vm.add.value.visible)
        assertEquals("PETG", vm.add.value.draft.material)
        vm.closeAdd()
        assertEquals(AddSpoolState(), vm.add.value)
    }

    @Test
    fun `a search waits for typing to settle, then fills the results`() = runTest(dispatcher) {
        catalog.result = ApiResult.Success(listOf(item))
        val vm = FilamentViewModel(spools, catalog)
        advanceUntilIdle()
        vm.onQueryChanged("po")
        vm.onQueryChanged("poly")
        advanceTimeBy(300)
        assertTrue("not searched yet", catalog.queries.isEmpty())
        advanceTimeBy(200)
        advanceUntilIdle()
        assertEquals(listOf("poly"), catalog.queries)
        assertEquals(listOf(item), vm.add.value.results)
    }

    @Test
    fun `no matches says to fill the fields by hand, and offline says the catalogue needs a connection`() = runTest(dispatcher) {
        val vm = FilamentViewModel(spools, catalog)
        advanceUntilIdle()
        vm.onQueryChanged("zzz")
        advanceTimeBy(500)
        advanceUntilIdle()
        assertTrue(vm.add.value.searchMessage!!.contains("No matches"))
        catalog.result = ApiResult.Error(message = "m", errorType = ApiErrorType.NETWORK_ERROR)
        vm.onQueryChanged("yyy")
        advanceTimeBy(500)
        advanceUntilIdle()
        assertTrue(vm.add.value.searchMessage!!.contains("needs a connection"))
    }

    @Test
    fun `picking a catalogue entry fills the draft`() = runTest(dispatcher) {
        val vm = FilamentViewModel(spools, catalog)
        vm.pick(item)
        val draft = vm.add.value.draft
        assertEquals("Polymaker", draft.brand)
        assertEquals("PLA", draft.material)
        assertEquals("00A0A0", draft.colorHex)
        assertEquals(750, draft.labelWeightGrams)
        assertEquals(200, draft.coreWeightGrams)
    }

    @Test
    fun `an invalid draft is never sent and the form says why`() = runTest(dispatcher) {
        val vm = FilamentViewModel(spools, catalog)
        vm.openAdd(SpoolDraft(material = "PLA", colorHex = "teal"))
        assertFalse(vm.add.value.canSave)
        assertTrue(vm.add.value.problem!!.contains("Colour"))
        vm.save()
        advanceUntilIdle()
        assertTrue(spools.created.isEmpty())
    }

    @Test
    fun `saving a valid draft adds it, says so, refreshes and closes the form`() = runTest(dispatcher) {
        spools.create = ApiResult.Success(spool)
        val vm = FilamentViewModel(spools, catalog)
        val seen = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { seen += it } }
        vm.openAdd(SpoolDraft(material = "PLA", colorHex = "307FE2"))
        vm.save()
        advanceUntilIdle()
        job.cancel()
        assertEquals(1, spools.created.size)
        assertEquals(listOf("Added ${spool.title}"), seen)
        assertEquals(AddSpoolState(), vm.add.value)
    }

    @Test
    fun `a refused save keeps the form and shows the server's reason, not a printer error`() = runTest(dispatcher) {
        spools.create = ApiResult.Error(message = "m", errorType = ApiErrorType.REJECTED, serverDetail = "rgba: bad hex")
        val vm = FilamentViewModel(spools, catalog)
        vm.openAdd(SpoolDraft(material = "PLA"))
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.add.value.visible)
        assertFalse(vm.add.value.saving)
        assertEquals("rgba: bad hex", vm.add.value.saveError)
    }

    @Test
    fun `editing the draft clears an earlier save error`() = runTest(dispatcher) {
        spools.create = ApiResult.Error(message = "m", errorType = ApiErrorType.REJECTED, serverDetail = "bad")
        val vm = FilamentViewModel(spools, catalog)
        vm.openAdd(SpoolDraft(material = "PLA"))
        vm.save()
        advanceUntilIdle()
        vm.updateDraft { it.copy(note = "x") }
        assertEquals(null, vm.add.value.saveError)
    }
}
