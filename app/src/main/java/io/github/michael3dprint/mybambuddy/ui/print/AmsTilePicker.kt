package io.github.michael3dprint.mybambuddy.ui.print

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.policy.AmsTrayAddress
import io.github.michael3dprint.mybambuddy.ui.filament.AmsSlotRow
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * Picks a loaded AMS slot by tapping its tile, drawn with the same tiles as the Filament tab and the printer page (type, colour, amount),
 * with the chosen one outlined. [onPick] gets the printer's tray number; an empty slot cannot be picked.
 */
@Composable
internal fun AmsTilePicker(units: List<AmsUnit>, selectedTray: Int?, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
        units.forEach { unit ->
            AmsSlotRow(
                unit = unit,
                spoolsById = emptyMap(),
                selected = { tray -> selectedTray != null && AmsTrayAddress.forFeed(unit.amsId, unit.isHt, tray.trayId) == selectedTray },
                showSlotChangeReason = false,
                onSlot = { tray -> if (!tray.isEmpty) AmsTrayAddress.forFeed(unit.amsId, unit.isHt, tray.trayId)?.let(onPick) },
            )
        }
    }
}
