package io.github.michael3dprint.mybambuddy.standards

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the Markdown rules in `docs/WRITING_STANDARDS.md` for every document in the repository: one title first, a summary
 * paragraph after it, blank lines around blocks, fenced code with a language, one sentence per line, sentence-case headings,
 * `-` bullets, no trailing whitespace and one final newline. Each failure names the file and line.
 */
class DocumentFormatTest {
    private val root = File("..").canonicalFile
    private val skipDirs = setOf(".git", "build", ".gradle", ".idea", ".kotlin", ".claude", "artifacts", "node_modules")

    // CLAUDE.md only imports AGENTS.md, so it has no title or summary.
    private val exempt = setOf("CLAUDE.md")
    private val abbreviations = Regex("""\b(e\.g|i\.e|vs|etc|approx|incl|cf|No|Fig)\.\s""", RegexOption.IGNORE_CASE)
    private val sentenceBreak = Regex("""[.!?](\)|")? +[A-Z(]""")
    private val listItem = Regex("""^\s*(-|\d+\.) """)
    private val heading = Regex("""^(#{1,6}) (.*)$""")

    private fun markdownFiles() = root.walkTopDown().onEnter { it.name !in skipDirs }
        .filter { it.isFile && it.extension == "md" && it.name !in exempt }.toList()

    private fun stripInline(line: String) = line.replace(Regex("`[^`]*`"), "``")
        .replace(Regex("""\]\([^)]*\)"""), "]()")
        .replace(Regex("""https?://\S+"""), "URL")

    private fun isUpper(word: String) = word.any { it.isLetter() } && word.none { it.isLowerCase() }

    private fun issues(text: String): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        if ('\r' in text) out += 0 to "CRLF line endings"
        if (!text.endsWith("\n") || text.endsWith("\n\n")) out += 0 to "file must end with exactly one newline"
        val lines = text.replace("\r", "").split("\n").let { if (it.last().isEmpty()) it.dropLast(1) else it }
        var inFence = false
        var titles = 0
        var prevLevel = 0
        var firstContentSeen = false
        lines.forEachIndexed { idx, line ->
            val i = idx + 1
            val prv = if (idx >= 1) lines[idx - 1] else ""
            val nxt = lines.getOrElse(idx + 1) { "" }
            if (line != line.trimEnd()) out += i to "trailing whitespace"
            if ('\t' in line) out += i to "tab character"
            if (line.startsWith("```")) {
                if (!inFence) {
                    if (line.trim().length == 3) out += i to "code fence needs a language (use text for diagrams)"
                    if (i > 1 && prv.isNotBlank()) out += i to "blank line needed before code fence"
                    inFence = true
                } else {
                    inFence = false
                    if (nxt.isNotBlank() && i < lines.size) out += i to "blank line needed after code fence"
                }
                return@forEachIndexed
            }
            if (inFence) return@forEachIndexed
            val h = heading.matchEntire(line)
            if (h != null) {
                headingIssues(h.groupValues[1].length, h.groupValues[2], i, prv, nxt, lines.size, titles, prevLevel, out)
                if (h.groupValues[1].length == 1) titles++
                prevLevel = h.groupValues[1].length
                return@forEachIndexed
            }
            if (Regex("""^\s*[*+] """).containsMatchIn(line)) out += i to "use - for bullets"
            if (line.isBlank()) return@forEachIndexed
            val stripped = line.trim()
            val isTable = stripped.startsWith("|")
            val isList = listItem.containsMatchIn(line)
            val isQuote = stripped.startsWith(">")
            if ((isTable || isList) &&
                prv.isNotBlank() &&
                !Regex("""^\s*(-|\d+\.) |^\s*\|""").containsMatchIn(prv) &&
                !Regex("""^\s+\S""").containsMatchIn(prv)
            ) {
                out += i to "blank line needed before list or table"
            }
            if (!firstContentSeen) {
                firstContentSeen = true
                if (isTable || isList || isQuote) out += i to "the first block after the title must be a summary paragraph"
            }
            if (!isTable) {
                val body = abbreviations.replace(stripInline(line.replace(Regex("""^\s*(-|\d+\.)\s+"""), "")), "X ")
                if (sentenceBreak.containsMatchIn(body)) out += i to "one sentence per line"
                if (line.length > 240) out += i to "line longer than 240 characters; split the sentence or move it to a table"
            }
        }
        if (titles == 0) out += 1 to "missing # title"
        return out
    }

    @Suppress("LongParameterList")
    private fun headingIssues(
        level: Int,
        text: String,
        i: Int,
        prv: String,
        nxt: String,
        total: Int,
        titles: Int,
        prevLevel: Int,
        out: MutableList<Pair<Int, String>>,
    ) {
        if (level == 1) {
            if (titles >= 1) out += i to "more than one # title"
            if (i != 1) out += i to "the # title must be the first line"
        }
        if (prevLevel != 0 && level > prevLevel + 1) out += i to "heading level jumps from $prevLevel to $level"
        if (i > 1 && prv.isNotBlank()) out += i to "blank line needed before heading"
        if (i < total && nxt.isNotBlank()) out += i to "blank line needed after heading"
        val words = text.replace(Regex("`[^`]*`"), "").split(Regex("\\s+")).filter { it.firstOrNull()?.isLetter() == true }
        if (level > 1 && words.size >= 3 && words.drop(1).all { it.first().isUpperCase() } && !words.all { isUpper(it) }) {
            out += i to "use sentence case in headings"
        }
    }

    @Test
    fun `markdown files follow the writing standards`() {
        val bad = markdownFiles().flatMap { f ->
            issues(f.readText()).map { (line, msg) -> "${f.relativeTo(root).path}:$line: $msg" }
        }
        assertTrue(
            "Markdown does not follow docs/WRITING_STANDARDS.md (${bad.size} issues):\n" + bad.take(60).joinToString("\n"),
            bad.isEmpty(),
        )
    }
}
