package io.github.michael3dprint.mybambuddy.domain.model

/** The slicing settings the check needs. [addressSet] says whether the chosen slicer has an address saved; the address itself is never kept. */
data class SlicerSettings(val useSlicerApi: Boolean, val preferredSlicer: String, val addressSet: Boolean)

/** What the presets look like to the check. */
data class PresetSummary(
    /** How many printer presets the slicer app itself reported (the standard tier); zero means Bambuddy got nothing from it. */
    val standardPrinterCount: Int,
    /** Every printer preset name from every tier. */
    val printerPresetNames: List<String>,
    /** How many process presets exist across the tiers. */
    val processCount: Int,
    /** How many filament presets exist across the tiers. */
    val filamentCount: Int,
    /** True when Bambu Cloud presets are available (signed in). */
    val cloudSignedIn: Boolean,
)

/** A printer Bambuddy manages, as the check sees it. */
data class PrinterRef(val name: String, val model: String)

/** Everything the check works from. */
data class SlicingInputs(val settings: SlicerSettings, val presets: PresetSummary, val printers: List<PrinterRef>)

/** How much a failed check matters. */
enum class CheckLevel {
    /** Slicing cannot work until it is fixed. */
    BLOCKER,

    /** Slicing may work, but not for everything. */
    WARNING,

    /** Good to know; nothing to fix. */
    INFO,
}

/** One line of the check: what was tested, whether it passed, and, when it did not, the steps to fix it. */
data class SlicingCheck(
    /** A stable name for the check, for example "reach" or "printer-preset:X2D". */
    val id: String,
    /** The one-line statement shown for the check. */
    val title: String,
    /** True when the check passed. */
    val passed: Boolean,
    /** How much a failure matters. */
    val level: CheckLevel,
    /** Why it passed or failed, in a sentence. */
    val detail: String,
    /** What to do, in order, when it failed; empty when it passed. */
    val fixSteps: List<String> = emptyList(),
)

/** The result of the slicing check. */
data class SlicingReadiness(val checks: List<SlicingCheck>) {
    /** Ready means no failed blocker; warnings and information do not stop slicing. */
    val ready: Boolean get() = checks.none { !it.passed && it.level == CheckLevel.BLOCKER }

    /** The checks as they apply to one printer: the shared checks plus only that printer's own preset check. */
    fun forPrinter(printerName: String): SlicingReadiness =
        SlicingReadiness(checks.filter { !it.id.startsWith(PRINTER_PRESET_PREFIX) || it.id == PRINTER_PRESET_PREFIX + printerName })

    /** The first failed check, or null when everything passed. */
    val firstProblem: SlicingCheck? get() = checks.firstOrNull { !it.passed && it.level != CheckLevel.INFO }
}

/** The start of the id of a per-printer preset check; the printer's name follows it. */
const val PRINTER_PRESET_PREFIX = "printer-preset:"
