package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.DryPhase
import io.github.michael3dprint.mybambuddy.domain.model.DryingPresets
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.policy.ToolAvailability
import io.github.michael3dprint.mybambuddy.domain.policy.ToolStatus
import io.github.michael3dprint.mybambuddy.ui.common.ConfirmDialog
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** One line about an AMS unit: humidity, temperature and drying state. Shared by the Filament tab, the printer page popup and Tools (design 45). */
@Composable
internal fun AmsStatusLine(unit: AmsUnit) {
    Spoken(
        listOfNotNull(
            unit.humidityLevel?.let { "💧 $it %" },
            unit.tempC?.let { "🌡 ${"%.1f".format(it)} °C" },
            dryText(unit),
        ).joinToString("   ").ifBlank { "No humidity or temperature reported." },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The drying panel of one AMS unit with its confirmation: the same gate, reason line and confirmation wherever it is shown (the Filament tab, the
 * popup on the printer page, and Tools), so the places cannot disagree. A unit that cannot dry says so. Nothing starts without the confirmation.
 */
@Composable
internal fun AmsDryingControls(unit: AmsUnit, dryingViewModel: DryingViewModel) {
    val drafts by dryingViewModel.drafts.collectAsStateWithLifecycle()
    val draft = drafts["${unit.printerId}:${unit.amsId}"] ?: dryingViewModel.draftFor(unit)
    var confirmStart by remember { mutableStateOf(false) }
    val tool = ToolAvailability.statusOf(PrinterTool.DRYING, unit.capabilities, unit.printerName)
    if (unit.supportsDrying) {
        DryingPanel(unit, draft, tool, { dryingViewModel.updateDraft(unit, it) }, { dryingViewModel.stopDrying(unit) }) {
            confirmStart =
                true
        }
    } else {
        Text("This AMS can't dry filament.", style = MaterialTheme.typography.bodySmall)
    }
    if (confirmStart) {
        ConfirmDialog(
            title = stringResource(R.string.filament_start_drying),
            confirmLabel = stringResource(R.string.filament_start),
            onConfirm = {
                confirmStart = false
                dryingViewModel.startDrying(unit)
            },
            onCancel = { confirmStart = false },
        ) {
            Text("${unit.name}: ${draft.material} at ${draft.tempC} °C for ${draft.hours} h. The AMS will heat up.")
        }
    }
}

/** The drying state of a unit in words for the status line, or null when it is not drying. */
internal fun dryText(unit: AmsUnit): String? = when (unit.dryPhase) {
    DryPhase.OFF -> null
    DryPhase.CHECKING -> "Checking…"
    DryPhase.DRYING -> "Drying · ${unit.dryMinutesLeft / 60} h ${unit.dryMinutesLeft % 60} m left"
    DryPhase.COOLING -> "Cooling down"
    DryPhase.STOPPING -> "Stopping…"
    DryPhase.ERROR -> "Drying error"
}

/** The drying controls: presets, temperature and duration, Start or Stop; off with the tool's reason when drying is not available. */
@Composable
internal fun DryingPanel(
    unit: AmsUnit,
    draft: DryingDraft,
    tool: ToolStatus,
    onDraft: (DryingDraft) -> Unit,
    onStop: () -> Unit,
    onStart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
        Heading(stringResource(R.string.filament_drying), style = MaterialTheme.typography.titleSmall)
        if (!tool.isEnabled) {
            Text(
                tool.reason.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (unit.isDrying) {
            OutlinedButton(onClick = onStop, enabled = tool.isEnabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.filament_stop_drying))
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
            DryingPresets.all.take(3).forEach { preset ->
                FilledTonalButton(onClick = { onDraft(DryingDraft(preset.material, preset.tempC, preset.hours)) }) { Text(preset.material) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
            DryingPresets.all.drop(3).forEach { preset ->
                FilledTonalButton(onClick = { onDraft(DryingDraft(preset.material, preset.tempC, preset.hours)) }) { Text(preset.material) }
            }
        }
        Stepper("Temperature", "${draft.tempC} °C", { onDraft(draft.copy(tempC = (draft.tempC - 5).coerceIn(45, 85))) }) {
            onDraft(draft.copy(tempC = (draft.tempC + 5).coerceIn(45, 85)))
        }
        Stepper("Duration", "${draft.hours} h", { onDraft(draft.copy(hours = (draft.hours - 1).coerceIn(1, 24))) }) {
            onDraft(draft.copy(hours = (draft.hours + 1).coerceIn(1, 24)))
        }
        Button(onClick = onStart, enabled = tool.isEnabled, modifier = Modifier.fillMaxWidth()) { Text("Start drying (${draft.material})") }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = onMinus) { Text(stringResource(R.string.filament_text)) }
        Text(value, style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onPlus) { Text(stringResource(R.string.filament_text_2)) }
    }
}
