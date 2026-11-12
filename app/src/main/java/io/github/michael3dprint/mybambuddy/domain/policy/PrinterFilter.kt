package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.PrinterMaintenance
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem

/** Narrows the queue and the upkeep lists to one printer (design 30). A null printer id means "all printers" and returns the list unchanged. */
object PrinterFilter {
    /** The jobs that belong to [printerId]; jobs not yet given a printer only show in the all-printers list. */
    fun queue(items: List<QueueItem>, printerId: Int?): List<QueueItem> = if (printerId ==
        null
    ) {
        items
    } else {
        items.filter { it.printerId == printerId }
    }

    /** The upkeep of [printerId] only. */
    fun maintenance(printers: List<PrinterMaintenance>, printerId: Int?): List<PrinterMaintenance> =
        if (printerId == null) printers else printers.filter { it.printerId == printerId }

    /** The AMS units of [printerId] only. */
    fun ams(units: List<AmsUnit>, printerId: Int?): List<AmsUnit> = if (printerId ==
        null
    ) {
        units
    } else {
        units.filter { it.printerId == printerId }
    }
}
