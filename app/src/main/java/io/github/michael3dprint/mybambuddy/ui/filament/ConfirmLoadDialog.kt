package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.policy.SlotWarning
import io.github.michael3dprint.mybambuddy.ui.common.ConfirmDialog
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * The one confirmation every spool-to-slot assignment goes through. It says what Bambuddy may do to the printer
 * and lists each risk the policy found (see docs/design/09-slot-assignment-safety.md).
 */
@Composable
fun ConfirmLoadDialog(pending: PendingAssignment, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val spool = pending.spool
    ConfirmDialog(
        title = stringResource(R.string.filament_load_into_the_printer),
        confirmLabel = stringResource(R.string.filament_load),
        onConfirm = onConfirm,
        onCancel = onCancel,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
            InfoCard("Spool") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
                    ColorSwatch(spool.colorHex, Modifier.size(32.dp))
                    Column {
                        Text(spool.title, style = MaterialTheme.typography.titleSmall)
                        Spoken(
                            spoolDetails(spool),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            InfoCard("Into this slot") {
                Spoken(
                    "${pending.unit.printerName} · ${pending.unit.name} · Slot ${pending.trayId + 1}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spoken(
                    slotNow(pending.unit.trays.firstOrNull { it.trayId == pending.trayId }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            InfoCard("What will happen") {
                Text("• The inventory records this spool in that slot.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "• Bambuddy may set the slot on the printer to: ${pending.assessment.pushSummary}, with the spool's temperatures.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stringResource(R.string.filament_if_no_calibration_profile_is), style = MaterialTheme.typography.bodySmall)
            }
            pending.assessment.warnings.forEach {
                val info = it is SlotWarning.SlotLooksEmpty
                Text(
                    "${if (info) "i" else "!"} ${describe(it)}",
                    color = if (info) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.xs)) {
        Heading(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** The details line under a spool's name: colour name, material, what is left of its label weight, and where it is kept. */
internal fun spoolDetails(spool: Spool): String = listOfNotNull(
    spool.colorName.takeIf { it.isNotBlank() },
    spool.material.takeIf { it.isNotBlank() },
    "${spool.remainingGrams} g left of ${spool.labelWeightGrams} g",
    spool.location?.takeIf { it.isNotBlank() },
).joinToString(" · ")

/** What the slot holds now, for the confirmation: its filament and percent, or empty, plus the spool recorded there. */
internal fun slotNow(tray: AmsTray?): String {
    if (tray == null || tray.isEmpty) return "Now: empty"
    val content = listOfNotNull(tray.label, tray.remainPercent?.let { "$it %" }).joinToString(", ")
    val recorded = tray.loadedSpoolId?.let { " · recorded spool #$it" }.orEmpty()
    return "Now: $content$recorded"
}

/** The one-line text for each warning shown in the load confirmation. */
internal fun describe(warning: SlotWarning): String = when (warning) {
    is SlotWarning.ReplacesSpool -> "This slot is recorded as holding spool #${warning.otherSpoolId}; it will be unassigned."
    is SlotWarning.OverwritesDetected -> "The printer reports ${warning.current} in this slot. Its settings may be overwritten."
    is SlotWarning.MaterialMismatch -> "The slot reports ${warning.slotMaterial}, but this spool is ${warning.spoolMaterial}."
    is SlotWarning.MovesFromOtherSlot -> "This spool is also recorded in ${warning.where}; it will move."
    SlotWarning.UnitDrying -> "This AMS is drying right now."
    SlotWarning.SlotLooksEmpty -> "The slot looks empty. Bambuddy will set it up when you insert the spool."
    is SlotWarning.MayBeRefused -> warning.reason
}
