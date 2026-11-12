package io.github.michael3dprint.mybambuddy.domain.model

/** One slot of an AMS unit as the printer reports it: what it holds, how much is left, and which spool is recorded there. */
data class AmsTray(
    val trayId: Int,
    val material: String,
    val subBrand: String,
    val colorHex: String,
    /** 0-100, or null when the printer doesn't know (e.g. non-RFID spool). */
    val remainPercent: Int?,
    val loadedSpoolId: Int?,
    /** The AMS read an RFID tag on this spool (a Bambu spool). The printer then takes the slot settings from the tag. */
    val hasRfidTag: Boolean = false,
    /** The 32-character spool id a Bambu tag carries, upper-case hex; empty when the slot has none (design 33). */
    val trayUuid: String = "",
) {
    val isEmpty: Boolean get() = material.isBlank() && colorHex.isBlank()
    val label: String get() = subBrand.ifBlank { material }.ifBlank { "Empty" }
}

/** The drying state of an AMS unit as Bambuddy reports it; an unknown code is OFF. */
enum class DryPhase { OFF, CHECKING, DRYING, COOLING, STOPPING, ERROR }

/** One AMS unit with its humidity, drying state, slots and the [capabilities] of the printer it belongs to. */
data class AmsUnit(
    val printerId: Int,
    val printerName: String,
    val amsId: Int,
    val isHt: Boolean,
    val humidityLevel: Int?,
    val tempC: Double?,
    val supportsDrying: Boolean,
    val dryPhase: DryPhase,
    val dryMinutesLeft: Int,
    val dryTargetTemp: Int?,
    val trays: List<AmsTray>,
    /** What the printer behind this unit can do right now; see [PrinterCapabilities]. */
    val capabilities: PrinterCapabilities = PrinterCapabilities.UNKNOWN,
) {
    val name: String get() = if (isHt) "AMS HT ${amsId + 1}" else "AMS ${amsId + 1}"
    val isDrying: Boolean get() = dryPhase == DryPhase.DRYING || dryPhase == DryPhase.CHECKING || dryPhase == DryPhase.COOLING
}
