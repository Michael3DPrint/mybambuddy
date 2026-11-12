package io.github.michael3dprint.mybambuddy.ui.print

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michael3dprint.mybambuddy.domain.print.FindingLevel
import io.github.michael3dprint.mybambuddy.domain.print.PrintEstimate
import io.github.michael3dprint.mybambuddy.platform.print.UriPrintSource
import io.github.michael3dprint.mybambuddy.ui.common.ConfirmDialog
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.common.politeLive
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * The print-from-the-phone flow as a full-screen window over the app (designs 38 to 40), with the slice page in place of the review while
 * slicing. [onClose] is called when the flow ends or is backed out of.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintFlowScreen(viewModel: PrintFlowViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onFilePicked(UriPrintSource.from(resolver, uri))
    }
    var linkMode by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var sliceTab by rememberSaveable { mutableStateOf(SliceTab.PLATE) }
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    val sliceState by viewModel.slice.state.collectAsStateWithLifecycle()
    val sliceOpen = sliceState.phase != SlicePhase.CLOSED

    Dialog(
        onDismissRequest = if (sliceOpen) viewModel.slice::close else viewModel::cancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                topBar = {
                    if (sliceOpen) {
                        Column {
                            SliceTopBar(sliceState.model, viewModel.slice::close)
                            if (sliceState.phase == SlicePhase.READY) SliceTabRow(sliceState, sliceTab) { sliceTab = it }
                        }
                    } else {
                        TopAppBar(
                            title = { Heading("Print", style = MaterialTheme.typography.titleMedium) },
                            actions = {
                                IconButton(onClick = viewModel::cancel) {
                                    Icon(Icons.Default.Close, contentDescription = "Close the print flow")
                                }
                            },
                        )
                    }
                },
                bottomBar = { if (sliceOpen) SliceBottomBar(sliceState, viewModel.slice::start, viewModel.slice::close) },
            ) { padding ->
                Column(Modifier.padding(padding).padding(Dimens.Space.l).verticalScroll(rememberScrollState())) {
                    if (sliceOpen) {
                        SlicePageContent(sliceState, sliceTab, state.units, state.preview, viewModel.slice::choose)
                    } else {
                        when (state.step) {
                            PrintStep.SOURCE ->
                                if (linkMode) {
                                    PrintLinkStep(state, viewModel::lookUpLink) { viewModel.importInstance(it) }
                                } else {
                                    PrintSourceStep(state, onPickFile = {
                                        picker.launch(arrayOf("*/*"))
                                    }, onMakerWorld = { linkMode = true })
                                }
                            PrintStep.LINK -> PrintLinkStep(state, viewModel::lookUpLink) { viewModel.importInstance(it) }
                            PrintStep.WORKING -> PrintWorkingStep(state, onCancel = viewModel::cancel)
                            PrintStep.REVIEW -> PrintReviewStep(
                                state,
                                viewModel::select,
                                onAdd = viewModel::addToQueue,
                                onStart = { confirming = true },
                                onSlice = {
                                    state.file?.let { f -> state.printer?.let { p -> viewModel.slice.open(f, p.model, state.slots) } }
                                },
                            )
                            PrintStep.DONE -> PrintDoneStep(
                                state.message.orEmpty(),
                                state.watching,
                                state.problem,
                                onClose = viewModel::cancel,
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirming && state.step == PrintStep.REVIEW) {
        StartConfirmation(state, onCancel = { confirming = false }, onConfirm = {
            confirming = false
            viewModel.start(skipFilamentCheck = false)
        })
    }
    state.shortfall?.let { text ->
        ConfirmDialog(
            title = "Bambuddy reports a filament problem",
            confirmLabel = "Print anyway",
            onConfirm = { viewModel.start(skipFilamentCheck = true) },
            onCancel = viewModel::cancel,
            cancelLabel = "Don't print",
        ) { Text("$text\n\nThe job is staged in the queue and has not started.") }
    }
}

@Composable
private fun PrintDoneStep(message: String, watching: Boolean, problem: Boolean, onClose: () -> Unit) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.Space.m)) {
        Heading(if (problem) "It won't print" else "Done", style = MaterialTheme.typography.titleLarge)
        Text(message, Modifier.politeLive(), color = if (problem) MaterialTheme.colorScheme.error else Color.Unspecified)
        if (watching) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = onClose) { Text("Close") }
    }
}

/** The last look before a print starts: everything the printer will be told, and a tick for any warning. */
@Composable
private fun StartConfirmation(state: PrintFlowState, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val file = state.file ?: return
    val plate = state.plate ?: return
    val warnings = state.findings.filter { it.level == FindingLevel.WARNING }
    var read by rememberSaveable { mutableStateOf(false) }
    ConfirmDialog(
        title = "Start this print on ${state.printer?.name}?",
        confirmLabel = "Start print",
        onConfirm = onConfirm,
        onCancel = onCancel,
        confirmEnabled = warnings.isEmpty() || read,
    ) {
        Text("${file.name}, plate ${plate.index}")
        Spoken(PrintEstimate.line(plate.seconds, plate.grams))
        plate.filaments.forEach { need ->
            Text("Filament ${need.slotId} (${need.type}) from ${state.chosen[need.slotId]?.label ?: "no slot"}")
        }
        Text("The printer starts when it is idle. Nothing is sent until you tap Start print.")
        if (warnings.isNotEmpty()) {
            FindingsList(warnings)
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = read, onCheckedChange = { read = it })
                Text("I've read these warnings")
            }
        }
    }
}
