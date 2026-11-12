package io.github.michael3dprint.mybambuddy.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.michael3dprint.mybambuddy.domain.model.JobOptions
import io.github.michael3dprint.mybambuddy.domain.model.TriState
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.SwitchRow
import io.github.michael3dprint.mybambuddy.ui.print.AmsTilePicker
import io.github.michael3dprint.mybambuddy.ui.print.FindingsList
import io.github.michael3dprint.mybambuddy.ui.print.PrintChoiceField
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** The Settings tab of a job: printer, plate, a slot per filament, the print options and what the checks found; read-only unless the job is waiting. */
@Composable
internal fun JobSettingsTab(state: QueueJobState, onChoose: (JobChoice) -> Unit) {
    val edited = state.edited ?: return
    val options = edited.options
    val editable = state.editable
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
        val printer = state.printers.firstOrNull { it.id == edited.printerId?.toString() }
        if (editable) {
            PrintChoiceField(
                label = "Printer",
                value = printer?.let { "${it.name} (${it.model})" } ?: "Choose a printer",
                options = state.printers.mapNotNull { p ->
                    p.id.toIntOrNull()?.let { id ->
                        "${p.name} (${p.model})" to
                            { onChoose(JobChoice.Printer(id)) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("Printer: ${printer?.name ?: state.job?.printerName.orEmpty()}")
        }
        val plates = state.file?.plates.orEmpty()
        if (plates.size > 1 && editable) {
            PrintChoiceField(
                label = "Plate",
                value = state.plate?.let { "Plate ${it.index}${it.name?.let { n -> ": $n" }.orEmpty()}" } ?: "Choose a plate",
                options = plates.map {
                    "Plate ${it.index}${it.name?.let { n -> ": $n" }.orEmpty()}" to
                        { onChoose(JobChoice.Plate(it.index)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.plate?.filaments.orEmpty().forEach { need ->
            Heading("Filament ${need.slotId}: ${need.type}", style = MaterialTheme.typography.titleSmall)
            if (editable) {
                AmsTilePicker(state.units, state.chosen[need.slotId]?.tray) { tray ->
                    state.slots.firstOrNull { it.tray == tray }?.let { onChoose(JobChoice.Slot(need.slotId, it)) }
                }
                if (state.chosen[need.slotId] == null) Text("Tap a loaded slot.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(state.chosen[need.slotId]?.label ?: "No slot recorded")
            }
        }
        Heading("Print options", style = MaterialTheme.typography.titleSmall)
        OptionsBlock(options, editable) { onChoose(JobChoice.Options(it)) }
        FindingsList(state.findings)
    }
}

@Composable
private fun OptionsBlock(options: JobOptions, editable: Boolean, onChange: (JobOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
        if (!editable) {
            Text(
                "Bed levelling: ${options.bedLevelling.label}. Flow calibration: ${options.flowCalibration.label}. " +
                    "Vibration calibration ${onOff(options.vibrationCalibration)}, layer inspection ${onOff(options.layerInspection)}, " +
                    "timelapse ${onOff(options.timelapse)}, AMS ${onOff(options.useAms)}.",
            )
            return
        }
        PrintChoiceField(
            label = "Bed levelling",
            value = options.bedLevelling.label,
            options = TriState.entries.map { it.label to { onChange(options.copy(bedLevelling = it)) } },
            modifier = Modifier.fillMaxWidth(),
        )
        PrintChoiceField(
            label = "Flow calibration",
            value = options.flowCalibration.label,
            options = TriState.entries.map { it.label to { onChange(options.copy(flowCalibration = it)) } },
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow("Vibration calibration", options.vibrationCalibration) { onChange(options.copy(vibrationCalibration = it)) }
        SwitchRow("Layer inspection", options.layerInspection) { onChange(options.copy(layerInspection = it)) }
        SwitchRow("Timelapse", options.timelapse) { onChange(options.copy(timelapse = it)) }
        SwitchRow("Use the AMS", options.useAms) { onChange(options.copy(useAms = it)) }
    }
}

private fun onOff(value: Boolean) = if (value) "on" else "off"
