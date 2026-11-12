package io.github.michael3dprint.mybambuddy.domain.policy

/**
 * The printer's own tray number for an AMS slot, which Bambuddy's load and unload routes take (design 33). A regular AMS is its unit
 * number times four plus the slot; an AMS HT holds one filament and is addressed by its unit id (128 to 135). Anything else (for
 * example an AMS Lite or an out-of-range value) gives null, and the screen then offers no load or unload for that slot.
 */
object AmsTrayAddress {
    private const val SLOTS_PER_AMS = 4
    private const val REGULAR_UNITS = 4
    private val HT_UNITS = 128..135

    /** The tray number for slot [slot] (0 to 3) of unit [amsId], or null when it cannot be addressed. */
    fun forFeed(amsId: Int, isHt: Boolean, slot: Int): Int? = when {
        isHt || amsId in HT_UNITS -> amsId.takeIf { it in HT_UNITS }
        amsId in 0 until REGULAR_UNITS && slot in 0 until SLOTS_PER_AMS -> amsId * SLOTS_PER_AMS + slot
        else -> null
    }
}
