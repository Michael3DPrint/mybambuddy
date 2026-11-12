package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.CheckLevel
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticReport
import io.github.michael3dprint.mybambuddy.domain.model.PrinterReadiness
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.model.SlicingReadiness
import io.github.michael3dprint.mybambuddy.domain.model.ToolingMode

/** How a Tools section is doing. Each level has its own icon shape as well as a colour, so it never relies on colour alone. */
enum class StatusLevel {
    /** Everything is good (a check mark). */
    OK,

    /** Something needs a look (an exclamation mark). */
    WARN,

    /** Something is wrong (a cross). */
    ERROR,

    /** Nothing to judge yet, or not known (a dash). */
    NEUTRAL,
}

/** A section's one-line state: its [level] and the [text] shown beside the icon and read aloud. */
data class SectionStatus(val level: StatusLevel, val text: String)

/** The words for the Developer LAN mode setting, in one place so the printer card and the Tools tab always agree (design 37). */
object RemoteControlWords {
    /** What the setting is called: whether this app may send commands to the printer. */
    const val NAME = "Remote control"

    /** The value for [mode]: Full, Limited, Not confirmed, Offline or Couldn't check. */
    fun value(mode: ToolingMode): String = when (mode) {
        ToolingMode.FULL -> "Full"
        ToolingMode.LIMITED -> "Limited"
        ToolingMode.UNVERIFIED -> "Not confirmed"
        ToolingMode.OFFLINE -> "Offline"
        ToolingMode.UNKNOWN -> "Couldn't check"
    }

    /** The line on a printer card, for example "Remote control: Full". */
    fun line(report: PrinterReadiness): String = "$NAME: ${value(report.mode)}"

    /** The section status for [mode]: Full is good, Limited and Not confirmed need a look, Offline and unknown are neutral. */
    fun status(mode: ToolingMode): SectionStatus = SectionStatus(
        when (mode) {
            ToolingMode.FULL -> StatusLevel.OK
            ToolingMode.LIMITED, ToolingMode.UNVERIFIED -> StatusLevel.WARN
            ToolingMode.OFFLINE, ToolingMode.UNKNOWN -> StatusLevel.NEUTRAL
        },
        value(mode),
    )

    /** A sentence saying why the printer is in [mode] and what it means. */
    fun explain(mode: ToolingMode): String = when (mode) {
        ToolingMode.FULL -> "Developer LAN mode is on, so this app can send commands to the printer."
        ToolingMode.LIMITED ->
            "Developer LAN mode is off on the printer, so it rejects commands from this app. Monitoring, camera, history and inventory " +
                "still work. ${ToolAvailability.DEVELOPER_MODE_FIX}"
        ToolingMode.UNVERIFIED -> "The printer doesn't report Developer LAN mode. Controls stay on, but the printer may refuse them."
        ToolingMode.OFFLINE -> "The printer is offline, so controls are off. Monitoring shows the last saved data."
        ToolingMode.UNKNOWN ->
            "The printer's state couldn't be read yet. Nothing is assumed; a control will explain itself if the printer refuses it."
    }
}

/** The status of each Tools section from the data behind it (design 37). Pure, so every state is tested; unknown is never shown as good. */
object SectionStatuses {
    /** Printer Status: follows the worst HMS level present (error, warning or notice), else the printer's state; neutral when offline. */
    fun printerStatus(errors: List<io.github.michael3dprint.mybambuddy.domain.model.HmsStatus>, state: PrinterState): SectionStatus = when {
        HmsSummary.worst(errors) == HmsSeverity.ERROR -> SectionStatus(StatusLevel.ERROR, HmsSummary.words(errors))
        HmsSummary.worst(errors) == HmsSeverity.WARNING -> SectionStatus(StatusLevel.WARN, HmsSummary.words(errors))
        HmsSummary.worst(errors) == HmsSeverity.NOTICE -> SectionStatus(StatusLevel.NEUTRAL, HmsSummary.words(errors))
        state == PrinterState.ERROR -> SectionStatus(StatusLevel.ERROR, "Printer error")
        state == PrinterState.FAILED -> SectionStatus(StatusLevel.WARN, "Last print failed")
        state == PrinterState.OFFLINE || state == PrinterState.UNKNOWN -> SectionStatus(StatusLevel.NEUTRAL, "Not reported")
        else -> SectionStatus(StatusLevel.OK, "No errors")
    }

