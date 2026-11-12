package io.github.michael3dprint.mybambuddy.domain.help

import io.github.michael3dprint.mybambuddy.domain.model.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpLibraryTest {
    @Test
    fun `every topic id has exactly one page`() {
        HelpTopicId.values().forEach { id -> assertEquals(id.name, 1, HelpLibrary.topics.count { it.id == id }) }
    }

    @Test
    fun `every page has steps, a read date, and sources unless it is this app's own page`() {
        HelpLibrary.topics.forEach { t ->
            assertTrue(t.id.name, t.steps.isNotEmpty())
            assertTrue(t.id.name, Regex("\\d{4}-\\d{2}-\\d{2}").matches(t.readOn))
            val own = t.id == HelpTopicId.CONNECT_APP || t.id == HelpTopicId.T_CLOUD
            if (!own) assertTrue("${t.id} needs a source", t.sources.isNotEmpty())
            t.sources.forEach { assertTrue(it.url, it.url.startsWith("https://")) }
        }
    }

    @Test
    fun `no page holds a real address`() {
        val ip = Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b")
        HelpLibrary.topics.forEach { t ->
            val text = (t.steps.map { it.text + (it.command ?: "") } + t.summary + t.unverified).joinToString(" ")
            assertFalse(t.id.name, ip.containsMatchIn(text))
        }
    }

    @Test
    fun `each install guide has its own install pages and the shared ones`() {
        val docker = HelpLibrary.guide(InstallMethod.DOCKER).map { it.id }
        val ha = HelpLibrary.guide(InstallMethod.HA_OS).map { it.id }
        assertTrue(HelpTopicId.INSTALL_BAMBUDDY_DOCKER in docker && HelpTopicId.INSTALL_BAMBUDDY_HA !in docker)
        assertTrue(HelpTopicId.INSTALL_BAMBUDDY_HA in ha && HelpTopicId.INSTALL_BAMBUDDY_DOCKER !in ha)
        val shared = HelpLibrary.topics.filter { it.method == null }.map { it.id }.filterNot { it.name.startsWith("T_") }
        assertTrue(docker.containsAll(shared) && ha.containsAll(shared))
    }

    @Test
    fun `a page that uses a community project is marked so the notice shows`() {
        val community = HelpLibrary.topics.filter { it.community }.map { it.id }
        assertTrue(
            community.containsAll(
                listOf(HelpTopicId.INSTALL_BAMBUDDY_HA, HelpTopicId.SPOOLMAN_HA, HelpTopicId.SLICER_HA, HelpTopicId.TAILSCALE_SERVER_HA),
            ),
        )
        assertTrue(COMMUNITY_NOTICE.contains("Not made, maintained or supported"))
    }

    @Test
    fun `every error kind maps to a page that exists, or to the index`() {
        ErrorKind.values().forEach { kind ->
            val id = ErrorHelp.topicFor(kind)
            if (id != null) assertEquals(kind.name, 1, HelpLibrary.topics.count { it.id == id })
        }
        assertEquals(HelpTopicId.T_KEY_REJECTED, ErrorHelp.topicFor(ErrorKind.UNAUTHORIZED))
    }

    @Test
    fun `the text sent to a computer holds titles and links only`() {
        val text = HelpShare.textFor(HelpLibrary.topic(HelpTopicId.INSTALL_BAMBUDDY_DOCKER))
        assertTrue(text.contains("https://wiki.bambuddy.cool/getting-started/docker/"))
        assertFalse(text.contains("docker compose up"))
    }

    @Test
    fun `troubleshooting pages are the T_ ones`() {
        assertEquals(HelpLibrary.topics.count { it.id.name.startsWith("T_") }, HelpLibrary.troubleshooting().size)
    }
}
