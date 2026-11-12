package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.policy.LowStockPolicy
import io.github.michael3dprint.mybambuddy.domain.policy.SlotFillRule
import io.github.michael3dprint.mybambuddy.domain.policy.SlotSpeech
import io.github.michael3dprint.mybambuddy.domain.policy.ToolAvailability
import io.github.michael3dprint.mybambuddy.domain.policy.barStops
import io.github.michael3dprint.mybambuddy.ui.common.nameMaxLines
import io.github.michael3dprint.mybambuddy.ui.common.rememberLargeFont
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * One AMS unit as a single line: its name, then its slot tiles beside it, with the reason underneath when slot changes are off. The
 * printer page and the Filament tab both draw their AMS units with this, so they look the same (design 33).
 */
@Composable
fun AmsSlotRow(
    unit: AmsUnit,
    spoolsById: Map<Int, Spool>,
    selected: (AmsTray) -> Boolean = { false },
    showSlotChangeReason: Boolean = true,
    onUnit: (() -> Unit)? = null,
    onSlot: (AmsTray) -> Unit,
) {
    val slotChange = ToolAvailability.statusOf(PrinterTool.SLOT_CHANGE, unit.capabilities, unit.printerName)
    val name: @Composable (Modifier) -> Unit = { m -> UnitName(unit, onUnit, m) }
    val tiles: @Composable RowScope.() -> Unit = {
        unit.trays.forEach { tray ->
            SlotTile(unit.name, tray, tray.loadedSpoolId?.let(spoolsById::get), Modifier.weight(1f), selected(tray)) { onSlot(tray) }
        }
    }
    val content: @Composable () -> Unit = {
        if (rememberLargeFont()) {
            // Large text: the unit name gets its own line and the tiles the full width (design 34). Normal sizes keep the side-by-side layout.
            Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                name(Modifier)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TILE_GAP), content = tiles)
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
            ) {
                name(Modifier.width(UNIT_NAME_WIDTH))
                tiles()
            }
        }
        if (showSlotChangeReason && !slotChange.isEnabled) {
            Text(slotChange.reason.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    // With a settings action the whole unit is one outlined box (design 45); without it the row is drawn as before.
    if (onUnit == null) {
        content()
    } else {
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(Dimens.Space.s)) { content() } }
    }
}

/**
 * One slot as its own outlined tile, top to bottom: the filament type, the colour swatch with the slot number inside, a bar that fades out
 * from where the amount ends and the estimated grams left (design 32). No spool-record text: that lives on the Filament tab.
 */
@Composable
private fun SlotTile(unitName: String, tray: AmsTray, linkedSpool: Spool?, modifier: Modifier, selected: Boolean, onClick: () -> Unit) {
    val fill = SlotFillRule.estimate(tray, linkedSpool)
    val low = fill?.let { LowStockPolicy.isLow(it.percent) } == true
    val color = if (tray.isEmpty) null else parseHexColor(tray.colorHex)
    val spoken = SlotSpeech.describe(unitName, tray, fill, low)
    Surface(
        onClick = onClick,
        // One button for a screen reader with one description; the swatch, text and bar inside are not read separately (design 34).
        modifier = modifier.clearAndSetSemantics {
            contentDescription = spoken
            role = Role.Button
            this.selected = selected
            onClick(label = "Open options for this slot") {
                onClick()
                true
            }
        },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) {
            BorderStroke(SELECTED_BORDER, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Column(
            Modifier.padding(horizontal = Dimens.Space.s, vertical = Dimens.Space.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TILE_LINE_GAP),
        ) {
            Text(
                tray.material.ifBlank { stringResource(R.string.printer_slot_empty) },
                style = MaterialTheme.typography.labelMedium,
                maxLines = nameMaxLines(),
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier.size(SWATCH_SIZE).clip(CircleShape).background(color ?: Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${tray.trayId + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    // Black or white, whichever reads on this colour; an empty slot has no fill, so it uses the normal text colour.
                    color =
                    color?.let {
                        if (it.luminance() >
                            LIGHT_COLOR
                        ) {
                            Color.Black
                        } else {
                            Color.White
                        }
                    } ?: MaterialTheme.colorScheme.onSurface,
                )
            }
            // The bar keeps its length: solid up to the amount left, then it fades out (design 32).
            val barColor = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Box(
                Modifier.fillMaxWidth().height(BAR_HEIGHT).clip(CircleShape).background(
                    Brush.horizontalGradient(
                        colorStops = barStops(fill?.fraction).map {
                            it.position to barColor.copy(alpha = it.alpha)
                        }.toTypedArray(),
                    ),
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // A warning symbol as well as red, so low stock is not shown by colour alone (design 34).
                if (low) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    fill?.let { "~${it.grams} g" } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val UNIT_NAME_WIDTH = 52.dp
private val SELECTED_BORDER = 3.dp
private val SWATCH_SIZE = 30.dp
private val TILE_GAP = 6.dp
private val TILE_LINE_GAP = 4.dp
private val BAR_HEIGHT = 4.dp
private const val LIGHT_COLOR = 0.5f

/** The unit's name; with a settings action it is a 48 dp button with a gear that says what a tap does (design 45). */
@Composable
private fun UnitName(unit: AmsUnit, onUnit: (() -> Unit)?, modifier: Modifier) {
    if (onUnit == null) {
        Text(unit.name, style = MaterialTheme.typography.labelLarge, modifier = modifier)
        return
    }
    Column(
        modifier.heightIn(min = UNIT_TARGET).clickable(role = Role.Button, onClick = onUnit)
            .clearAndSetSemantics {
                contentDescription = "${unit.name} settings"
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(unit.name, style = MaterialTheme.typography.labelLarge)
        Icon(
            Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(GEAR_SIZE),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private val UNIT_TARGET = 48.dp
private val GEAR_SIZE = 16.dp
