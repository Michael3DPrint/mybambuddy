package io.github.michael3dprint.mybambuddy.domain.model
import io.github.michael3dprint.mybambuddy.domain.policy.LowStockPolicy

/** A physical spool in the user's inventory. */
data class Spool(
    val id: Int,
    val brand: String,
    val material: String,
    val subtype: String,
    val colorName: String,
    /** RRGGBB or RRGGBBAA hex, no '#'. Empty when unknown. */
    val colorHex: String,
    val labelWeightGrams: Int,
    val usedGrams: Int,
    val tagUid: String?,
    val location: String?,
    /** The spool's own low-stock threshold in percent, or null to use the app default. */
    val lowStockPct: Int? = null,
    /** The Bambu tray UUID recorded on this spool, upper-case hex; empty when none (design 33). */
    val trayUuid: String = "",
    /** The empty spool's own weight in grams, when the inventory knows it. */
    val coreWeightGrams: Int? = null,
) {
    val remainingGrams: Int get() = (labelWeightGrams - usedGrams).coerceAtLeast(0)
    val isLow: Boolean get() = LowStockPolicy.isLow(remainingPercent, lowStockPct)

    val remainingPercent: Int
        get() = if (labelWeightGrams > 0) (remainingGrams * 100 / labelWeightGrams).coerceIn(0, 100) else 0

    /** e.g. "Bambu Lab PLA Metal" */
    val title: String
        get() = listOf(brand, material, subtype).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Spool #$id" }
}

/** A product from the filament catalog (SpoolmanDB). */
data class CatalogFilament(
    val id: String,
    val manufacturer: String,
    val name: String,
    val material: String,
    val colorHex: String,
    val weightGrams: Int,
    val spoolWeightGrams: Int?,
    val nozzleTemp: Int?,
) {
    val title: String get() = "$manufacturer $material $name".trim()
}

/** What the user fills in to add a spool. */
data class SpoolDraft(
    val brand: String = "",
    val material: String = "",
    val subtype: String = "",
    val colorName: String = "",
    val colorHex: String = "",
    val labelWeightGrams: Int = 1000,
    val coreWeightGrams: Int? = null,
    val note: String = "",
)

/** One print that used filament from a spool. */
data class UsageEntry(val date: String, val printName: String, val grams: Int, val status: String)
