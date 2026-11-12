package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.Spool

/**
 * Compares a slot's reported material and colour with the spool assigned to it. Colour is only compared when both sides have
 * one, and only the RGB part (the alpha byte is ignored). It cannot see the filament preset id, so a wrong preset with the same
 * material and colour reads as confirmed.
 */
object SlotReadBack {
    /** [ReadBack.CONFIRMED] when [tray] shows [spool]'s material and colour, otherwise [ReadBack.DIFFERENT] (also for a missing or empty tray). */
    fun compare(spool: Spool, tray: AmsTray?): ReadBack {
        if (tray == null || tray.isEmpty) return ReadBack.DIFFERENT
        if (normalise(spool.material) != normalise(tray.material)) return ReadBack.DIFFERENT
        val want = rgb(spool.colorHex)
        val have = rgb(tray.colorHex)
        return if (want != null && have != null && want != have) ReadBack.DIFFERENT else ReadBack.CONFIRMED
    }

    private fun normalise(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    private fun rgb(hex: String): String? = hex.trim().removePrefix("#").uppercase().takeIf { it.length >= 6 }?.take(6)
}
