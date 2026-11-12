package io.github.michael3dprint.mybambuddy.domain.help

/** All help pages, in reading order, with lookups (design 47). */
object HelpLibrary {
    /** Every page: install, shared guides, then troubleshooting. */
    val topics: List<HelpTopic> = InstallGuides.all + SharedGuides.all + TroubleshootingTopics.all

    /** The page for [id]; every id has one (a test checks it). */
    fun topic(id: HelpTopicId): HelpTopic = topics.first { it.id == id }

    /** The guide for [method]: its own install pages and the shared pages, in reading order. */
    fun guide(method: InstallMethod): List<HelpTopic> = order.mapNotNull { id ->
        topic(id).takeIf { it.method == null || it.method == method }
    }

    /** The troubleshooting pages. */
    fun troubleshooting(): List<HelpTopic> = topics.filter { it.id.name.startsWith("T_") }

    private val order = listOf(
        HelpTopicId.NETWORK_ADVICE,
        HelpTopicId.INSTALL_BAMBUDDY_DOCKER,
        HelpTopicId.INSTALL_BAMBUDDY_HA,
        HelpTopicId.FIRST_RUN,
        HelpTopicId.SPOOLMAN_DOCKER,
        HelpTopicId.SPOOLMAN_HA,
        HelpTopicId.SPOOLMAN_LINK,
        HelpTopicId.SLICER_DOCKER,
        HelpTopicId.SLICER_HA,
        HelpTopicId.SLICER_LINK,
        HelpTopicId.TAILSCALE_SERVER_DOCKER,
        HelpTopicId.TAILSCALE_SERVER_HA,
        HelpTopicId.TAILSCALE_PHONE,
        HelpTopicId.CONNECT_APP,
    )
}
