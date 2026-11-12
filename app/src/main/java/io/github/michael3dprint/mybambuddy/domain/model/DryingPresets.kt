package io.github.michael3dprint.mybambuddy.domain.model

/** Drying presets: temperature in C and hours, per material family. */
object DryingPresets {
    data class Preset(val material: String, val tempC: Int, val hours: Int)

    /** The presets, one per material family. The first is the default. */
    val all = listOf(
        Preset("PLA", 55, 8),
        Preset("PETG", 65, 8),
        Preset("TPU", 70, 8),
        Preset("ABS/ASA", 80, 8),
        Preset("PA/Nylon", 85, 12),
        Preset("PC", 80, 8),
    )

    /** The preset whose material family the given name starts with, or the first preset when none matches. */
    fun forMaterial(material: String): Preset =
        all.firstOrNull { material.uppercase().startsWith(it.material.substringBefore('/')) } ?: all.first()
}
