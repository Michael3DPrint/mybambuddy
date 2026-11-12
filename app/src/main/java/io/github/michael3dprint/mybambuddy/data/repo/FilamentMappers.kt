package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.dto.ExternalFilamentDto
import io.github.michael3dprint.mybambuddy.data.api.dto.InventorySpoolDto
import io.github.michael3dprint.mybambuddy.domain.model.CatalogFilament
import io.github.michael3dprint.mybambuddy.domain.model.Spool

/** Maps an inventory row to a [Spool]; a row without an id gives null. */
internal fun InventorySpoolDto.toSpool(): Spool? {
    val spoolId = id ?: return null
    return Spool(
        id = spoolId,
        brand = brand.orEmpty(),
        material = material.orEmpty(),
        subtype = subtype.orEmpty(),
        colorName = colorName.orEmpty(),
        colorHex = rgba.orEmpty(),
        labelWeightGrams = labelWeight ?: 1000,
        usedGrams = (weightUsed ?: 0.0).toInt(),
        tagUid = tagUid ?: trayUuid,
        location = storageLocation,
        lowStockPct = lowStockThresholdPct,
        trayUuid = trayUuid.orEmpty().filter { it.isLetterOrDigit() }.uppercase(),
        coreWeightGrams = coreWeight,
    )
}

/** Maps a catalogue row to a [CatalogFilament]; a row without an id gives null. */
internal fun ExternalFilamentDto.toCatalog(): CatalogFilament? {
    val key = id ?: return null
    return CatalogFilament(
        id = key,
        manufacturer = manufacturer.orEmpty(),
        name = name.orEmpty(),
        material = material.orEmpty(),
        colorHex = colorHex.orEmpty(),
        weightGrams = (weight ?: 1000.0).toInt(),
        spoolWeightGrams = spoolWeight?.toInt(),
        nozzleTemp = extruderTemp,
    )
}
