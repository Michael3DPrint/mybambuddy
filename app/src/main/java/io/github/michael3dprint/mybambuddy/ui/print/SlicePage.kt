package io.github.michael3dprint.mybambuddy.ui.print

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.BedType
import io.github.michael3dprint.mybambuddy.domain.slicing.ScaleRule
import io.github.michael3dprint.mybambuddy.ui.common.AppTabRow
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.common.SwitchRow
import io.github.michael3dprint.mybambuddy.ui.common.politeLive
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * The slice page body (design 40): loading, the choices with the additional options, progress while slicing, or the reason a load failed.
 * It replaces the earlier popup and scrolls with the rest of the print flow.
 */
@Composable
internal fun SlicePageContent(
    state: SliceState,
    tab: SliceTab,
    units: List<AmsUnit>,
    preview: ByteArray?,
    onChoose: (SliceChoice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
        when (state.phase) {
            SlicePhase.LOADING -> {
                Text("Reading Bambuddy's presets and the file's filaments. This can take up to a minute for a large model.")
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            SlicePhase.SLICING -> {
                Heading(state.working.ifBlank { "Slicing ${state.source?.name.orEmpty()}" }, style = MaterialTheme.typography.titleMedium)
                val percent = state.percent
                if (percent != null) {
                    LinearProgressIndicator(progress = { percent / PERCENT_FULL }, modifier = Modifier.fillMaxWidth())
                    Spoken("$percent %", Modifier.politeLive())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    "Stopping here only stops waiting; Bambuddy finishes the slice and keeps the file in its library.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SlicePhase.FAILED, SlicePhase.CLOSED -> Unit
            SlicePhase.READY -> SliceTabBody(tab, state, units, preview, onChoose)
        }
        state.message?.let { Text(it, Modifier.politeLive(), color = MaterialTheme.colorScheme.error) }
    }
}

/** The top bar of the slice page: a back arrow to the review (nothing already chosen is lost) and the printer model being sliced for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SliceTopBar(model: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Heading("Slice for $model", style = MaterialTheme.typography.titleMedium) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the review") }
        },
    )
}

/** The bar at the bottom of the slice page: why Slice is off, a one-line summary, and the one action that fits the phase. */
@Composable
internal fun SliceBottomBar(state: SliceState, onSlice: () -> Unit, onClose: () -> Unit) {
    Surface(tonalElevation = BAR_ELEVATION) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(Dimens.Space.l),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s),
        ) {
            when (state.phase) {
                SlicePhase.READY -> {
                    state.blocker?.let {
                        Text(it, Modifier.politeLive(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spoken(summary(state), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onSlice, enabled = state.canSlice, modifier = Modifier.fillMaxWidth()) { Text("Slice") }
                }
                SlicePhase.SLICING -> OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Stop waiting") }
                SlicePhase.FAILED -> Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                SlicePhase.LOADING, SlicePhase.CLOSED -> OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}

private fun summary(state: SliceState): String = listOfNotNull(
    state.model,
    state.bed?.label ?: "plate not chosen",
    state.process?.name,
    state.scalePercent?.takeIf { it != 100 }?.let { "scale $it %" },
    state.options.changed.takeIf { it > 0 }?.let { "$it option(s) changed" },
).joinToString(" · ")

/** The Options tab (design 40): each switch described in words; the designer's settings only when the file targets this printer. */
@Composable
private fun SliceOptionsPanel(state: SliceState, onChoose: (SliceChoice) -> Unit) {
    val options = state.options
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
        ScaleBlock(state.scalePercent) { onChoose(SliceChoice.Scale(it)) }
        SwitchRow("Let the slicer rotate the model for the best orientation", options.autoOrient) {
            onChoose(SliceChoice.Option(orient = it))
        }
        Text(
            "Off by default: it turns the model, so a model laid flat on purpose would change.",
            style = MaterialTheme.typography.bodySmall,
        )
        SwitchRow("Let the slicer arrange the objects on the plate", options.autoArrange) { onChoose(SliceChoice.Option(arrange = it)) }
        Text("Off by default: it moves objects, which discards a layout the designer chose.", style = MaterialTheme.typography.bodySmall)
        SwitchRow(
            "Use the designer's own settings from the file",
            options.useEmbeddedSettings && state.embeddedAvailable,
            enabled = state.embeddedAvailable,
        ) { onChoose(SliceChoice.Option(embedded = it)) }
        Text(
            if (state.embeddedAvailable) {
                "Keeps the designer's wall count, infill and so on. The process and filament presets are still sent but not used."
            } else {
                "Not available: the file doesn't say it was made for ${state.model}."
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private val BAR_ELEVATION = androidx.compose.ui.unit.Dp(3f)

/** One tab's body: Plate (preview and build plate), Filament (AMS slots and presets), Process (printer and process presets), Options. */
@Composable
private fun SliceTabBody(tab: SliceTab, state: SliceState, units: List<AmsUnit>, preview: ByteArray?, onChoose: (SliceChoice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
        when (tab) {
            SliceTab.PLATE -> {
                PrintPreview(preview)
                BedChoice(state.bed) { onChoose(SliceChoice.Bed(it)) }
                if (state.bedFromMemory) {
                    Text(
                        "Last used: ${state.bed?.label}. Check it is still the plate on the printer.",
                        Modifier.politeLive(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            SliceTab.FILAMENT -> FilamentTab(state, units, onChoose)
            SliceTab.PROCESS -> {
                state.printer?.let { Text("Printer preset: ${it.name}") }
                PrintChoiceField(
                    label = "Process",
                    value = state.process?.name ?: "No preset fits",
                    options = state.processOptions.map { it.name to { onChoose(SliceChoice.Process(it)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "The designer's own process tweaks are not carried over unless you switch on the designer's settings under Options.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SliceTab.OPTIONS -> SliceOptionsPanel(state, onChoose)
        }
    }
}

@Composable
private fun FilamentTab(state: SliceState, units: List<AmsUnit>, onChoose: (SliceChoice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
        state.slots.forEach { slot ->
            if (slot.usedInPlate) {
                Heading("Filament ${slot.slotId}: ${slot.type}, from the AMS", style = MaterialTheme.typography.titleSmall)
                AmsTilePicker(units, state.ams[slot.slotId]?.tray) { tray ->
                    state.loaded.firstOrNull { it.tray == tray }?.let { onChoose(SliceChoice.AmsSlot(slot.slotId, it)) }
                }
                if (state.ams[slot.slotId] == null) Text("Tap a loaded slot.", style = MaterialTheme.typography.bodySmall)
            }
            val recommended = state.recommended(slot)
            val ordered = state.filamentOptions(slot).sortedBy { if (it == recommended) 0 else 1 }.take(MAX_OPTIONS)
            PrintChoiceField(
                label = "Preset for filament ${slot.slotId}${if (slot.usedInPlate) "" else " (not used on this plate)"}",
                value = state.chosen[slot.slotId]?.name ?: "Choose a preset",
                options = ordered.map {
                    (if (it == recommended) "Recommended: ${it.name}" else it.name) to
                        { onChoose(SliceChoice.Filament(slot.slotId, it)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            recommended?.let {
                val same = state.chosen[slot.slotId] == it
                Text(
                    if (same) {
                        "Recommended preset for ${slot.type}: in use."
                    } else {
                        "Recommended preset for ${slot.type}: ${it.name}. A different one is chosen."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text("Each filament uses the colour of its AMS slot (the file's colour if none).", style = MaterialTheme.typography.bodySmall)
    }
}

/** The tab row under the top bar, with a word on any tab that still needs something; at large font sizes it scrolls instead of squeezing. */
@Composable
internal fun SliceTabRow(state: SliceState, selected: SliceTab, onSelect: (SliceTab) -> Unit) {
    AppTabRow(SliceTab.entries.map { SliceTabs.label(it, state) }, selected.ordinal) { onSelect(SliceTab.entries[it]) }
}

private const val PERCENT_FULL = 100f
private const val MAX_OPTIONS = 40

/** Asks which build plate is on the printer: required, nothing pre-selected, because the printer cannot say and a wrong guess wastes a slice. */
@Composable
private fun BedChoice(selected: BedType?, onPick: (BedType) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Dimens.Space.xs)) {
        Heading("Which build plate is on the printer?", style = MaterialTheme.typography.titleSmall)
        BedType.entries.forEach { bed ->
            Row(
                Modifier.fillMaxWidth().heightIn(
                    min = MIN_TARGET,
                ).selectable(selected = selected == bed, role = Role.RadioButton, onClick = {
                    onPick(bed)
                }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == bed, onClick = null)
                Text(
                    bed.label + (bed.alsoKnownAs?.let { " ($it)" } ?: ""),
                    modifier = Modifier.padding(start = Dimens.Space.s),
                )
            }
        }
        Text(
            "The printer doesn't tell the app which plate is fitted, so pick the one on the bed. Bambu Lab's filament guide lists which " +
                "filaments suit which plate.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (selected == null) Text("Choose a plate to enable Slice.", style = MaterialTheme.typography.bodySmall)
    }
}

private val MIN_TARGET = androidx.compose.ui.unit.Dp(48f)

/**
 * The slice page as its own full-screen window, for screens that are not the print flow (the queue job page, design 42). It shows nothing
 * while the slice flow is closed; the print flow itself draws the same pieces inside its own scaffold.
 */
@Composable
fun SlicePageDialog(flow: SliceFlow, state: SliceState, units: List<AmsUnit>, preview: ByteArray?) {
    if (state.phase == SlicePhase.CLOSED) return
    var tab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(SliceTab.PLATE) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = flow::close,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            androidx.compose.material3.Scaffold(
                topBar = {
                    Column {
                        SliceTopBar(state.model, flow::close)
                        if (state.phase == SlicePhase.READY) SliceTabRow(state, tab) { tab = it }
                    }
                },
                bottomBar = { SliceBottomBar(state, flow::start, flow::close) },
            ) { padding ->
                Column(Modifier.padding(padding).padding(Dimens.Space.l).verticalScroll(rememberScrollState())) {
                    SlicePageContent(state, tab, units, preview, flow::choose)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
/** The scale (design 40): a percent field with quick choices. Marked experimental because it rewrites a copy of the file. */
@Composable
private fun ScaleBlock(percent: Int?, onChange: (Int?) -> Unit) {
    var text by rememberSaveable { mutableStateOf((percent ?: DEFAULT_PERCENT).toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
        Heading("Scale (experimental)", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(ScaleRule.parse(it))
            },
            label = { Text("Scale in percent, ${ScaleRule.MIN_PERCENT} to ${ScaleRule.MAX_PERCENT}") },
            suffix = { Text("%") },
            singleLine = true,
            isError = percent == null,
            supportingText = {
                if (percent ==
                    null
                ) {
                    Text("Enter a whole number from ${ScaleRule.MIN_PERCENT} to ${ScaleRule.MAX_PERCENT}.")
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
            ScaleRule.QUICK.forEach { q ->
                FilterChip(
                    selected = percent == q,
                    onClick = {
                        text = q.toString()
                        onChange(q)
                    },
                    label = { Text("$q %") },
                )
            }
        }
        Text(
            "Makes a scaled copy of the file; the original is not changed. The model keeps the centre of its footprint and stays " +
                "on the bed. With several objects each scales about its own centre, so turn on arrange if they might overlap. " +
                "Not tried on many models yet.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private const val DEFAULT_PERCENT = 100
