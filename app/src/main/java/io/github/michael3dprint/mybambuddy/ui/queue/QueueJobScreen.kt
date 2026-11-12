package io.github.michael3dprint.mybambuddy.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michael3dprint.mybambuddy.domain.policy.JobEdit
import io.github.michael3dprint.mybambuddy.domain.print.PrintEstimate
import io.github.michael3dprint.mybambuddy.ui.common.AppTabRow
import io.github.michael3dprint.mybambuddy.ui.common.ConfirmDialog
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.common.politeLive
import io.github.michael3dprint.mybambuddy.ui.print.SlicePageDialog
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

private enum class JobTab(val title: String) { SETTINGS("Settings"), SLICING("Slicing") }

/**
 * The page of one queue job (design 42): picture, status and estimate, then Settings and Slicing tabs and a Save bar. A job that is not
 * waiting is read-only. [onClose] is called when the page is closed or the job was replaced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueJobScreen(viewModel: QueueJobViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sliceState by viewModel.slice.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(JobTab.SETTINGS) }
    var confirmSave by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    // Leaving with unsaved changes asks first (design 42); with nothing changed it just closes.
    val leave = { if (state.editable && !state.change.isEmpty) confirmDiscard = true else viewModel.close() }
    LaunchedEffect(state.closed) { if (state.closed) onClose() }

    Dialog(onDismissRequest = leave, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(
                            title = { Heading(state.job?.name ?: "Job", style = MaterialTheme.typography.titleMedium, maxLines = 2) },
                            navigationIcon = {
                                IconButton(onClick = leave) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the queue")
                                }
                            },
                        )
                        AppTabRow(JobTab.entries.map { it.title }, tab.ordinal) { tab = JobTab.entries[it] }
                    }
                },
                bottomBar = { if (state.editable) SaveBar(state, onSave = { confirmSave = true }) },
            ) { padding ->
                Column(
                    Modifier.padding(padding).padding(Dimens.Space.l).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space.m),
                ) {
                    when {
                        state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        state.error != null -> Text(state.error.orEmpty(), Modifier.politeLive(), color = MaterialTheme.colorScheme.error)
                        else -> {
                            JobHeader(state)
                            state.message?.let { Text(it, Modifier.politeLive(), color = MaterialTheme.colorScheme.error) }
                            if (tab == JobTab.SETTINGS) {
                                JobSettingsTab(state, viewModel::edit)
                            } else {
                                SlicingTab(state, onReslice = viewModel::reslice)
                            }
                        }
                    }
                }
            }
        }
    }
    SlicePageDialog(viewModel.slice, sliceState, state.units, state.preview)
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard your changes?",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                viewModel.close()
            },
            onCancel = { confirmDiscard = false },
            cancelLabel = "Keep editing",
        ) { Text("You changed this job but haven't saved it.") }
    }
    if (confirmSave) {
        val names = state.printers.associate { it.id to it.name }
        ConfirmDialog(
            title = "Save these changes?",
            confirmLabel = "Save",
            onConfirm = {
                confirmSave = false
                viewModel.save()
            },
            onCancel = { confirmSave = false },
        ) {
            JobEdit.lines(state.change) { names[it.toString()] ?: "Printer $it" }.forEach { Text(it) }
        }
    }
    state.replacement?.let { new ->
        ConfirmDialog(
            title = "Replace this job with the new slice?",
            confirmLabel = "Replace job",
            onConfirm = viewModel::replace,
            onCancel = viewModel::dismissReplacement,
            cancelLabel = "Keep the old job",
            confirmEnabled = state.busy == null,
        ) {
            new.plates.firstOrNull()?.let { Spoken(PrintEstimate.line(it.seconds, it.grams)) }
            Text("The new job takes this job's place in the queue and waits there until you start it. This job is then removed.")
        }
    }
}

@Composable
private fun SaveBar(state: QueueJobState, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(Dimens.Space.l),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s),
        ) {
            state.busy?.let { Text(it, Modifier.politeLive(), style = MaterialTheme.typography.bodySmall) }
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) { Text("Save changes") }
            if (state.change.isEmpty) {
                Text("Nothing changed yet.", style = MaterialTheme.typography.bodySmall)
            } else if (!state.canSave && state.busy == null) {
                Text(
                    "Can't save yet: fix the problems listed on the Settings tab.",
                    Modifier.politeLive(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun JobHeader(state: QueueJobState) {
    val job = state.job ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
        QueueThumb(state.preview, Modifier.size(96.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.Space.xs)) {
            Spoken(statusLabel(job), style = MaterialTheme.typography.labelLarge)
            PrintEstimate.summary(job.seconds, job.grams, job.filamentType)?.let { Spoken("$it (slicer estimate)") }
            Text(job.printerName, style = MaterialTheme.typography.bodySmall)
            if (!state.editable) Text("This job is not waiting, so it can't be changed.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SlicingTab(state: QueueJobState, onReslice: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
        Heading("Slice this file again", style = MaterialTheme.typography.titleSmall)
        Text("Choose a different build plate, filament, process or options, and slice the file again.")
        Text(
            "The new slice becomes a new job in this job's place in the queue and replaces this one. It waits there until you start it.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onReslice, enabled = state.editable && state.file != null, modifier = Modifier.fillMaxWidth()) {
            Text("Re-slice")
        }
        if (state.file == null) Text("This job has no library file to slice again.", style = MaterialTheme.typography.bodySmall)
    }
}
