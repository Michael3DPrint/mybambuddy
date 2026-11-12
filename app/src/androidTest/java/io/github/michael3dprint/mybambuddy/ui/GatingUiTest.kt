package io.github.michael3dprint.mybambuddy.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.michael3dprint.mybambuddy.domain.model.HmsStatus
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.policy.ReadinessEvaluator
import io.github.michael3dprint.mybambuddy.ui.common.RemoteControlSection
import io.github.michael3dprint.mybambuddy.ui.detail.PrinterStatusSection
import io.github.michael3dprint.mybambuddy.ui.readiness.RemoteControlLine
import io.github.michael3dprint.mybambuddy.ui.theme.MyBambuddyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks on a device that the safety gating is visible: a printer card says whether Developer LAN mode is on, the tool list is hidden
 * unless Developer LAN mode is off, and the Clear errors button is disabled when its tool is off and asks before it sends. Written
 * without a device to run on, so it has been compiled but not yet run (OI-17).
 */
@RunWith(AndroidJUnit4::class)
class GatingUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val full = PrinterCapabilities(connected = true, developerMode = true)
    private val limited = PrinterCapabilities(connected = true, developerMode = false)

    @Test
    fun printerCardLineSaysLimitedWhenRemoteControlIsOff() {
        val report = ReadinessEvaluator.evaluate("1", "X2D", limited)
        compose.setContent { MyBambuddyTheme { RemoteControlLine(report, onOpen = {}) } }
        compose.onNodeWithText("Remote control: Limited").assertExists()
    }

    @Test
    fun printerCardLineSaysFullWhenRemoteControlIsOn() {
        val report = ReadinessEvaluator.evaluate("1", "X2D", full)
        compose.setContent { MyBambuddyTheme { RemoteControlLine(report, onOpen = {}) } }
        compose.onNodeWithText("Remote control: Full").assertExists()
    }

    @Test
    fun remoteControlSectionIsCollapsedUntilTappedAndThenListsOnlyTheControls() {
        val report = ReadinessEvaluator.evaluate("1", "X2D", limited)
        compose.setContent { MyBambuddyTheme { RemoteControlSection(report) } }
        compose.onNodeWithText("Pause and resume").assertDoesNotExist()
        compose.onNodeWithText("Remote control", substring = true).performClick()
        compose.onNodeWithText("Pause and resume").assertExists()
        compose.onNodeWithText("Refresh status").assertDoesNotExist()
    }

    @Test
    fun clearButtonIsDisabledWhenItsToolIsOff() {
        val errors = listOf(HmsStatus(code = "0700_2000", description = "Test error"))
        compose.setContent {
            MyBambuddyTheme {
                PrinterStatusSection(errors, PrinterState.IDLE, clearEnabled = false, reason = "Developer LAN mode is off.", onClear = {})
            }
        }
        compose.onNodeWithText("Printer Status", substring = true).performClick()
        compose.onNodeWithText("Clear HMS errors").assertIsNotEnabled()
        compose.onNodeWithText("Developer LAN mode is off.").assertExists()
    }

    @Test
    fun clearButtonAsksFirstAndOnlySendsWhenConfirmed() {
        var sent = 0
        val errors = listOf(HmsStatus(code = "0700_2000", description = "Test error"))
        compose.setContent {
            MyBambuddyTheme { PrinterStatusSection(errors, PrinterState.IDLE, clearEnabled = true, reason = null, onClear = { sent++ }) }
        }
        compose.onNodeWithText("Printer Status", substring = true).performClick()
        compose.onNodeWithText("Clear HMS errors").assertIsEnabled().performClick()
        assertEquals(0, sent)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, sent)
        compose.onNodeWithText("Clear HMS errors").performClick()
        compose.onNodeWithText("Clear").performClick()
        assertEquals(1, sent)
    }
}