    /** Connection diagnostics: from whether a run is under way, failed to run, or finished with [report]. */
    fun diagnostics(running: Boolean, couldNotRun: Boolean, report: DiagnosticReport?): SectionStatus = when {
        running -> SectionStatus(StatusLevel.NEUTRAL, "Running…")
        couldNotRun -> SectionStatus(StatusLevel.ERROR, "Couldn't run")
        report == null -> SectionStatus(StatusLevel.NEUTRAL, "Not run")
        report.problems > 0 -> SectionStatus(StatusLevel.ERROR, "${report.problems} failed")
        report.warnings > 0 -> SectionStatus(StatusLevel.WARN, if (report.warnings == 1) "1 warning" else "${report.warnings} warnings")
        report.checks.isEmpty() -> SectionStatus(StatusLevel.NEUTRAL, "No result")
        else -> SectionStatus(StatusLevel.OK, "All passed")
    }

    /** Slicing: not ready is an error, ready with a failed warning needs a look, ready is good; null [readiness] is still checking. */
    fun slicing(readiness: SlicingReadiness?, checkFailed: Boolean): SectionStatus = when {
        checkFailed -> SectionStatus(StatusLevel.NEUTRAL, "Couldn't check")
        readiness == null -> SectionStatus(StatusLevel.NEUTRAL, "Checking…")
        !readiness.ready -> SectionStatus(StatusLevel.ERROR, "Not ready")
        readiness.checks.any { !it.passed && it.level == CheckLevel.WARNING } -> SectionStatus(StatusLevel.WARN, "Ready, with notes")
        else -> SectionStatus(StatusLevel.OK, "Ready")
    }

    /**
     * The Diagnostics group (design 45): any red part makes it red and says how many need attention; else any amber part makes it amber; else
     * any part not checked yet makes it neutral; else good. A group with no parts is neutral.
     */
    fun group(parts: List<SectionStatus>): SectionStatus {
        val errors = parts.count { it.level == StatusLevel.ERROR }
        val warns = parts.count { it.level == StatusLevel.WARN }
        val unchecked = parts.count { it.level == StatusLevel.NEUTRAL }
        return when {
            parts.isEmpty() -> SectionStatus(StatusLevel.NEUTRAL, "Nothing to show")
            errors > 0 -> SectionStatus(StatusLevel.ERROR, "$errors need attention")
            warns > 0 -> SectionStatus(StatusLevel.WARN, "$warns to look at")
            unchecked > 0 -> SectionStatus(StatusLevel.NEUTRAL, "Not all checked")
            else -> SectionStatus(StatusLevel.OK, "All good")
        }
    }

    /** One AMS unit (design 45): a drying error is red, drying shows the time left, otherwise its humidity; neutral when it reports none. */
    fun ams(unit: io.github.michael3dprint.mybambuddy.domain.model.AmsUnit): SectionStatus = when (unit.dryPhase) {
        io.github.michael3dprint.mybambuddy.domain.model.DryPhase.ERROR -> SectionStatus(StatusLevel.ERROR, "Drying error")
        io.github.michael3dprint.mybambuddy.domain.model.DryPhase.DRYING ->
            SectionStatus(
                StatusLevel.OK,
                "Drying, ${unit.dryMinutesLeft / MINUTES_PER_HOUR} h ${unit.dryMinutesLeft % MINUTES_PER_HOUR} m left",
            )
        io.github.michael3dprint.mybambuddy.domain.model.DryPhase.CHECKING -> SectionStatus(StatusLevel.OK, "Checking before drying")
        io.github.michael3dprint.mybambuddy.domain.model.DryPhase.COOLING -> SectionStatus(StatusLevel.OK, "Cooling down")
        io.github.michael3dprint.mybambuddy.domain.model.DryPhase.STOPPING -> SectionStatus(StatusLevel.NEUTRAL, "Stopping")
        io.github.michael3dprint.mybambuddy.domain.model.DryPhase.OFF ->
            unit.humidityLevel?.let { SectionStatus(StatusLevel.OK, "Humidity $it %") }
                ?: SectionStatus(StatusLevel.NEUTRAL, "Humidity not reported")
    }

    private const val MINUTES_PER_HOUR = 60

    /** Maintenance: good when nothing is due or coming due, otherwise how many are due and how many soon. */
    fun maintenance(due: Int, soon: Int): SectionStatus = when {
        due > 0 -> SectionStatus(StatusLevel.WARN, "$due due" + if (soon > 0) ", $soon soon" else "")
        soon > 0 -> SectionStatus(StatusLevel.WARN, "$soon soon")
        else -> SectionStatus(StatusLevel.OK, "Nothing due")
    }
}
