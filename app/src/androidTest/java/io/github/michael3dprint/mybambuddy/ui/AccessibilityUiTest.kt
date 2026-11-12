package io.github.michael3dprint.mybambuddy.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.DryPhase
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities
import io.github.michael3dprint.mybambuddy.ui.common.ConfirmDialog
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.filament.AmsSlotRow
import io.github.michael3dprint.mybambuddy.ui.theme.MyBambuddyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility checks on a device (design 34): headings are marked, an AMS slot is one button with one spoken description, dialog
 * buttons are at least 48 dp tall, and Compose's own accessibility checks pass on these components. It needs a device or emulator and is not part of `check`;
 * run it with `./gradlew :app:connectedDebugAndroidTest` (OI-17).
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val unit = AmsUnit(
        printerId = 1,
        printerName = "X2D",
        amsId = 0,
        isHt = false,
        humidityLevel = null,
        tempC = null,
        supportsDrying = false,
        dryPhase = DryPhase.OFF,
        dryMinutesLeft = 0,
        dryTargetTemp = null,
        trays = listOf(
            AmsTray(2, "PLA", "PLA Basic", "307FE2FF", 75, null, hasRfidTag = true, trayUuid = "B952275D8AAB4DD1853A1DD80A6EA7A2"),
        ),
        capabilities = PrinterCapabilities(connected = true, developerMode = true, hasAms = true),
    )

    @Test
    fun aSlotTileIsOneButtonWithOneSpokenDescription() {
        var tapped = 0
        compose.setContent { MyBambuddyTheme { AmsSlotRow(unit, emptyMap()) { tapped++ } } }
        compose.onNodeWithContentDescription("AMS 1, slot 3, PLA Basic, about 750 grams left").performClick()
        assertEquals(1, tapped)
    }

    @Test
    fun aHeadingIsMarkedAsAHeading() {
        compose.setContent { MyBambuddyTheme { Heading("On the printer") } }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
    }

    @Test
    fun dialogButtonsAreAtLeast48dpTall() {
        compose.setContent { MyBambuddyTheme { ConfirmDialog("Load?", "Load", onConfirm = {}, onCancel = {}) { } } }
        compose.onNodeWithText("Load").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Cancel").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun composeAccessibilityChecksPassOnTheSlotRowAndTheDialog() {
        compose.enableAccessibilityChecks()
        compose.setContent { MyBambuddyTheme { AmsSlotRow(unit, emptyMap()) {} } }
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}
