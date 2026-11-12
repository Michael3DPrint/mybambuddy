package io.github.michael3dprint.mybambuddy.domain.policy

/** A reason the user should look twice before loading a spool into a slot. Rendered by the UI, decided here. */
sealed interface SlotWarning {
    /** Another spool is recorded in the slot and will be unassigned. */
    data class ReplacesSpool(val otherSpoolId: Int) : SlotWarning

    /** The printer reports a filament in the slot that this app has no record of (e.g. an RFID spool); its settings may be overwritten. */
    data class OverwritesDetected(val current: String) : SlotWarning

    /** The slot reports a different material from the spool being loaded. */
    data class MaterialMismatch(val slotMaterial: String, val spoolMaterial: String) : SlotWarning

    /** The spool is recorded in another slot and will leave it. */
    data class MovesFromOtherSlot(val where: String) : SlotWarning

    /** The AMS unit is drying right now. */
    data object UnitDrying : SlotWarning

    /** The printer shows nothing in the slot; Bambuddy will configure it when a spool is inserted. */
    data object SlotLooksEmpty : SlotWarning

    /** The change may be refused (for example the printer does not report Developer LAN mode). */
    data class MayBeRefused(val reason: String) : SlotWarning
}
