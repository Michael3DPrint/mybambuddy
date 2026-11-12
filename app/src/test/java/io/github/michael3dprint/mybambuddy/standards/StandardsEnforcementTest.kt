package io.github.michael3dprint.mybambuddy.standards

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turns the written engineering standards into checks that fail the build (`docs/ENGINEERING_STANDARDS.md`).
 * Each check names the offending files, so the failure says what to fix. Exceptions live in `docs/standards-allowlist.txt` with a reason,
 * and an exception that is no longer needed fails too. Gradle runs unit tests from the `app/` directory, so the repository root is one up.
 */
class StandardsEnforcementTest {
    private val root = File("..").canonicalFile
    private val mainDir = File(root, "app/src/main/java")
    private val testDir = File(root, "app/src/test/java")

    private fun kotlinFiles(dir: File) = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun rel(f: File) = f.relativeTo(root).path

    private val mainFiles by lazy { kotlinFiles(mainDir) }

    private val allowlist by lazy {
        File(root, "docs/standards-allowlist.txt").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line ->
                val parts = line.split("|", limit = 2)
                assertTrue("Allowlist line needs a reason after '|': $line", parts.size == 2 && parts[1].isNotBlank())
                parts[0].trim() to parts[1].trim()
            }
    }

    /** Source with string literals emptied and comments removed, keeping every newline so line numbers still match. */
    private fun code(src: String): String {
        fun blank(m: MatchResult) = "\n".repeat(m.value.count { it == '\n' })
        var s = Regex("\"\"\"[\\s\\S]*?\"\"\"").replace(src) { "\"\"" + blank(it) }
        s = Regex("\"(?:[^\"\\\\\\n]|\\\\.)*\"").replace(s, "\"\"")
        s = Regex("'(?:[^'\\\\\\n]|\\\\.)'").replace(s, "''")
        s = Regex("/\\*[\\s\\S]*?\\*/").replace(s) { blank(it) }
        return Regex("//[^\\n]*").replace(s, "")
    }

    /** Like [code] but string literals are kept, because `${'$'}{name}` in a template is a use. Comments are still removed. */
    private fun codeKeepingStrings(src: String): String {
        val strings = Regex("\"(?:[^\"\\\\\n]|\\\\.)*\"")
        val guarded = strings.replace(src) { it.value.replace("//", "/\u0000/") }
        val noBlock = Regex("/\\*[\\s\\S]*?\\*/").replace(guarded, "")
        return Regex("//[^\n]*").replace(noBlock, "").replace("\u0000", "")
    }

    // ---------------------------------------------------------------- no `!!`

    @Test
    fun `no double bang in app code`() {
        val bad = mainFiles.filter { Regex("!!").containsMatchIn(code(it.readText())) }.map(::rel)
        assertTrue("`!!` is not allowed outside tests (use ?:, ?. or a safe call): $bad", bad.isEmpty())
    }

    // ---------------------------------------------------------------- file length

    @Test
    fun `no source file is over 400 lines`() {
        val bad = mainFiles.map {
            it to it.readLines().size
        }.filter { it.second > MAX_FILE_LINES }.map { "${rel(it.first)} (${it.second})" }
        assertTrue("Source files over $MAX_FILE_LINES lines: split them (docs/design/13): $bad", bad.isEmpty())
    }

    // ---------------------------------------------------------------- ViewModel size and tests

    @Test
    fun `no view model has more than 8 public functions`() {
        val bad = mainFiles.filter { it.name.endsWith("ViewModel.kt") }.map { f ->
            f to Regex("^ {4}(?:suspend )?fun ", RegexOption.MULTILINE).findAll(code(f.readText())).count()
        }.filter { it.second > MAX_VIEW_MODEL_FUNCTIONS }.map { "${rel(it.first)} (${it.second})" }
        assertTrue("View models over $MAX_VIEW_MODEL_FUNCTIONS public functions: split or remove wrappers: $bad", bad.isEmpty())
    }

    @Test
    fun `every view model has a test class`() {
        val tests = kotlinFiles(testDir).map { it.name }.toSet()
        val bad = mainFiles.filter {
            it.name.endsWith("ViewModel.kt")
        }.filter { it.name.removeSuffix(".kt") + "Test.kt" !in tests }.map(::rel)
        assertTrue("View models without a *ViewModelTest.kt: $bad", bad.isEmpty())
    }

    // ---------------------------------------------------------------- KDoc

    private data class Missing(val where: String, val name: String)

    private val declType = Regex(
        "^\\s*(?:(?:public|internal|private|protected|data|sealed|enum|abstract|open|annotation|fun|companion|value)\\s+)*(class|interface|object)\\b",
    )
    private val declTopLevel = Regex(
        "^(?:(?:public|internal|data|sealed|enum|abstract|open|annotation|fun|suspend|inline|operator|value)\\s+)*" +
            "(class|interface|object|typealias|fun)\\s+(?:<[^>]*>\\s*)?([A-Za-z_][\\w.]*)",
    )
    private val declMember = Regex(
        "^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:(?:public|internal|protected|open|abstract|suspend|inline|operator|const|lateinit)\\s+)*" +
            "(fun|val|var)\\s+(?:<[^>]*>\\s*)?([A-Za-z_][\\w.]*)",
    )

    /** True if a KDoc of real content sits directly above line [index], above any annotations. */
    private fun hasKdoc(lines: List<String>, index: Int): Boolean {
        var j = index - 1
        while (j >= 0 && lines[j].trim().startsWith("@")) j--
        if (j < 0 || !lines[j].trim().endsWith("*/")) return false
        var k = j
        while (k >= 0 && !lines[k].contains("/**")) k--
        if (k < 0) return false
        val text = lines.subList(k, j + 1).joinToString(" ")
            .replace("/**", " ").replace("*/", " ").replace(Regex("(?m)^\\s*\\*"), " ").replace(Regex("\\s+"), " ").trim()
        return text.length >= MIN_KDOC_CHARS
    }

    private fun missingKdoc(file: File, membersToo: Boolean): List<Missing> {
        val lines = file.readLines()
        val codeLines = code(file.readText()).split("\n")
        val out = mutableListOf<Missing>()
        data class Open(val kind: String, val depth: Int, val priv: Boolean)
        val stack = ArrayList<Open>()
        var depth = 0
        for (i in lines.indices) {
            val c = codeLines.getOrElse(i) { "" }
            val priv = c.trimStart().startsWith("private ")
            var kind: String? = null
            if (declType.containsMatchIn(c) && '{' in c) {
                kind = "type"
            } else if (Regex("^\\s*(?:private\\s+)?companion object").containsMatchIn(c) && '{' in c) {
                kind = "type"
            } else if (Regex("\\bfun\\b").containsMatchIn(c) && '{' in c && !c.trim().startsWith("//")) {
                kind = "fun"
            }
            if (c.isNotEmpty() && !c[0].isWhitespace() && c[0] != '}' && c[0] != '@' && !c.startsWith("private ")) {
                val m = declTopLevel.find(c)
                if (m != null && !hasKdoc(lines, i)) out += Missing("${rel(file)}:${i + 1}", m.groupValues[2])
            }
            if (membersToo &&
                stack.isNotEmpty() &&
                stack.last().kind == "type" &&
                depth == stack.last().depth + 1 &&
                stack.none { it.priv }
            ) {
                val m = declMember.find(lines[i])
                if (m != null &&
                    "override" !in lines[i] &&
                    !Regex("^\\s*(?:@\\w+\\s+)*private ").containsMatchIn(lines[i]) &&
                    !lines[i].trimStart().startsWith("@Json") &&
                    !hasKdoc(lines, i)
                ) {
                    out += Missing("${rel(file)}:${i + 1}", m.groupValues[2])
                }
            }
            val opens = c.count { it == '{' }
            val closes = c.count { it == '}' }
            if (kind != null && opens > closes) stack.add(Open(kind, depth, priv))
            depth += opens - closes
            while (stack.isNotEmpty() && depth <= stack.last().depth) stack.removeAt(stack.size - 1)
        }
        return out
    }

    @Test
    fun `public declarations have KDoc`() {
        val bad = mainFiles.flatMap { f ->
            val path = rel(f)
            when {
                "/domain/" in path || "/data/" in path -> missingKdoc(f, membersToo = true)
                "/ui/" in path -> missingKdoc(f, membersToo = false)
                else -> emptyList()
            }
        }
        assertTrue(
            "Public declarations without KDoc (what it does and any contract, at least $MIN_KDOC_CHARS characters), " +
                "${bad.size} in all: " + bad.take(25).joinToString { "${it.where} ${it.name}" },
            bad.isEmpty(),
        )
    }

    // ---------------------------------------------------------------- dead production code

    private val declName = Regex(
        "^([ \\t]*)(?:@\\w+(?:\\([^)]*\\))?[ \\t]+)*" +
            "(?:(?:public|internal|private|protected|data|sealed|enum|abstract|open|override|suspend|inline|operator|" +
            "const|lateinit|annotation)[ \\t]+)*(class|interface|object|typealias|fun|val|var)[ \\t]+(?:<[^>]*>[ \\t]*)?" +
            "(?:[A-Za-z_][\\w<>?,. ]*\\.)?([A-Za-z_]\\w*)",
        RegexOption.MULTILINE,
    )
    private val neverDead = setOf(
        "onCreate", "main", "invoke", "get", "set", "equals", "hashCode", "toString", "copy",
        "doWork", "intercept", "fromJson", "toJson", "Companion",
    )

    /** Names declared in domain/ or data/ that nothing else in app code uses (a use in a test does not count). */
    private fun deadNames(): Set<String> {
        val all = mainFiles.associateWith { codeKeepingStrings(it.readText()) }
        val text = all.values.joinToString("\n")
        val dead = mutableSetOf<String>()
        for ((f, c) in all) {
            val path = rel(f)
            if ("/domain/" !in path && "/data/" !in path) continue
            for (m in declName.findAll(c)) {
                val indent = m.groupValues[1].length
                val name = m.groupValues[3]
                val line = c.substring(
                    c.lastIndexOf('\n', m.range.first) + 1,
                    c.indexOf('\n', m.range.last).let {
                        if (it <
                            0
                        ) {
                            c.length
                        } else {
                            it
                        }
                    },
                )
                if (indent > 4 || name in neverDead || "@Json" in line || "Preview" in name) continue
                val total = Regex("\\b" + Regex.escape(name) + "\\b").findAll(text).count()
                val declared = Regex(
                    "\\b(?:class|interface|object|typealias|fun|val|var)\\s+(?:<[^>]*>\\s*)?(?:[A-Za-z_][\\w<>?,. ]*\\.)?" +
                        Regex.escape(name) +
                        "\\b",
                ).findAll(text).count()
                if (total - declared <= 0) dead += name
            }
        }
        return dead
    }

    @Test
    fun `no dead production code`() {
        val dead = deadNames()
        val allowed = allowlist.keys.filter { it.startsWith("dead:") }.map { it.removePrefix("dead:") }.toSet()
        val bad = (dead - allowed).sorted()
        assertTrue(
            "Declared in domain/ or data/ but used by nothing in app code (remove it, or add `dead:Name | reason` to the allowlist): $bad",
            bad.isEmpty(),
        )
        val stale = (allowed - dead).sorted()
        assertTrue("Allowlisted as dead but now used or gone; remove the entry: $stale", stale.isEmpty())
    }

    // ---------------------------------------------------------------- UI patterns (design 48)

    private val uiFiles by lazy { mainFiles.filter { "/ui/" in rel(it) } }

    @Test
    fun `tab rows are built only by the shared AppTabRow`() {
        val tabRow = Regex("""\b(?:Primary|Secondary)?(?:Scrollable)?TabRow\(|(?<![\w.])Tab\(""")
        val bad = uiFiles.filter { it.name != "AppTabRow.kt" && tabRow.containsMatchIn(code(it.readText())) }.map(::rel)
        assertTrue("Build tab rows with AppTabRow (design 48, pattern P2), not your own copy: $bad", bad.isEmpty())
    }

    @Test
    fun `an error shown to the user always has the Help link`() {
        // Any screen that reads an error's title or message must show it through ErrorNotice or add a HelpButton itself.
        val shows = Regex("""\b(?:errState|errorState|testErrorState\??|error)\.(?:title|message)\b""")
        val bad = uiFiles.filter { f ->
            val c = code(f.readText())
            f.name != "ErrorNotice.kt" &&
                !f.name.endsWith("ViewModel.kt") &&
                shows.containsMatchIn(c) &&
                !c.contains("ErrorNotice(") &&
                !c.contains("HelpButton(")
        }.map(::rel)
        assertTrue(
            "These screens show an error without the Help link (use ErrorNotice or HelpButton, design 48, pattern P7): $bad",
            bad.isEmpty(),
        )
    }

    // ---------------------------------------------------------------- documents

    private val liveDocs by lazy {
        File(root, "docs/design").listFiles { f -> f.extension == "md" }.orEmpty().toList() +
            File(root, "docs/guides").listFiles { f -> f.extension == "md" }.orEmpty().toList() +
            listOf("docs/OPEN_ITEMS.md", "docs/PROJECT_OVERVIEW.md").map { File(root, it) }
    }

    @Test
    fun `no real server address is committed`() {
        // Tailscale-range addresses (not CIDR notation) and full host.tailnet.ts.net names; example addresses live in files listed in .pii-guard-allow.
        val address = Regex("""\b100\.(?:6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.\d{1,3}\.\d{1,3}\b(?!/)|\b[a-z0-9-]+\.[a-z0-9-]+\.ts\.net\b""")
        val allowed = File(root, ".pii-guard-allow").readLines()
            .map { it.trim() }.filter { it.startsWith("path:") }
            .map { Regex(it.removePrefix("path:").trim().split("*").joinToString(".*") { part -> Regex.escape(part) }) }
        val skip = setOf(".git", "build", ".gradle", ".idea", "node_modules")
        val bad = root.walkTopDown().onEnter { it.name !in skip }.filter { f ->
            f.isFile &&
                f.length() < 2_000_000 &&
                allowed.none { it.matches(rel(f)) } &&
                f.extension in setOf("kt", "kts", "md", "xml", "txt", "json", "toml", "properties") &&
                address.containsMatchIn(f.readText())
        }.map(::rel).toList()
        assertTrue("A real server address must not be committed (keep it in ~/.config/mybambuddy): $bad", bad.isEmpty())
    }

    @Test
    fun `live documents do not cite line numbers`() {
        val patterns = listOf(
            Regex("\\.(?:kt|kts|xml)`?[: ]+(?:lines? )?\\d{2,4}\\b"),
            Regex("\\(\\s*(?:now )?lines? \\d"),
            Regex("(?<!max )\\blines? \\d{2,4}\\b"),
        )
        val bad = liveDocs.filter { f -> patterns.any { it.containsMatchIn(f.readText()) } }.map(::rel)
        assertTrue("Name a class or function, not a line number (they go stale): $bad", bad.isEmpty())
    }

    @Test
    fun `live documents do not state test counts`() {
        val count = Regex("(?<![-\\w])\\d{2,4}\\s+(?:unit\\s+)?tests\\b")
        val bad = liveDocs.filter { count.containsMatchIn(it.readText()) }.map(::rel)
        assertTrue("A test count goes stale at once; say what is covered instead: $bad", bad.isEmpty())
    }

    @Test
    fun `every finished item has a ledger row`() {
        val ledger = File(root, "docs/TASK_LEDGER.md").readText()
        val done = Regex("(?m)^## (OI-\\d+) .*`\\[x\\]`").findAll(File(root, "docs/OPEN_ITEMS.md").readText()).map {
            it.groupValues[1]
        }.toList()
        val bad = done.filter { !Regex("\\b" + it + "\\b").containsMatchIn(ledger) }
        assertTrue("Items marked done in docs/OPEN_ITEMS.md with no row in docs/TASK_LEDGER.md: $bad", bad.isEmpty())
    }

    @Test
    fun `every design doc and guide is in the document map`() {
        val overview = File(root, "docs/PROJECT_OVERVIEW.md").readText()
        val files = File(root, "docs/design").listFiles { f -> f.extension == "md" }.orEmpty().toList() +
            File(root, "docs/guides").listFiles { f -> f.extension == "md" }.orEmpty().toList()
        val bad = files.map { it.name }.filter { it !in overview }
        assertTrue("Documents missing from the document map in docs/PROJECT_OVERVIEW.md: $bad", bad.isEmpty())
    }

    private companion object {
        const val MAX_FILE_LINES = 400
        const val MAX_VIEW_MODEL_FUNCTIONS = 8
        const val MIN_KDOC_CHARS = 12
    }
}
