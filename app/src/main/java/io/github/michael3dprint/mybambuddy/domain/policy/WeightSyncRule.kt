package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.Spool

/** What "update the inventory from the printer" would do for one slot, or why it must not. */
sealed interface WeightSyncPlan {
    /** [spool] would be set to about [newRemainingGrams] left (the printer's [percent] of its label weight); [grossGrams] is what is sent. */
    data class Ready(
        /** The inventory spool that would be updated, found by the tag the slot reports. */
        val spool: Spool,
        /** The percent the printer reports for the slot. */
        val percent: Int,
        /** What the inventory says is left now, in grams. */
        val currentRemainingGrams: Int,
        /** What the inventory would say after the update, in grams. */
        val newRemainingGrams: Int,
        /** The value sent to the weight route: the new remaining amount plus the empty spool's own weight. */
        val grossGrams: Double,
    ) : WeightSyncPlan

    /** Nothing is sent; [reason] is shown to the user. */
    data class Refused(val reason: String) : WeightSyncPlan
}

/**
 * Decides whether a Bambu slot's remaining amount can be written to the inventory (design 33). Pure and strict: the spool is found by the
 * tray UUID the slot reports, never by guesswork, and anything unclear is refused with a reason.
 */
object WeightSyncRule {
    private const val UUID_LENGTH = 32

    /** The plan for [tray], given every inventory spool, whether the inventory takes weight updates, and whether the printer is printing. */
    fun plan(tray: AmsTray, spools: List<Spool>, canWeigh: Boolean, printerBusy: Boolean): WeightSyncPlan {
        val percent = tray.remainPercent
        val refusal = when {
            !isBambuUuid(tray.trayUuid) -> "Only a Bambu spool read by the AMS reports its own amount."
            percent == null || percent <= 0 -> "The printer isn't reporting an amount for this spool."
            !canWeigh -> "Updating the inventory needs Spoolman. Bambuddy's built-in inventory keeps itself up to date."
            printerBusy -> "The printer is printing, so the amount is still changing. Try again when it's done."
            else -> null
        }
        if (refusal != null || percent == null) return WeightSyncPlan.Refused(refusal.orEmpty())
        val matches = spools.filter { it.trayUuid == tray.trayUuid }
        val spool = matches.singleOrNull() ?: return WeightSyncPlan.Refused(
            if (matches.isEmpty()) {
                "No spool in your inventory carries this spool's tag, so there is nothing safe to update."
            } else {
                "More than one inventory spool carries this spool's tag. Fix that first."
            },
        )
        val recorded = tray.loadedSpoolId
        val core = spool.coreWeightGrams
        return when {
            recorded != null && recorded != spool.id ->
                WeightSyncPlan.Refused(
                    "This slot is recorded as spool #$recorded, but the tag belongs to spool #${spool.id}. Fix the record first.",
                )
            spool.labelWeightGrams <= 0 || core == null -> WeightSyncPlan.Refused(
                "The inventory doesn't know this spool's weight, so the update can't be worked out.",
            )
            else -> readyOrSame(spool, percent, core)
        }
    }

    private fun readyOrSame(spool: Spool, percent: Int, core: Int): WeightSyncPlan {
        val newRemaining = spool.labelWeightGrams * percent.coerceAtMost(PERCENT) / PERCENT
        return if (kotlin.math.abs(newRemaining - spool.remainingGrams) < 1) {
            WeightSyncPlan.Refused("The inventory already matches the printer (about ${spool.remainingGrams} g).")
        } else {
            WeightSyncPlan.Ready(spool, percent, spool.remainingGrams, newRemaining, (newRemaining + core).toDouble())
        }
    }

    private fun isBambuUuid(value: String): Boolean = value.length == UUID_LENGTH && value.any { it != '0' }
}

private const val PERCENT = 100
