package io.github.michael3dprint.mybambuddy.domain.model

/** The result of one connection check. UNKNOWN is a status this app does not recognise, so a newer server cannot break the screen. */
enum class DiagnosticStatus { PASS, WARN, FAIL, SKIP, UNKNOWN }

/** Bambuddy's overall verdict on a diagnostics run. */
enum class DiagnosticOverall { OK, WARNINGS, PROBLEMS, UNKNOWN }

/** One check from a diagnostics run, by Bambuddy's stable check id (for example `developer_mode`). */
data class DiagnosticCheck(val id: String, val status: DiagnosticStatus)

/**
 * What Bambuddy found when it tested its connection to one printer. The printer's address is in the server's reply but is deliberately
 * not kept here.
 */
data class DiagnosticReport(val overall: DiagnosticOverall, val checks: List<DiagnosticCheck>) {
    /** How many checks failed. */
    val problems: Int get() = checks.count { it.status == DiagnosticStatus.FAIL }

    /** How many checks gave a warning. */
    val warnings: Int get() = checks.count { it.status == DiagnosticStatus.WARN }
}
