package io.github.michael3dprint.mybambuddy.domain.model

/**
 * One thing the app can do to or with a printer, and what it depends on. This table is the single source of truth for
 * "which of our tools rely on Developer LAN mode" (docs/design/10-print-state-gate.md): flip one flag and the
 * UI and the repositories follow.
 *
 * [needsIdle] marks a tool that must not run while a print is in progress.
 *
 * [needsDeveloperMode] is true for tools that reach the printer as an MQTT command, which the printer rejects when
 * Developer LAN mode is off. Reads and Bambuddy-local actions do not.
 */
enum class PrinterTool(
    val label: String,
    val needsDeveloperMode: Boolean,
    val needsAms: Boolean = false,
    val needsDrying: Boolean = false,
    val needsIdle: Boolean = false,
) {
    PAUSE_RESUME("Pause and resume", needsDeveloperMode = true),
    CANCEL_PRINT("Cancel print", needsDeveloperMode = true),
    CLEAR_ERRORS("Clear printer errors", needsDeveloperMode = true),
    PRINT_SPEED("Print speed", needsDeveloperMode = true),
    CHAMBER_LIGHT("Chamber light", needsDeveloperMode = true),
    DRYING("Filament drying", needsDeveloperMode = true, needsAms = true, needsDrying = true),
    SLOT_CHANGE("Change AMS filament", needsDeveloperMode = true, needsAms = true),
    START_QUEUED_PRINT("Start a queued print", needsDeveloperMode = true),

    /** Feeds filament in or out of an AMS slot: moves filament and may heat the nozzle, so never while a print is running (design 33). */
    FEED_FILAMENT("Load and unload filament", needsDeveloperMode = true, needsAms = true, needsIdle = true),

    /** Asks Bambuddy for a fresh status push; a read request, so not gated. */
    REFRESH_STATUS("Refresh status", needsDeveloperMode = false),

    /** Only flips a flag inside Bambuddy; nothing is sent to the printer. */
    CLEAR_PLATE("Mark plate cleared", needsDeveloperMode = false),
    ;

    companion object {
        /** The tool a control command uses. */
        fun forCommand(command: PrinterControlCommand): PrinterTool = when (command) {
            PrinterControlCommand.Pause, PrinterControlCommand.Resume -> PAUSE_RESUME
            PrinterControlCommand.Cancel -> CANCEL_PRINT
            PrinterControlCommand.RefreshStatus -> REFRESH_STATUS
            PrinterControlCommand.ClearHms -> CLEAR_ERRORS
            PrinterControlCommand.ClearPlate -> CLEAR_PLATE
            is PrinterControlCommand.SetPrintSpeed -> PRINT_SPEED
            is PrinterControlCommand.SetChamberLight -> CHAMBER_LIGHT
            is PrinterControlCommand.AmsLoadFilament, is PrinterControlCommand.AmsUnloadFilament -> FEED_FILAMENT
        }
    }
}
