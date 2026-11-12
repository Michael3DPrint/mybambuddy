package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.DueState
import io.github.michael3dprint.mybambuddy.domain.model.MaintenanceItem
import io.github.michael3dprint.mybambuddy.domain.model.PrinterMaintenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaintenanceBadgeTest {
    private fun item(state: DueState) =
        MaintenanceItem(id = 1, printerId = 1, name = "x", dueState = state, progress = 0f, remainingLabel = "", lastDone = null)

    private fun printer(vararg states: DueState) = PrinterMaintenance(1, "X2D", 0.0, states.map { item(it) })

    @Test
    fun `due items give a count and win over soon`() {
        assertEquals("2 due", MaintenanceBadge.label(printer(DueState.DUE, DueState.DUE, DueState.SOON)))
    }

    @Test
    fun `only soon items give a soon count`() = assertEquals("1 soon", MaintenanceBadge.label(printer(DueState.SOON, DueState.OK)))

    @Test
    fun `nothing due and no data give no badge`() {
        assertNull(MaintenanceBadge.label(printer(DueState.OK)))
        assertNull(MaintenanceBadge.label(null))
    }
}
