package io.github.michael3dprint.mybambuddy.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing, icon and component sizes screens are allowed to use, so the same gap is the same number everywhere.
 *
 * Every spacing value here is a multiple of 4 dp, and a test keeps it that way.
 *
 * Screens use these tokens for padding and spacing; a few one-off sizes are still literals (OI-23, `docs/design/15-ui-modernization.md`).
 */
object Dimens {
    /** Gaps between things, from a hairline inside a row to the space between sections. */
    object Space {
        val xs: Dp = 4.dp
        val s: Dp = 8.dp
        val m: Dp = 12.dp
        val l: Dp = 16.dp
        val xl: Dp = 24.dp
        val xxl: Dp = 32.dp
    }

    /**
     * Icon sizes. [l] and [xl] are for placeholders and empty states, not for buttons.
     * Named `IconSize` rather than `Icon` so it can never be confused with Material 3's `Icon` composable at a call site.
     */
    object IconSize {
        val s: Dp = 16.dp
        val m: Dp = 24.dp
        val l: Dp = 48.dp
        val xl: Dp = 64.dp
    }

    /** The floor for anything tappable, per the Android accessibility guidance. */
    val touchTarget: Dp = 48.dp

    /** Height of the camera snapshot box on a printer card. */
    val cameraCard: Dp = 160.dp

    /** Height of the print-progress track. */
    val progressBar: Dp = 8.dp
}
