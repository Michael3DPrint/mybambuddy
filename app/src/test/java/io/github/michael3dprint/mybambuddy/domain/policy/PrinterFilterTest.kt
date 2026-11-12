package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.DryPhase
import io.github.michael3dprint.mybambuddy.domain.model.PrinterMaintenance
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.model.QueueStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterFilterTest {
    private fun job(id: Int, printer: Int?) = QueueItem(id, printer, "P$printer", "job$id", QueueStatus.PENDING, id, null, false)

    private val jobs = listOf(job(1, 1), job(2, 2), job(3, 1), job(4, null))
    private val upkeep = listOf(PrinterMaintenance(1, "X2D", 1.0, emptyList()), PrinterMaintenance(2, "P1S", 2.0, emptyList()))

    @Test
    fun `no printer id returns every job unchanged`() {
        assertEquals(jobs, PrinterFilter.queue(jobs, null))
    }

    @Test
    fun `a printer id keeps only that printer's jobs and drops unassigned ones`() {
        assertEquals(listOf(1, 3), PrinterFilter.queue(jobs, 1).map { it.id })
        assertEquals(listOf(2), PrinterFilter.queue(jobs, 2).map { it.id })
        assertEquals(emptyList<QueueItem>(), PrinterFilter.queue(jobs, 9))
    }

    @Test
    fun `upkeep is narrowed the same way`() {
        assertEquals(upkeep, PrinterFilter.maintenance(upkeep, null))
        assertEquals(listOf(2), PrinterFilter.maintenance(upkeep, 2).map { it.printerId })
    }

    @Test
    fun `AMS units are narrowed by printer`() {
        fun unit(printer: Int, ams: Int) = AmsUnit(printer, "P$printer", ams, false, null, null, false, DryPhase.OFF, 0, null, emptyList())
        val units = listOf(unit(1, 0), unit(2, 0), unit(1, 1))
        assertEquals(units, PrinterFilter.ams(units, null))
        assertEquals(listOf(0, 1), PrinterFilter.ams(units, 1).map { it.amsId })
        assertEquals(emptyList<AmsUnit>(), PrinterFilter.ams(units, 9))
    }
}
