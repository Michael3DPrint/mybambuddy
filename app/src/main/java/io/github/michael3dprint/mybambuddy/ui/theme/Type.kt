package io.github.michael3dprint.mybambuddy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

/** Tabular figures: digits all take the same width, so a number that updates every few seconds does not make its neighbours jump. */
private const val TABULAR = "tnum"

/**
 * The app's type scale (docs/design/15-ui-modernization.md): the Material 3 scale with the weights set in the scale,
 * so call sites do not pass `fontWeight`. Only [Typography.titleSmall] and [Typography.labelSmall], the two styles that carry
 * live numbers, use tabular figures.
 */
val Typography = Typography(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
)
