package io.github.michael3dprint.mybambuddy.ui.print

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.michael3dprint.mybambuddy.domain.print.Finding
import io.github.michael3dprint.mybambuddy.domain.print.FindingLevel
import io.github.michael3dprint.mybambuddy.domain.print.PrintEstimate
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.common.politeLive
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** The review: file, estimate, printer, plate, the slot for each filament, everything the validation found, and the two actions. */
@Composable
internal fun PrintReviewStep(
    state: PrintFlowState,
    onSelect: (PrintSelection) -> Unit,
    onAdd: () -> Unit,
    onStart: () -> Unit,
    onSlice: () -> Unit,
) {
    val file = state.file ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
        Heading(file.name, style = MaterialTheme.typography.titleLarge)
        state.message?.let { Text(it, Modifier.politeLive(), color = MaterialTheme.colorScheme.error) }
        PrintPreview(state.preview)
        state.plate?.let { Spoken(PrintEstimate.line(it.seconds, it.grams), style = MaterialTheme.typography.bodyLarge) }
        PrintChoiceField(
            label = "Printer",
            value = state.printer?.let { "${it.name} (${it.model})" } ?: "Choose a printer",
            options = state.printers.map { "${it.name} (${it.model})" to { onSelect(PrintSelection.Printer(it.id)) } },
            modifier = Modifier.fillMaxWidth(),
        )
        if (file.plates.size > 1) {
            PrintChoiceField(
                label = "Plate",
                value = state.plate?.let { "Plate ${it.index}${it.name?.let { n -> ": $n" }.orEmpty()}" } ?: "Choose a plate",
                options = file.plates.map {
                    "Plate ${it.index}${it.name?.let { n -> ": $n" }.orEmpty()}" to
                        { onSelect(PrintSelection.Plate(it.index)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Heading("Filament", style = MaterialTheme.typography.titleMedium)
        state.plate?.filaments.orEmpty().forEach { need ->
            Heading("${need.slotId}: ${need.type}, about ${need.grams.toInt()} g", style = MaterialTheme.typography.titleSmall)
            AmsTilePicker(state.units, state.chosen[need.slotId]?.tray) { tray ->
                onSelect(PrintSelection.Slot(need.slotId, state.slots.firstOrNull { it.tray == tray }))
            }
            if (state.chosen[need.slotId] == null) Text("Tap a loaded slot.", style = MaterialTheme.typography.bodySmall)
        }
        FindingsList(state.findings)
        if (state.printer?.awaitingPlateClear == true) {
            OutlinedButton(onClick = { onSelect(PrintSelection.PlateCleared) }, modifier = Modifier.fillMaxWidth()) {
                Text("Mark plate cleared")
            }
            Text(
                "Only tells Bambuddy the plate is empty. Nothing is sent to the printer, so check the plate first.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!file.isSliced && file.origin == io.github.michael3dprint.mybambuddy.domain.model.FileOrigin.LIBRARY) {
            Button(onClick = onSlice, enabled = state.printer != null, modifier = Modifier.fillMaxWidth()) {
                Text("Slice for ${state.printer?.model ?: "this printer"}")
            }
        }
        Button(onClick = onStart, enabled = state.canStart, modifier = Modifier.fillMaxWidth()) { Text("Start print") }
        OutlinedButton(onClick = onAdd, enabled = state.canAdd, modifier = Modifier.fillMaxWidth()) { Text("Add to queue") }
        Text(
            "Not checked: whether the plate fitted is the one named in the file, and the physical state of the bed. Only you can check those.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The findings, each starting with its level in words so colour is never the only signal. */
@Composable
internal fun FindingsList(findings: List<Finding>) {
    if (findings.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
        findings.forEach { f ->
            val (prefix, color) = when (f.level) {
                FindingLevel.BLOCKER -> "Problem: " to MaterialTheme.colorScheme.error
                FindingLevel.WARNING -> "Check: " to MaterialTheme.colorScheme.tertiary
                FindingLevel.INFO -> "Note: " to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Spoken(prefix + f.text, color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
