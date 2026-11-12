package io.github.michael3dprint.mybambuddy.domain.alerts

import io.github.michael3dprint.mybambuddy.data.api.ApiErrorType
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.DueState
import io.github.michael3dprint.mybambuddy.domain.model.HmsStatus
import io.github.michael3dprint.mybambuddy.domain.model.MaintenanceItem
import io.github.michael3dprint.mybambuddy.domain.model.PrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterMaintenance
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.domain.model.UsageEntry
import io.github.michael3dprint.mybambuddy.domain.repo.MaintenanceRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsTest {
    private val on = AlertSettings(enabled = true, printerOffline = true)

    private fun obs(
        state: PrinterState,
        codes: Set<String> = emptySet(),
        spools: List<SpoolObservation> = emptyList(),
        due: List<DueObservation> = emptyList(),
    ) = Observation(listOf(PrinterObservation(1, "X2D", state, codes)), spools, due)

    private fun run(previous: AlertMemory, now: Observation, settings: AlertSettings = on) = AlertRules.evaluate(previous, now, settings)

    @Test
    fun `the first run only records, it never alerts`() {
        val result =
            run(
                AlertMemory(),
                obs(PrinterState.ERROR, setOf("E1"), listOf(SpoolObservation(1, "s", 3, null)), listOf(DueObservation(1, 1, "x"))),
            )
        assertTrue(result.alerts.isEmpty())
        assertTrue(result.memory.initialised)
    }

    @Test
    fun `print finished and failed are detected from a printing state`() {
        val printing = run(AlertMemory(), obs(PrinterState.PRINTING)).memory
        assertEquals(AlertType.PRINT_FINISHED, run(printing, obs(PrinterState.FINISHED)).alerts.single().type)
        assertEquals(AlertType.PRINT_FAILED, run(printing, obs(PrinterState.ERROR)).alerts.single().type)
        // A failed print reported as the printer's FAILED state raises the same alert.
        assertEquals(AlertType.PRINT_FAILED, run(printing, obs(PrinterState.FAILED)).alerts.single().type)
        assertTrue(run(printing, obs(PrinterState.PRINTING)).alerts.isEmpty())
        val idle = run(AlertMemory(), obs(PrinterState.IDLE)).memory
        assertTrue(run(idle, obs(PrinterState.FINISHED)).alerts.isEmpty()) // finishing without having seen it print is not news
    }

    @Test
    fun `only new error codes alert`() {
        val first = run(AlertMemory(), obs(PrinterState.IDLE, setOf("A"))).memory
        assertTrue(run(first, obs(PrinterState.IDLE, setOf("A"))).alerts.isEmpty())
        val result = run(first, obs(PrinterState.IDLE, setOf("A", "B")))
        assertEquals(AlertType.NEW_ERROR, result.alerts.single().type)
    }

    @Test
    fun `offline needs two checks in a row`() {
        val seen = run(AlertMemory(), obs(PrinterState.IDLE)).memory
        val once = run(seen, obs(PrinterState.OFFLINE))
        assertTrue(once.alerts.isEmpty())
        val twice = run(once.memory, obs(PrinterState.OFFLINE))
        assertEquals(AlertType.PRINTER_OFFLINE, twice.alerts.single().type)
        assertTrue(run(twice.memory, obs(PrinterState.OFFLINE)).alerts.isEmpty()) // not again
    }

    @Test
    fun `low filament alerts once and re-arms only after real recovery`() {
        fun spools(pct: Int) = listOf(SpoolObservation(7, "PLA", pct, null))
        val base = run(AlertMemory(), obs(PrinterState.IDLE, spools = spools(50))).memory
        val low = run(base, obs(PrinterState.IDLE, spools = spools(15)))
        assertEquals(AlertType.LOW_FILAMENT, low.alerts.single().type)
        assertEquals("a low-filament alert carries the spool so a tap can open it", 7, low.alerts.single().spoolId)
        assertTrue(run(low.memory, obs(PrinterState.IDLE, spools = spools(12))).alerts.isEmpty())
        val hover = run(low.memory, obs(PrinterState.IDLE, spools = spools(23))) // above 20 but inside the margin
        assertTrue(run(hover.memory, obs(PrinterState.IDLE, spools = spools(18))).alerts.isEmpty())
        val recovered = run(hover.memory, obs(PrinterState.IDLE, spools = spools(80))).memory
        assertEquals(AlertType.LOW_FILAMENT, run(recovered, obs(PrinterState.IDLE, spools = spools(10))).alerts.single().type)
    }

    @Test
    fun `new maintenance due alerts once`() {
        val base = run(AlertMemory(), obs(PrinterState.IDLE)).memory
        val due = listOf(DueObservation(3, 1, "X2D: Clean rods"))
        val first = run(base, obs(PrinterState.IDLE, due = due))
        assertEquals(AlertType.MAINTENANCE_DUE, first.alerts.single().type)
        assertTrue(run(first.memory, obs(PrinterState.IDLE, due = due)).alerts.isEmpty())
    }

    @Test
    fun `disabled categories and the master switch are respected`() {
        val printing = run(AlertMemory(), obs(PrinterState.PRINTING)).memory
        assertTrue(run(printing, obs(PrinterState.FINISHED), on.copy(printFinished = false)).alerts.isEmpty())
        assertTrue(run(printing, obs(PrinterState.FINISHED), on.copy(enabled = false)).alerts.isEmpty())
    }

    // ---- runner ----
    private class Memory(var value: AlertMemory = AlertMemory()) : AlertMemoryStore {
        override fun load() = value

        override fun save(memory: AlertMemory) {
            value = memory
        }
    }

    private class Notifier : AlertNotifier {
        val posted = mutableListOf<Alert>()
        var summaries = 0

        override fun post(alert: Alert): Boolean {
            posted += alert
            return true
        }

        override fun postSummary(count: Int) {
            summaries += count
        }
    }

    private class Printers(var result: ApiResult<List<Printer>>) : PrinterRepository {
        override fun getPrintersFlow(pollIntervalMs: Long): Flow<ApiResult<List<Printer>>> = emptyFlow()

        override suspend fun fetchPrinters() = result

        override suspend fun fetchPrinter(printerId: String): ApiResult<Printer> =
            ApiResult.Error(message = "n/a", errorType = ApiErrorType.UNKNOWN)

        override suspend fun fetchPrinterStatus(printerId: String): ApiResult<PrintStatus> = ApiResult.Success(PrintStatus())
    }

    private class Spools(var result: ApiResult<List<Spool>>) : SpoolRepository {
        override suspend fun getSpools() = result

        override suspend fun createSpool(draft: SpoolDraft): ApiResult<Spool> =
            ApiResult.Error(message = "n/a", errorType = ApiErrorType.UNKNOWN)

        override suspend fun linkTag(spoolId: Int, tagUid: String): ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun getUsage(spoolId: Int): ApiResult<List<UsageEntry>> = ApiResult.Success(emptyList())

        override suspend fun canWeigh(): Boolean = false

        override suspend fun updateWeight(spoolId: Int, grossGrams: Double): ApiResult<Unit> =
            ApiResult.Error(message = "n/a", errorType = ApiErrorType.UNSUPPORTED)
    }

    private class Maint(var result: ApiResult<List<PrinterMaintenance>>) : MaintenanceRepository {
        override suspend fun getOverview() = result

        override suspend fun markDone(itemId: Int): ApiResult<Unit> = ApiResult.Success(Unit)
    }

    private val net = ApiResult.Error(message = "down", errorType = ApiErrorType.NETWORK_ERROR)

    private fun printer(state: PrinterState, errors: List<HmsStatus> = emptyList()) =
        Printer(id = "1", name = "X2D", model = "X2D", state = state, hmsErrors = errors)

    private fun runner(p: Printers, s: Spools, m: Maint, mem: Memory, n: Notifier, settings: AlertSettings = on) = AlertRunner(
        p,
        s,
        m,
        mem,
        n,
        object : AlertSettingsSource {
            override fun load() = settings
        },
    )

    @Test
    fun `runner alerts on a finished print and remembers it`() = runBlocking {
        val printers = Printers(ApiResult.Success(listOf(printer(PrinterState.PRINTING))))
        val mem = Memory()
        val notifier = Notifier()
        val r = runner(printers, Spools(ApiResult.Success(emptyList())), Maint(ApiResult.Success(emptyList())), mem, notifier)
        r.run()
        printers.result = ApiResult.Success(listOf(printer(PrinterState.FINISHED)))
        assertEquals(1, r.run())
        assertEquals("Print finished", notifier.posted.single().title)
        assertEquals(0, r.run()) // the same state again is not news
    }

    @Test
    fun `an unreachable server is not an alert and does not erase memory`() = runBlocking {
        val printers = Printers(ApiResult.Success(listOf(printer(PrinterState.PRINTING))))
        val mem = Memory()
        val notifier = Notifier()
        val r = runner(printers, Spools(ApiResult.Success(emptyList())), Maint(ApiResult.Success(emptyList())), mem, notifier)
        r.run()
        val remembered = mem.value
        printers.result = net
        val spools = Spools(net)
        val down = runner(printers, spools, Maint(net), mem, notifier)
        assertEquals(0, down.run())
        assertEquals(remembered, mem.value)
        // when only the printer call fails, remembered printer state survives and the print can still be reported later
        val partial = runner(Printers(net), Spools(ApiResult.Success(emptyList())), Maint(ApiResult.Success(emptyList())), mem, notifier)
        partial.run()
        assertEquals(remembered.states, mem.value.states)
    }

    @Test
    fun `disabled alerts do nothing and at most five notifications are posted per run`() = runBlocking {
        val notifier = Notifier()
        val off = runner(
            Printers(ApiResult.Success(emptyList())),
            Spools(ApiResult.Success(emptyList())),
            Maint(ApiResult.Success(emptyList())),
            Memory(),
            notifier,
            AlertSettings(enabled = false),
        )
        assertEquals(0, off.run())

        val many = (1..9).map { Spool(it, "b", "PLA", "", "", "", 1000, 950, null, null) }
        val mem = Memory(AlertMemory(initialised = true))
        val loud =
            runner(
                Printers(ApiResult.Success(emptyList())),
                Spools(ApiResult.Success(many)),
                Maint(ApiResult.Success(emptyList())),
                mem,
                notifier,
            )
        loud.run()
        assertEquals(5, notifier.posted.size)
        assertEquals(4, notifier.summaries)
    }

    @Test
    fun `maintenance overview feeds due alerts`() = runBlocking {
        val items = listOf(MaintenanceItem(3, 1, "Clean rods", DueState.DUE, 1f, "5 h overdue", null))
        val maint = Maint(ApiResult.Success(listOf(PrinterMaintenance(1, "X2D", 10.0, items))))
        val notifier = Notifier()
        val mem = Memory(AlertMemory(initialised = true))
        runner(Printers(ApiResult.Success(emptyList())), Spools(ApiResult.Success(emptyList())), maint, mem, notifier).run()
        assertEquals("Maintenance due", notifier.posted.single().title)
    }
}
