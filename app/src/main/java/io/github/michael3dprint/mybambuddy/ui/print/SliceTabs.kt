package io.github.michael3dprint.mybambuddy.ui.print

/** The tabs of the slice page (design 40): one concern each, so no tab is busy. */
enum class SliceTab(val title: String) {
    PLATE("Plate"),
    FILAMENT("Filament"),
    PROCESS("Process"),
    OPTIONS("Options"),
}

/** The words on each tab. A tab that still needs something says so in words, so colour is never the only cue. Pure, so it is tested. */
object SliceTabs {
    /** "scale 150 %, 1 changed" or null when nothing on the Options tab differs from its defaults. */
    fun optionsNote(state: SliceState): String? {
        val parts = listOfNotNull(
            state.scalePercent?.takeIf { it != 100 }?.let { "scale $it %" },
            state.effectiveOptions.changed.takeIf { it > 0 }?.let { "$it changed" },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /** "Plate (needed)", "Options (2 changed)" or just the title. */
    fun label(tab: SliceTab, state: SliceState): String = when (tab) {
        SliceTab.PLATE -> if (state.bed == null) "${tab.title} (needed)" else tab.title
        SliceTab.FILAMENT -> if (state.slots.any { state.chosen[it.slotId] == null }) "${tab.title} (needed)" else tab.title
        SliceTab.PROCESS -> if (state.process == null) "${tab.title} (needed)" else tab.title
        SliceTab.OPTIONS -> when {
            state.scalePercent == null -> "${tab.title} (needed)"
            else -> optionsNote(state)?.let { "${tab.title} ($it)" } ?: tab.title
        }
    }
}
