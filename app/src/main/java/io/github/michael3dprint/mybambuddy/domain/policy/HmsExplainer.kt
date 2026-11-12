package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.HmsStatus

/** How serious an HMS error is. */
enum class HmsSeverity { ERROR, WARNING, NOTICE }

/** A printer error made readable. */
data class HmsAdvice(
    val severity: HmsSeverity,
    val area: String,
    /** The server's own description, or a generic title when it has none. */
    val title: String,
    /** What the printer offers to do, in plain words (never raw keys). */
    val options: List<String>,
    /** Official explanation page, or null when the code has an unexpected shape. */
    val learnMoreUrl: String?,
    val codeLabel: String,
)

/**
 * Pure explanation of an HMS error: severity, affected area, the printer's own options in plain words and a link.
 * No network and no big dictionary: the description text comes from the server; this adds structure.
 * The link is built here from a validated code on a fixed host, never copied from the server.
 */
object HmsExplainer {
    private val FULL_CODE = Regex("^[0-9A-Fa-f]{16}$")

    private val AREAS = mapOf(
        0x03 to "Main board",
        0x05 to "Motion controller",
        0x07 to "AMS",
        0x08 to "Toolhead",
        0x0B to "Camera / AI monitoring",
    )

    private val OPTIONS = mapOf(
        "RESUME_PRINTING" to "Resume the print",
        "RESUME_PRINTING_DEFECTS" to "Resume the print, accepting possible defects",
        "RESUME_PRINTING_PROBELM_SOLVED" to "Resume once the problem is fixed",
        "PROBLEM_SOLVED_RESUME" to "Resume once the problem is fixed",
        "IGNORE_RESUME" to "Ignore this and resume",
        "STOP_PRINTING" to "Stop the print",
        "ABORT" to "Abort",
        "CHECK_ASSISTANT" to "Open the printer's step-by-step help",
        "FILAMENT_EXTRUDED" to "Confirm the filament came out of the nozzle",
        "RETRY_FILAMENT_EXTRUDED" to "Retry feeding the filament",
        "FILAMENT_LOAD_RESUME" to "Load the filament, then resume",
        "LOAD_VIRTUAL_TRAY" to "Load filament on the external spool holder",
        "REFRESH_NOZZLE" to "Refresh the nozzle information",
        "STOP_DRYING" to "Stop drying",
        "TURN_OFF_FIRE_ALARM" to "Turn off the fire alarm",
        "RETRY_PROBLEM_SOLVED" to "Retry after fixing the problem",
        "CONTINUE" to "Continue",
        "PROCEED" to "Proceed",
        "OK_BUTTON" to "Acknowledge",
    )

    /** The level of one HMS error: Bambuddy's levels are 1 error (task stopped), 2 warning (task paused) and 3 notification. */
    fun severityOf(hms: HmsStatus): HmsSeverity = when (hms.severity) {
        "1" -> HmsSeverity.ERROR
        "3" -> HmsSeverity.NOTICE
        else -> HmsSeverity.WARNING
    }

    /** Turns an HMS error into readable advice: severity, area, title, the options the printer offers and a wiki link. */
    fun explain(hms: HmsStatus): HmsAdvice {
        val severity = severityOf(hms)
        val full = hms.fullCode.takeIf { FULL_CODE.matches(it) }
        return HmsAdvice(
            severity = severity,
            area = hms.module?.let { AREAS[it] } ?: "Printer",
            title = hms.description.ifBlank { "The printer reported a problem" },
            options = hms.actions.mapNotNull { OPTIONS[it] }.distinct(),
            learnMoreUrl = full?.let { wikiUrl(it) },
            codeLabel = full?.let { it.chunked(4).joinToString("-") } ?: hms.code.ifBlank { "unknown code" },
        )
    }

    /** Only ever returns a URL built from a validated 16-digit hex code on the official wiki host. */
    fun wikiUrl(fullCode: String): String? =
        if (FULL_CODE.matches(fullCode)) "https://wiki.bambulab.com/en/hms/error-code?e=${fullCode.uppercase()}" else null
}
