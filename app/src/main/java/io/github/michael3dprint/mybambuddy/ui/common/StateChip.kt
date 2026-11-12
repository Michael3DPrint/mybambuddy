package io.github.michael3dprint.mybambuddy.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * The one pill used for a status everywhere (printer state, print result). Callers choose the label and colours; the shape, type and
 * spacing live here so the two screens cannot drift apart again. The icon is decorative: the label already says it.
 */
@Composable
fun StateChip(label: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    // The colours ease to the new state, so a printer changing state is noticed instead of blinking.
    val animatedContainer by animateColorAsState(container, tween(200), label = "chipContainer")
    val animatedContent by animateColorAsState(content, tween(200), label = "chipContent")
    Surface(color = animatedContainer, contentColor = animatedContent, shape = CircleShape, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = Dimens.Space.m, vertical = Dimens.Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space.xs),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
