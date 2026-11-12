package io.github.michael3dprint.mybambuddy.docs

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the design documents honest: every CamelCase name written in backticks in the markdown files under
 * `docs/design` and in `docs/ENGINEERING_STANDARDS.md` must be declared somewhere in the code, or be listed in `docs/doc-identifier-allowlist.txt` with a
 * reason. The reverse also holds: a name on the list that now exists in the code must come off it, so the list cannot go stale.
 * Gradle runs unit tests from the `app/` directory, so the documents are one level up.
 */
class DocumentationIdentifiersTest {
    private val root = File("..").canonicalFile
    private val nameInBackticks = Regex("`([A-Z][a-z0-9]+(?:[A-Z][A-Za-z0-9]*)+)(?:\\(\\))?`")
    private val word = Regex("[A-Za-z_][A-Za-z0-9_]*")

    private fun codeWords(): Set<String> {
        val sources = File(root, "app/src").walkTopDown().filter { it.isFile && it.extension in setOf("kt", "kts", "xml") }.toList() +
            listOfNotNull(File(root, "app/build.gradle.kts"), File(root, "gradle/libs.versions.toml")).filter { it.exists() }
        return sources.flatMap { word.findAll(it.readText()).map { m -> m.value }.toList() }.toSet()
    }

    private fun documentedNames(): Map<String, Set<String>> {
        val docs = File(root, "docs/design").listFiles { f -> f.extension == "md" }.orEmpty().toList() +
            File(root, "docs/ENGINEERING_STANDARDS.md")
        val found = mutableMapOf<String, MutableSet<String>>()
        docs.filter { it.exists() }.forEach { doc ->
            nameInBackticks.findAll(doc.readText()).forEach { found.getOrPut(it.groupValues[1]) { mutableSetOf() } += doc.name }
        }
        return found
    }

    private fun allowlist(): Set<String> = File(root, "docs/doc-identifier-allowlist.txt").readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val parts = line.split("|", limit = 2)
            assertTrue("Allowlist line needs a reason after '|': $line", parts.size == 2 && parts[1].isNotBlank())
            parts[0].trim()
        }.toSet()

    @Test
    fun `every documented name exists in the code or is explained on the allowlist`() {
        val code = codeWords()
        val allowed = allowlist()
        val unknown = documentedNames().filter { (name, _) -> name !in code && name !in allowed }
        assertTrue(
            "Documented names with no declaration and no allowlist entry (add them to docs/doc-identifier-allowlist.txt with a reason, " +
                "or fix the document): " + unknown.entries.joinToString { "${it.key} in ${it.value.sorted()}" },
            unknown.isEmpty(),
        )
    }

    @Test
    fun `a name on the allowlist that now exists in the code is removed from it`() {
        val code = codeWords()
        val stale = allowlist().filter { it in code }
        assertTrue("These allowlist entries now exist in the code; remove them: $stale", stale.isEmpty())
    }

    @Test
    fun `the allowlist only holds names that are still documented`() {
        val documented = documentedNames().keys
        val unused = allowlist().filter { it !in documented }
        assertTrue("These allowlist entries are no longer mentioned in any document; remove them: $unused", unused.isEmpty())
    }
}
