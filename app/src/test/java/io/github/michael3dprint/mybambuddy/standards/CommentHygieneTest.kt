package io.github.michael3dprint.mybambuddy.standards

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps code comments factual (`docs/ENGINEERING_STANDARDS.md` section 5): a comment cites only designs and open items that exist,
 * never a section number of a design, and carries no history, dates, people or to-do markers.
 */
class CommentHygieneTest {
    private val root = File("..").canonicalFile
    private val sources = File(root, "app/src").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    private val designs = File(root, "docs/design").listFiles().orEmpty()
        .mapNotNull { Regex("^(\\d+)-").find(it.name)?.groupValues?.get(1)?.toInt() }.toSet()
    private val openItems = Regex("OI-\\d+").findAll(File(root, "docs/OPEN_ITEMS.md").readText()).map { it.value }.toSet()

    private val designCite = Regex("""[Dd]esigns? ((?:\d+(?:,? and |, | to )?)+)""")
    private val sectionAfterDesign = Regex("""design \d+[, ]+sections? \d""", RegexOption.IGNORE_CASE)
    private val markers = Regex("""\b(TODO|FIXME|XXX|HACK)\b""")
    private val history = Regex("""\b(previously|formerly|since fixed|no longer used)\b""")
    private val people = Regex("""\b[Tt]he owner\b|\bowner's\b|\bGemini\b|\bClaude\b""")
    private val date = Regex("""\b20\d\d-\d\d-\d\d\b""")

    private fun commentLines(text: String): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var inBlock = false
        text.split("\n").forEachIndexed { idx, line ->
            val t = line.trim()
            val isComment = inBlock ||
                t.startsWith("/*") ||
                t.startsWith("*") ||
                ("//" in t && "http" !in t && '"' !in t.substringBefore("//"))
            if ("/*" in t && "*/" !in t) inBlock = true
            if ("*/" in t) inBlock = false
            if (isComment) out += (idx + 1) to t
        }
        return out
    }

    @Test
    fun `code comments cite only what exists and carry no history`() {
        val bad = mutableListOf<String>()
        for (f in sources) {
            val isTest = "/src/test/" in f.path || "/src/androidTest/" in f.path
            for ((line, c) in commentLines(f.readText())) {
                val at = "${f.relativeTo(root).path}:$line"
                designCite.findAll(c).forEach { m ->
                    Regex("\\d+").findAll(m.groupValues[1]).map { it.value.toInt() }.filter { it !in designs }
                        .forEach { bad += "$at: cites design $it, which does not exist" }
                }
                if (sectionAfterDesign.containsMatchIn(c)) bad += "$at: cite the design number only, not a section"
                Regex("OI-\\d+").findAll(c).filter { it.value !in openItems }.forEach { bad += "$at: ${it.value} is not an open item" }
                if (markers.containsMatchIn(c)) bad += "$at: to-do marker"
                if (history.containsMatchIn(c)) bad += "$at: history wording"
                if (people.containsMatchIn(c)) bad += "$at: say \"the user\", not a person or tool"
                if (!isTest && date.containsMatchIn(c)) bad += "$at: date in a comment"
            }
        }
        assertTrue(
            "Comments break the rules in docs/ENGINEERING_STANDARDS.md section 5:\n" + bad.take(60).joinToString("\n"),
            bad.isEmpty(),
        )
    }
}
