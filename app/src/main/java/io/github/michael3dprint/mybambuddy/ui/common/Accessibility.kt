package io.github.michael3dprint.mybambuddy.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.michael3dprint.mybambuddy.domain.policy.SpokenText

/** Marks this element as a heading, so screen reader users can jump between sections (design 34). */
fun Modifier.heading(): Modifier = semantics { heading() }

/** Announces this element's text to a screen reader when it appears or changes, without interrupting (for results and errors). */
fun Modifier.politeLive(): Modifier = semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }

/** Shows a message in a snackbar that stays long enough to read and can be dismissed, for results a screen reader user must not miss. */
suspend fun androidx.compose.material3.SnackbarHostState.showMessage(message: String) {
    showSnackbar(message, withDismissAction = true, duration = androidx.compose.material3.SnackbarDuration.Long)
}

/** Gives this element the spoken form of [display] (see [SpokenText]) instead of reading its symbols literally. */
fun Modifier.spokenAs(display: String): Modifier = semantics { contentDescription = SpokenText.of(display) }

/** A [Text] that is a heading for screen readers. It takes the same arguments as [Text] that titles use. */
@Composable
fun Heading(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = text,
        modifier = modifier.heading().spokenAs(text),
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        style = style,
    )
}

/** A [Text] whose screen reader description is the spoken form of what it shows (see [SpokenText]): symbols and abbreviations in words. */
@Composable
fun Spoken(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = text,
        modifier = modifier.spokenAs(text),
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        style = style,
    )
}

/** The font scale from which layouts switch to their large-text form. Below it nothing changes (design 34). */
object LargeFont {
    /** Android's "large" settings start around 1.3. */
    const val FROM_SCALE = 1.3f

    /** True when [fontScale] is large enough for the large-text layouts. */
    fun isLarge(fontScale: Float): Boolean = fontScale >= FROM_SCALE
}

/** True when the phone's font size is large (see [LargeFont]). */
@Composable
fun rememberLargeFont(): Boolean = LargeFont.isLarge(LocalDensity.current.fontScale)

/** One line normally, two lines at large font sizes, so names are not cut off. */
@Composable
fun nameMaxLines(): Int = if (rememberLargeFont()) 2 else 1
