package io.github.michael3dprint.mybambuddy.domain.model

/**
 * What one printer can currently do, as reported by Bambuddy's status endpoint. Every flag is tri-state: null means
 * "not reported", which is never read as a yes.
 */
data class PrinterCapabilities(
    val connected: Boolean? = null,
    /** Developer LAN mode. False means the printer rejects MQTT commands from Bambuddy. */
    val developerMode: Boolean? = null,
    val hasAms: Boolean? = null,
    val supportsDrying: Boolean? = null,
    val dryingWhilePrinting: Boolean? = null,
    /** The AMS can dry, but only from the printer's own screen. */
    val dryingScreenOnly: Boolean? = null,
    val hasChamberHeater: Boolean? = null,
    val activity: PrintActivity = PrintActivity.UNKNOWN,
) {
    /** True when at least one thing about the printer was reported; false for a status that could not be read. */
    val isKnown: Boolean get() = connected != null || developerMode != null || activity != PrintActivity.UNKNOWN

    companion object {
        /** Nothing is known. */
        val UNKNOWN = PrinterCapabilities()
    }
}
