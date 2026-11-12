package io.github.michael3dprint.mybambuddy.domain.print

import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.policy.AmsTrayAddress
import io.github.michael3dprint.mybambuddy.domain.policy.SlotFillRule

/** Turns a printer's AMS units into the slots a print can use, each with the printer's own tray number. */
object SlotCandidates {
    /** The slots of the units that belong to printer [printerId]; a slot that cannot be addressed is left out. */
    fun of(units: List<AmsUnit>, printerId: Int?): List<SlotCandidate> = units.filter { it.printerId == printerId }.flatMap { unit ->
        unit.trays.mapNotNull { tray ->
            val address = AmsTrayAddress.forFeed(unit.amsId, unit.isHt, tray.trayId) ?: return@mapNotNull null
            SlotCandidate(
                tray = address,
                label = "${unit.name} slot ${tray.trayId + 1}: ${tray.label}",
                material = tray.material,
                colorHex = tray.colorHex,
                isEmpty = tray.isEmpty,
                gramsLeft = SlotFillRule.estimate(tray, null)?.grams,
                subBrand = tray.subBrand,
            )
        }
    }
}
