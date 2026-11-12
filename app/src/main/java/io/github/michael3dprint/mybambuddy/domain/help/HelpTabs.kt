package io.github.michael3dprint.mybambuddy.domain.help

/** The three tabs of the Setup help screen (design 48). */
enum class HelpTab(val title: String) {
    GUIDE("Guide"),
    FIX("Fix a problem"),
    PHONE("This phone"),
}

/** Which tab the Setup help screen shows, so Back from a page lands where the user came from. Pure, so it is tested. */
object HelpTabs {
    /** A troubleshooting page belongs to "Fix a problem"; every other page belongs to "Guide". */
    fun forTopic(id: HelpTopicId): HelpTab = if (id.name.startsWith("T_")) HelpTab.FIX else HelpTab.GUIDE
}
