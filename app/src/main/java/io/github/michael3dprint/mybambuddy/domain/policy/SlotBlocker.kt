package io.github.michael3dprint.mybambuddy.domain.policy

/** A reason the change cannot be processed at all. Any blocker stops the change before anything is sent. */
sealed interface SlotBlocker {
    /** Developer LAN mode off, printer offline or no AMS, as decided by [ToolAvailability]. */
    data class ToolUnavailable(val reason: String) : SlotBlocker

    /** A print is in progress (including paused). */
    data object PrinterBusy : SlotBlocker

    /** The printer's state is missing or unrecognised, so it cannot be shown to be safe. */
    data object CannotTell : SlotBlocker

    /** The slot holds an RFID-identified Bambu spool; the printer is the authority for its settings. */
    data class RfidSpool(val slotNumber: Int, val label: String) : SlotBlocker
}
