package io.github.michael3dprint.mybambuddy.domain.print

import io.github.michael3dprint.mybambuddy.domain.model.RequiredFilament
import kotlin.math.abs

/** A loaded slot the print could use. [tray] is the printer's own tray number; [gramsLeft] is the existing estimate, or null. */
data class SlotCandidate(
    val tray: Int,
    val label: String,
    val material: String,
    val colorHex: String,
    val isEmpty: Boolean,
    val gramsLeft: Int? = null,
    /** The product line the AMS reports, such as "PLA Basic"; blank when it reports none. */
    val subBrand: String = "",
)

/** Proposes which loaded slot should supply each filament a plate needs (design 35, decision 3). */
object FilamentMatcher {
    /**
     * A proposal per required filament: a slot of the same type with the nearest colour, never one slot twice. A filament with no slot
     * of its type is left unmatched (null) so the screen asks for a choice instead of guessing.
     */
    fun propose(required: List<RequiredFilament>, slots: List<SlotCandidate>): Map<Int, SlotCandidate?> {
        val used = mutableSetOf<Int>()
        val result = linkedMapOf<Int, SlotCandidate?>()
        for (need in required.sortedBy { it.slotId }) {
            val pick = slots
                .filter { !it.isEmpty && it.tray !in used && it.material.equals(need.type, ignoreCase = true) }
                .minByOrNull { colorDistance(need.colorHex, it.colorHex) }
            if (pick != null) used += pick.tray
            result[need.slotId] = pick
        }
        return result
    }

    /** Distance between two hex colours (sum of channel differences); a colour that cannot be read is as far as possible. */
    fun colorDistance(a: String, b: String): Int {
        val x = rgb(a)
        val y = rgb(b)
        if (x == null || y == null) return MAX_DISTANCE
        return abs(x[0] - y[0]) + abs(x[1] - y[1]) + abs(x[2] - y[2])
    }

    private fun rgb(hex: String): IntArray? {
        val clean = hex.trim().removePrefix("#")
        if (clean.length < 6) return null
        return runCatching {
            intArrayOf(clean.substring(0, 2).toInt(16), clean.substring(2, 4).toInt(16), clean.substring(4, 6).toInt(16))
        }.getOrNull()
    }

    /** Colours differing by more than this are shown as a colour note on the review. */
    const val CLOSE_ENOUGH = 120
    private const val MAX_DISTANCE = 765
}

/** Builds the list Bambuddy takes for `ams_mapping`: position is the project slot minus one, the value the tray number or -1. */
object AmsMapping {
    /** [chosen] maps a project slot number to its tray number; unused slots, and slots up to the highest one needed, are -1. */
    fun build(chosen: Map<Int, Int>): List<Int> {
        val highest = chosen.keys.maxOrNull() ?: return emptyList()
        return (1..highest).map { chosen[it] ?: -1 }
    }
}
