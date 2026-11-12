package io.github.michael3dprint.mybambuddy.domain.model

/** What the printer is doing, reduced to what a safety check needs. Derived from the raw `gcode_state`, not from [PrinterState]. */
enum class PrintActivity {
    /** IDLE, FINISH or FAILED: Bambuddy treats these as valid targets for new work. */
    IDLE,

    /** PREPARE, SLICING, RUNNING or PAUSE: a print is in progress (a paused print is still in progress). */
    BUSY,
    OFFLINE,

    /** No state, or one we do not recognise. Never treated as safe. */
    UNKNOWN,
    ;

    companion object {
        private val BUSY_STATES = setOf("PREPARE", "SLICING", "RUNNING", "PAUSE", "PAUSED", "PRINTING")
        private val IDLE_STATES = setOf("IDLE", "FINISH", "FAILED")

        /**
         * Classifies a raw printer state. Not connected is OFFLINE; preparing, slicing, running and paused states are BUSY; idle,
         * finished and failed are IDLE; anything else is UNKNOWN.
         */
        fun from(rawState: String?, connected: Boolean?): PrintActivity {
            if (connected == false) return OFFLINE
            val state = rawState?.trim()?.uppercase().orEmpty()
            return when (state) {
                in BUSY_STATES -> BUSY
                in IDLE_STATES -> IDLE
                else -> UNKNOWN
            }
        }
    }
}
