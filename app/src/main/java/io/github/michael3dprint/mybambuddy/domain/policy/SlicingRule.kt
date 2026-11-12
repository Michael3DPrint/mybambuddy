package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.CheckLevel
import io.github.michael3dprint.mybambuddy.domain.model.PRINTER_PRESET_PREFIX
import io.github.michael3dprint.mybambuddy.domain.model.SlicingCheck
import io.github.michael3dprint.mybambuddy.domain.model.SlicingInputs
import io.github.michael3dprint.mybambuddy.domain.model.SlicingReadiness

/** Decides whether server slicing is ready from what Bambuddy reports (design 36). Pure, so every row of the check is tested. */
object SlicingRule {
    private const val BAMBU_STUDIO = "bambu_studio"
    private const val ORCA = "orcaslicer"
    private const val BAMBU_PORT = 3001
    private const val ORCA_PORT = 3003

    /** The check list for [inputs], in the order shown. */
    fun evaluate(inputs: SlicingInputs): SlicingReadiness {
        val s = inputs.settings
        val chosen = slicerName(s.preferredSlicer)
        val port = if (s.preferredSlicer == ORCA) ORCA_PORT else BAMBU_PORT
        val checks = mutableListOf<SlicingCheck>()
        checks += SlicingCheck(
            "switch",
            "Server slicing is switched on",
            s.useSlicerApi,
            CheckLevel.BLOCKER,
            if (s.useSlicerApi) {
                "Use Slicer API is on in Bambuddy."
            } else {
                "Use Slicer API is off in Bambuddy, so Slice would open a desktop slicer instead."
            },
            if (s.useSlicerApi) emptyList() else SlicingHelp.switchOn(),
        )
        val known = s.preferredSlicer == BAMBU_STUDIO || s.preferredSlicer == ORCA
        checks += SlicingCheck(
            "slicer",
            if (known) "Slicer chosen: $chosen" else "No slicer chosen",
            known,
            CheckLevel.BLOCKER,
            if (known) "Preferred Slicer is $chosen." else "Preferred Slicer is not set to Bambu Studio or OrcaSlicer.",
            if (known) emptyList() else SlicingHelp.chooseSlicer(),
        )
        val reaches = inputs.presets.standardPrinterCount > 0
        checks += SlicingCheck(
            "reach",
            if (reaches) "Bambuddy can reach the slicer app" else "Bambuddy can't reach the slicer app",
            reaches,
            CheckLevel.BLOCKER,
            if (reaches) {
                "The slicer app answered with ${inputs.presets.standardPrinterCount} printer presets" +
                    (if (s.addressSet) "." else " (no address is saved, so Bambuddy used its default).")
            } else {
                "Bambuddy got no printer presets from the slicer app. It is not running, not reachable, " +
                    "or Bambuddy points at the wrong address" +
                    (if (s.addressSet) "." else " (no address is saved, so it used its default).")
            },
            if (reaches) emptyList() else SlicingHelp.reachSlicer(chosen, port),
        )
        inputs.printers.forEach { p ->
            val model = p.model.trim()
            if (model.isEmpty()) {
                checks +=
                    SlicingCheck(
                        PRINTER_PRESET_PREFIX + p.name,
                        "Printer preset for ${p.name}",
                        true,
                        CheckLevel.INFO,
                        "Bambuddy does not report this printer's model, so it was not checked.",
                    )
            } else {
                val found = inputs.presets.printerPresetNames.any { it.contains(model, ignoreCase = true) }
                checks += SlicingCheck(
                    PRINTER_PRESET_PREFIX + p.name,
                    "Printer preset for ${p.name} ($model)",
                    found,
                    CheckLevel.WARNING,
                    if (found) {
                        "A printer preset for $model exists."
                    } else {
                        "No printer preset mentions $model, so Bambuddy cannot slice for this printer yet."
                    },
                    if (found) emptyList() else SlicingHelp.importPresets(),
                )
            }
        }
        val haveProfiles = inputs.presets.processCount > 0 && inputs.presets.filamentCount > 0
        checks += SlicingCheck(
            "profiles",
            if (haveProfiles) "Process and filament presets are available" else "Process or filament presets are missing",
            haveProfiles,
            CheckLevel.BLOCKER,
            if (haveProfiles) {
                "${inputs.presets.processCount} process and ${inputs.presets.filamentCount} filament presets."
            } else {
                "Slicing needs at least one process preset and one filament preset."
            },
            if (haveProfiles) emptyList() else SlicingHelp.importPresets(),
        )
        checks += SlicingCheck(
            "cloud",
            if (inputs.presets.cloudSignedIn) "Bambu Cloud presets are available" else "Bambu Cloud is not signed in",
            inputs.presets.cloudSignedIn,
            CheckLevel.INFO,
            if (inputs.presets.cloudSignedIn) {
                "Your Bambu Cloud presets can be used."
            } else {
                "Signing in adds your own Bambu Cloud profiles; slicing can work without it."
            },
        )
        return SlicingReadiness(checks)
    }

    private fun slicerName(preferred: String): String = when (preferred) {
        BAMBU_STUDIO -> "Bambu Studio"
        ORCA -> "OrcaSlicer"
        else -> "the slicer"
    }
}
