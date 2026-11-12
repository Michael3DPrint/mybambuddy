package io.github.michael3dprint.mybambuddy.ui.common

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * Placeholders for content that is about to arrive, used where the shape of what is coming is known. A spinner stays for things with
 * no shape (a button, a photo). The pulse stands still when the system's animation scale is off.
 */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val context = LocalContext.current
    val animated = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val alpha = if (animated) {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val pulse by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
        pulse
    } else {
        0.7f
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.medium),
    )
}

/** A stack of card-shaped placeholders: the loading state of every list screen. */
@Composable
fun ListSkeleton(modifier: Modifier = Modifier, rows: Int = 4, rowHeight: Dp = 88.dp) {
    Column(modifier.padding(Dimens.Space.l), verticalArrangement = Arrangement.spacedBy(Dimens.Space.l)) {
        repeat(rows) { SkeletonBlock(height = rowHeight) }
    }
}
