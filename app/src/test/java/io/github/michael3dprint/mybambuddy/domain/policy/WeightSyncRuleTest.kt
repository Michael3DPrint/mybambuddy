package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightSyncRuleTest {
    private val uuid = "B952275D8AAB4DD1853A1DD80A6EA7A2"

    private fun tray(percent: Int? = 75, loaded: Int? = 2, trayUuid: String = uuid) =
        AmsTray(2, "PLA", "PLA Basic", "307FE2FF", percent, loaded, hasRfidTag = true, trayUuid = trayUuid)

    private fun spool(id: Int = 2, used: Int = 1000, label: Int = 1000, core: Int? = 250, trayUuid: String = uuid) =
        Spool(id, "Bambu Lab", "PLA", "Basic", "Blue", "307FE2", label, used, null, null, trayUuid = trayUuid, coreWeightGrams = core)

    private fun refusal(plan: WeightSyncPlan) = (plan as WeightSyncPlan.Refused).reason

    @Test
    fun `a Bambu slot is matched on its tray UUID and the plan shows both numbers`() {
        val plan = WeightSyncRule.plan(tray(), listOf(spool()), canWeigh = true, printerBusy = false) as WeightSyncPlan.Ready
        assertEquals(2, plan.spool.id)
        assertEquals(75, plan.percent)
        assertEquals(0, plan.currentRemainingGrams)
        assertEquals(750, plan.newRemainingGrams)
        // The weight route takes a gross reading, so the empty spool's own weight is added.
        assertEquals(1000.0, plan.grossGrams, 0.0)
    }

    @Test
    fun `the label weight scales the amount`() {
        val plan = WeightSyncRule.plan(tray(percent = 50), listOf(spool(used = 0, label = 3000)), true, false) as WeightSyncPlan.Ready
        assertEquals(1500, plan.newRemainingGrams)
        assertEquals(1750.0, plan.grossGrams, 0.0)
    }

    @Test
    fun `a slot with no tray UUID, no percent or a zero percent is refused`() {
        assertTrue(refusal(WeightSyncRule.plan(tray(trayUuid = ""), listOf(spool()), true, false)).contains("Bambu spool"))
        assertTrue(refusal(WeightSyncRule.plan(tray(trayUuid = "0".repeat(32)), listOf(spool()), true, false)).contains("Bambu spool"))
        assertTrue(refusal(WeightSyncRule.plan(tray(percent = null), listOf(spool()), true, false)).contains("isn't reporting"))
        assertTrue(refusal(WeightSyncRule.plan(tray(percent = 0), listOf(spool()), true, false)).contains("isn't reporting"))
    }

    @Test
    fun `a built-in inventory or a printing printer is refused`() {
        assertTrue(refusal(WeightSyncRule.plan(tray(), listOf(spool()), canWeigh = false, printerBusy = false)).contains("Spoolman"))
        assertTrue(refusal(WeightSyncRule.plan(tray(), listOf(spool()), canWeigh = true, printerBusy = true)).contains("printing"))
    }

    @Test
    fun `no spool or several spools with that tag are refused`() {
        assertTrue(refusal(WeightSyncRule.plan(tray(), emptyList(), true, false)).contains("No spool"))
        assertTrue(
            refusal(WeightSyncRule.plan(tray(), listOf(spool(id = 2, trayUuid = "AAAA".repeat(8))), true, false)).contains("No spool"),
        )
        assertTrue(refusal(WeightSyncRule.plan(tray(), listOf(spool(id = 2), spool(id = 3)), true, false)).contains("More than one"))
    }

    @Test
    fun `a slot recorded as a different spool is refused`() {
        val reason = refusal(WeightSyncRule.plan(tray(loaded = 9), listOf(spool(id = 2)), true, false))
        assertTrue(reason, reason.contains("#9") && reason.contains("#2"))
        // An unrecorded slot is fine: the tag decides.
        assertTrue(WeightSyncRule.plan(tray(loaded = null), listOf(spool()), true, false) is WeightSyncPlan.Ready)
    }

    @Test
    fun `an unknown spool weight is refused rather than guessed`() {
        assertTrue(refusal(WeightSyncRule.plan(tray(), listOf(spool(core = null)), true, false)).contains("doesn't know"))
        assertTrue(refusal(WeightSyncRule.plan(tray(), listOf(spool(label = 0)), true, false)).contains("doesn't know"))
    }

    @Test
    fun `an inventory that already matches is refused as nothing to do`() {
        val reason = refusal(WeightSyncRule.plan(tray(percent = 75), listOf(spool(used = 250)), true, false))
        assertTrue(reason.contains("already matches"))
    }
}
