package io.github.michael3dprint.mybambuddy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The one shape scale (docs/design/15-ui-modernization.md). Chosen so the two dominant radii already in use, 12 dp and 16 dp,
 * are [Shapes.medium] and [Shapes.large]: most call sites become a one-word change.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
