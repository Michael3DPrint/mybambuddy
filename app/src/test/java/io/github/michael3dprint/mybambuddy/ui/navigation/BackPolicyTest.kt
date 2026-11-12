package io.github.michael3dprint.mybambuddy.ui.navigation

import io.github.michael3dprint.mybambuddy.AppTab
import org.junit.Assert.assertEquals
import org.junit.Test

class BackPolicyTest {
    @Test
    fun `every nested page goes back to the main page`() {
        listOf(Routes.DETAIL, Routes.ALERTS).forEach { route ->
            AppTab.entries.forEach { tab -> assertEquals("$route on $tab", BackStep.GO_TO_MAIN, BackPolicy.onBack(route, tab)) }
        }
    }

    @Test
    fun `another tab goes back to the Printers tab`() {
        listOf(AppTab.QUEUE, AppTab.FILAMENT, AppTab.HISTORY, AppTab.SETTINGS).forEach {
            assertEquals(BackStep.SELECT_PRINTERS, BackPolicy.onBack(Routes.MAIN, it))
        }
    }

    @Test
    fun `the Printers tab and the setup page leave the app`() {
        assertEquals(BackStep.LEAVE_APP, BackPolicy.onBack(Routes.MAIN, AppTab.PRINTERS))
        AppTab.entries.forEach { assertEquals(BackStep.LEAVE_APP, BackPolicy.onBack(Routes.SETUP, it)) }
    }

    @Test
    fun `an unknown page leaves the app rather than trapping the user`() {
        assertEquals(BackStep.LEAVE_APP, BackPolicy.onBack("something-new", AppTab.HISTORY))
    }

    @Test
    fun `a full walk from a notification to the exit takes the expected steps`() {
        var route = Routes.DETAIL
        var tab = AppTab.PRINTERS
        val steps = mutableListOf<BackStep>()
        while (true) {
            val step = BackPolicy.onBack(route, tab)
            steps += step
            when (step) {
                BackStep.GO_TO_MAIN -> route = Routes.MAIN
                BackStep.SELECT_PRINTERS -> tab = AppTab.PRINTERS
                BackStep.LEAVE_APP -> break
            }
        }
        assertEquals(listOf(BackStep.GO_TO_MAIN, BackStep.LEAVE_APP), steps)
    }
}
