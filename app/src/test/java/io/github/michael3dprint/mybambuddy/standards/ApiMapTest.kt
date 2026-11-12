package io.github.michael3dprint.mybambuddy.standards

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps docs/API_MAP.md honest: every route the app declares is listed there, and nothing is listed that the app no longer calls. */
class ApiMapTest {
    private val root = File("..").canonicalFile
    private val verb = Regex("""@(GET|POST|PATCH|PUT|DELETE)\("([^"]+)"\)""")
    private val row = Regex("""\| `(GET|POST|PATCH|PUT|DELETE) ([^`]+)` \|""")

    private fun declared(): Set<String> = File(root, "app/src/main/java/io/github/michael3dprint/mybambuddy/data/api").listFiles { f ->
        f.name.endsWith("Api.kt")
    }
        .orEmpty().flatMap { f -> verb.findAll(f.readText()).map { "${it.groupValues[1]} ${it.groupValues[2]}" }.toList() }.toSet()

    private fun listed(): Set<String> = row.findAll(File(root, "docs/API_MAP.md").readText()).map {
        "${it.groupValues[1]} ${it.groupValues[2]}"
    }.toSet()

    @Test
    fun `every endpoint the app declares is in the API map`() {
        val missing = declared() - listed()
        assertTrue("Add these to docs/API_MAP.md (and say how far they were checked): $missing", missing.isEmpty())
    }

    @Test
    fun `the API map lists only endpoints the app still declares`() {
        val stale = listed() - declared()
        assertTrue("Remove these from docs/API_MAP.md, the app no longer declares them: $stale", stale.isEmpty())
    }
}
