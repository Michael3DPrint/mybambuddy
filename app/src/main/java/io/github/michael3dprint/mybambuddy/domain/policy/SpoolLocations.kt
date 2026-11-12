package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit

/** Where each loaded spool is, as short text shared by the Spools list and the slot sheet (design 49). */
object SpoolLocations {
    /**
     * Spool id to its place, for example "AMS 1, slot 3". The printer's name comes first only when more than one printer has units, and a spool
     * linked to two slots lists both. A spool that is not loaded is not in the map.
     */
    fun of(units: List<AmsUnit>): Map<Int, String> {
        val withPrinter = units.map { it.printerId }.distinct().size > 1
        val where = linkedMapOf<Int, MutableList<String>>()
        units.forEach { unit ->
            unit.trays.forEach { tray ->
                val id = tray.loadedSpoolId ?: return@forEach
                val place = (if (withPrinter) "${unit.printerName}, " else "") + "${unit.name}, slot ${tray.trayId + 1}"
                where.getOrPut(id) { mutableListOf() } += place
            }
        }
        return where.mapValues { it.value.joinToString("; ") }
    }
}
