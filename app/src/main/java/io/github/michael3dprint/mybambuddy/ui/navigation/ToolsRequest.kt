package io.github.michael3dprint.mybambuddy.ui.navigation

/**
 * The one-shot request "open this printer on its Tools tab", raised by tapping the Remote control line on a printer card. It belongs to one
 * printer, is used once, and any other way of opening a printer cancels it, so opening a printer from its card never lands on Tools by mistake.
 */
object ToolsRequest {
    /** The pending request after the Remote control line of printer [printerId] was tapped. */
    fun afterLineTap(printerId: String): String? = printerId

    /** The pending request after the printer's card was tapped: none, so the page opens on Overview. */
    fun afterCardTap(): String? = null

    /** True when the pending request [pending] is for the page of [printerId], which should then switch to Tools. */
    fun appliesTo(pending: String?, printerId: String): Boolean = pending == printerId

    /** The pending request after the page acted on it: none, so it is not used again. */
    fun afterHandled(): String? = null
}
