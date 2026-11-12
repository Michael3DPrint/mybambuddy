package io.github.michael3dprint.mybambuddy.domain.policy

/** Decides between the phone layout and the two-pane layout from the screen width. Pure, so it is unit tested. */
object ScreenWidthPolicy {
    /** From this width in dp the app shows a navigation rail and two panes. */
    const val WIDE_FROM_DP = 600

    /** True for a tablet, or a foldable that is opened up. */
    fun isWide(widthDp: Int): Boolean = widthDp >= WIDE_FROM_DP
}
