package io.github.michael3dprint.mybambuddy.domain.slicing

import io.github.michael3dprint.mybambuddy.domain.model.BedType
import io.github.michael3dprint.mybambuddy.domain.model.ProjectSlot
import io.github.michael3dprint.mybambuddy.domain.model.SliceOptions
import io.github.michael3dprint.mybambuddy.domain.model.SliceRequest
import io.github.michael3dprint.mybambuddy.domain.model.SlicerPreset

/** Chooses sensible presets for a printer model so the user only changes what they want to (design 39). Pure and tested. */
object SlicePicker {
    private val nozzle = Regex("""(\d\.\d)\s*nozzle""", RegexOption.IGNORE_CASE)
    private const val DEFAULT_NOZZLE = "0.4"

    private fun mentions(name: String, word: String) = Regex("""\b${Regex.escape(word)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(name)

    /** The printer preset for [model]; the 0.4 nozzle one when there is a choice, else the first that names the model. */
    fun printer(model: String, printers: List<SlicerPreset>): SlicerPreset? {
        val matches = printers.filter { mentions(it.name, model) }
        return matches.firstOrNull { it.name.contains("$DEFAULT_NOZZLE nozzle", ignoreCase = true) } ?: matches.firstOrNull()
    }

    private fun nozzleOf(printer: SlicerPreset) = nozzle.find(printer.name)?.groupValues?.get(1) ?: DEFAULT_NOZZLE

    private fun fitsNozzle(preset: SlicerPreset, printer: SlicerPreset): Boolean {
        val wanted = nozzleOf(printer)
        val named = nozzle.find(preset.name)?.groupValues?.get(1)
        return if (named != null) named == wanted else wanted == DEFAULT_NOZZLE
    }

    private fun fitsPrinter(preset: SlicerPreset, printer: SlicerPreset, model: String): Boolean = when {
        preset.compatiblePrinters.isNotEmpty() -> printer.name in preset.compatiblePrinters
        else -> mentions(preset.name, model) && fitsNozzle(preset, printer)
    }

    /** Process presets that fit [printer], best first: "0.20mm Standard" for the model, then other "0.20mm Standard", then the rest by name. */
    fun processOptions(printer: SlicerPreset, model: String, processes: List<SlicerPreset>): List<SlicerPreset> =
        processes.filter { fitsPrinter(it, printer, model) }.sortedWith(
            compareBy<SlicerPreset>(
                { if (it.name.startsWith("0.20mm Standard") && !it.name.contains("Copy", ignoreCase = true)) 0 else 1 },
                { if (it.name.startsWith("0.20mm Standard")) 0 else 1 },
                { it.name },
            ),
        )

    /** Filament presets of [type] that fit [printer], best first: "Bambu TYPE Basic", "Bambu TYPE", "Generic TYPE", then the rest by name. */
    fun filamentOptions(type: String, printer: SlicerPreset, model: String, filaments: List<SlicerPreset>): List<SlicerPreset> {
        val fitting = filaments.filter { f ->
            val isType = f.type?.equals(type, ignoreCase = true) ?: mentions(f.name, type)
            isType && fitsPrinter(f, printer, model) && mentions(f.name, type)
        }
        return fitting.sortedWith(
            compareBy<SlicerPreset>(
                {
                    when {
                        it.name.startsWith("Bambu $type Basic", ignoreCase = true) -> 0
                        it.name.startsWith("Bambu $type ", ignoreCase = true) -> 1
                        it.name.startsWith("Generic $type", ignoreCase = true) -> 2
                        else -> 3
                    }
                },
                { it.name },
            ),
        )
    }

    /** The preset for a loaded spool: the first of [type]'s options that names its [subBrand] (such as "PLA Basic"), else the best of the type. */
    fun presetForSpool(type: String, subBrand: String, printer: SlicerPreset, model: String, filaments: List<SlicerPreset>): SlicerPreset? {
        val options = filamentOptions(type, printer, model, filaments)
        val line = subBrand.trim()
        return (if (line.isNotEmpty()) options.firstOrNull { it.name.contains(line, ignoreCase = true) } else null) ?: options.firstOrNull()
    }

    /** The default filament preset for each slot: the best of its type, and for a slot with no match the first slot's pick. */
    fun defaultFilaments(
        slots: List<ProjectSlot>,
        printer: SlicerPreset,
        model: String,
        filaments: List<SlicerPreset>,
    ): Map<Int, SlicerPreset?> {
        val picks = slots.associate { it.slotId to filamentOptions(it.type, printer, model, filaments).firstOrNull() }
        val fallback = slots.firstNotNullOfOrNull { picks[it.slotId] }
        return slots.associate { s -> s.slotId to (picks[s.slotId] ?: if (s.usedInPlate) null else fallback) }
    }
}

/** Builds the slice request from the user's choices; refuses with a reason while anything is missing. */
object SliceRequestBuilder {
    /** The request, or null when the printer, process, build plate or a slot's filament is not chosen yet. */
    fun build(
        printer: SlicerPreset?,
        process: SlicerPreset?,
        slots: List<ProjectSlot>,
        chosen: Map<Int, SlicerPreset?>,
        plate: Int,
        bed: BedType?,
        options: SliceOptions = SliceOptions(),
    ): SliceRequest? {
        if (printer == null || process == null || bed == null || slots.isEmpty()) return null
        val ordered = slots.sortedBy { it.slotId }
        val filaments = ordered.map { chosen[it.slotId] ?: return null }
        return SliceRequest(printer, process, filaments, ordered.map { colour(it.colorHex) }, plate, bed, options)
    }

    /** "#RRGGBB" from "RRGGBB", "#RRGGBB" or "RRGGBBAA"; blank when the colour cannot be read, which leaves the slicer's fallback. */
    fun colour(hex: String): String {
        val clean = hex.trim().removePrefix("#")
        return if (clean.length >= HEX_LENGTH && clean.take(HEX_LENGTH).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "#" + clean.take(HEX_LENGTH).uppercase()
        } else {
            ""
        }
    }

    private const val HEX_LENGTH = 6
}
