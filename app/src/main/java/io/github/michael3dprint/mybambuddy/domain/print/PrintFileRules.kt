package io.github.michael3dprint.mybambuddy.domain.print

/** Which files the print flow accepts, decided on the phone before anything is uploaded (design 35). */
object PrintFileRules {
    /** A reason to refuse [fileName], or null when it can be uploaded. Only 3MF files (including .gcode.3mf) can be printed. */
    fun refusal(fileName: String): String? {
        val lower = fileName.trim().lowercase()
        return when {
            lower.endsWith(".3mf") -> null
            lower.endsWith(".gcode") -> "Raw G-code can't be printed from here. Choose a .gcode.3mf or .3mf file sliced in Bambu Studio."
            else -> "Choose a .3mf or .gcode.3mf file."
        }
    }
}

/** A MakerWorld model link split into the model id and, when the link names one, the plate configuration (profile) id. */
data class MakerWorldLinkParts(val modelId: Int, val profileId: Int?)

/** Reads a MakerWorld link; anything that is not a MakerWorld model link gives null, so nothing is sent for it. */
object MakerWorldLink {
    private val pattern =
        Regex(
            """^(?:https?://)?(?:www\.)?makerworld\.com/(?:[a-z]{2}(?:-[a-zA-Z]{2})?/)?models/(\d{1,12})(?:[-\w]*)(?:[/?][^#]*)?(?:#profileId-(\d{1,12}))?$""",
        )

    /** The parts of [text], or null when it is not a MakerWorld model link. */
    fun parse(text: String): MakerWorldLinkParts? {
        val match = pattern.matchEntire(text.trim()) ?: return null
        val model = match.groupValues[1].toIntOrNull() ?: return null
        return MakerWorldLinkParts(model, match.groupValues[2].toIntOrNull())
    }
}
