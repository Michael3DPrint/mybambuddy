package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.barcode.BarcodeOutcome
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.policy.SpoolLocations
import io.github.michael3dprint.mybambuddy.ui.common.AppTabRow
import io.github.michael3dprint.mybambuddy.ui.common.ErrorNotice
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.ListSkeleton
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.common.nameMaxLines
import io.github.michael3dprint.mybambuddy.ui.common.showMessage
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens
import kotlinx.coroutines.delay

private const val AMS_POLL_MS = 10_000L

/**
 * The Filament tab: the spool list, the AMS and drying section, and adding spools by hand, barcode or NFC. Collects the messages
 * of its view models into one snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilamentScreen(
    viewModel: FilamentViewModel,
    amsViewModel: AmsViewModel,
    slotViewModel: SlotAssignmentViewModel,
    dryingViewModel: DryingViewModel,
    nfcViewModel: NfcTagViewModel,
    barcodeViewModel: BarcodeIntakeViewModel,
    weighViewModel: SpoolWeighViewModel,
    slotActions: SlotActions,
    /** A spool to open as soon as the list is loaded (from a tapped low-filament alert), or null. */
    openSpoolId: Int? = null,
    /** Called once [openSpoolId] has been handled, so it is not opened again. */
    onSpoolOpened: () -> Unit = {},
) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val add by viewModel.add.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var section by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Spool?>(null) }
    var weighing by remember { mutableStateOf<Spool?>(null) }
    val canWeigh by weighViewModel.available.collectAsStateWithLifecycle()
    val nfcDialog by nfcViewModel.dialog.collectAsStateWithLifecycle()
    val pendingLoad by slotViewModel.pendingLoad.collectAsStateWithLifecycle()
    val intake by barcodeViewModel.state.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showMessage(it) } }
    val spoolGone = stringResource(R.string.filament_spool_not_found)
    LaunchedEffect(openSpoolId, list) {
        val id = openSpoolId ?: return@LaunchedEffect
        when (val state = list) {
            is SpoolListState.Success -> {
                section = 0
                val found = state.spools.firstOrNull { it.id == id }
                if (found != null) detail = found else snackbar.showMessage(spoolGone)
                onSpoolOpened()
            }
            is SpoolListState.Error -> onSpoolOpened()
            SpoolListState.Loading -> Unit
        }
    }
    LaunchedEffect(Unit) { weighViewModel.checkAvailable() }
    LaunchedEffect(Unit) { weighViewModel.messages.collect { snackbar.showMessage(it) } }
    LaunchedEffect(Unit) { weighViewModel.updated.collect { viewModel.refresh() } }
    LaunchedEffect(Unit) {
        slotViewModel.messages.collect {
            amsViewModel.dismissSlot()
            snackbar.showMessage(it)
        }
    }
    LaunchedEffect(Unit) { dryingViewModel.messages.collect { snackbar.showMessage(it) } }
    // Either one saying something changed means the AMS list is stale.
    LaunchedEffect(Unit) { slotViewModel.changed.collect { amsViewModel.refresh() } }
    LaunchedEffect(Unit) { dryingViewModel.changed.collect { amsViewModel.refresh() } }
    LaunchedEffect(Unit) { nfcViewModel.loadRequests.collect { slotViewModel.beginLoad(it) } }
    // A tag was just linked on the server: reload so the spool shows it at once.
    LaunchedEffect(Unit) { nfcViewModel.linked.collect { viewModel.refresh() } }
    LaunchedEffect(Unit) {
        barcodeViewModel.outcomes.collect { outcome ->
            when (outcome) {
                is BarcodeOutcome.Draft -> viewModel.openAdd(outcome.draft)
                is BarcodeOutcome.Search -> viewModel.onQueryChanged(outcome.query)
                is BarcodeOutcome.NotFound -> snackbar.showMessage(outcome.message)
                is BarcodeOutcome.AskOnlineLookup -> Unit
            }
        }
    }
    // The Spools list shows where each spool is loaded, so the AMS list is read once when that section opens (design 49).
    val amsState by amsViewModel.state.collectAsStateWithLifecycle()
    val locations = remember(amsState) { SpoolLocations.of((amsState as? AmsListState.Success)?.units.orEmpty()) }
    LaunchedEffect(section) { if (section == 0) amsViewModel.refresh() }
    // AMS humidity and drying change while you watch: refresh every 10 s, only while this section is visible.
    LaunchedEffect(section, lifecycleOwner) {
        if (section == 1) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    amsViewModel.refresh()
                    delay(AMS_POLL_MS)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Heading(stringResource(R.string.filament_filament)) },
                actions = {
                    if (nfcViewModel.isAvailable) {
                        // Icon and words together: an NFC symbol alone does not say what it does.
                        TextButton(onClick = nfcViewModel::startRead) {
                            Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Dimens.Space.xs))
                            Text(stringResource(R.string.filament_scan_tag_short))
                        }
                    }
                    IconButton(onClick = { if (section == 0) viewModel.refresh() else amsViewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.filament_refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            if (section == 0) {
                FloatingActionButton(onClick = viewModel::openAdd) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.filament_add_spool))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AppTabRow(listOf(stringResource(R.string.filament_spools), stringResource(R.string.filament_ams_drying)), section) {
                section =
                    it
            }
            if (section == 1) {
                AmsScreen(amsViewModel, slotViewModel, dryingViewModel, slotActions, snackbar)
                return@Column
            }
            Box(Modifier.fillMaxSize()) {
                when (val state = list) {
                    is SpoolListState.Loading -> ListSkeleton()
                    is SpoolListState.Error -> ErrorNotice(
                        state.error,
                        viewModel::refresh,
                        Modifier.align(Alignment.Center).padding(Dimens.Space.xl),
                    )
                    is SpoolListState.Success ->
                        if (state.spools.isEmpty()) {
                            Text(
                                stringResource(R.string.filament_no_spools_yet_tap_to),
                                modifier = Modifier.align(Alignment.Center).padding(Dimens.Space.xl),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(Dimens.Space.l),
                                verticalArrangement = Arrangement.spacedBy(Dimens.Space.m),
                                modifier = Modifier.fillMaxSize(),
                            ) { items(state.spools, key = { it.id }) { spool -> SpoolCard(spool, locations[spool.id]) { detail = spool } } }
                        }
                }
            }
        }
    }

    detail?.let { spool ->
        ModalBottomSheet(
            onDismissRequest = { detail = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LaunchedEffect(spool.id) { viewModel.loadUsage(spool.id) }
            SpoolDetailSheet(
                spool,
                usage,
                nfcAvailable = nfcViewModel.isAvailable,
                canWeigh = canWeigh,
                onWeigh = {
                    detail = null
                    weighing = spool
                },
                onLoadIntoAms = {
                    detail = null
                    slotViewModel.beginLoad(spool)
                },
                onWriteTag = {
                    detail = null
                    nfcViewModel.startWrite(spool)
                },
            )
        }
    }

    weighing?.let { spool ->
        WeighDialog(
            spool = spool,
            onUpdate = {
                weighing = null
                weighViewModel.weigh(spool, it)
            },
            onCancel = { weighing = null },
        )
    }

    NfcDialog(
        state = nfcDialog,
        onClose = nfcViewModel::close,
        onOpenSpool = {
            nfcViewModel.close()
            detail = it
        },
        onCreateFromTag = {
            nfcViewModel.close()
            section = 0
            viewModel.openAdd(it)
        },
        onLoadIntoAms = nfcViewModel::requestLoad,
    )

    pendingLoad?.let { pending ->
        LoadIntoSlotSheet(
            pending = pending,
            onPick = { unit, tray -> slotViewModel.requestLoad(pending.spool, unit, tray.trayId, pending.units) },
            onCancel = slotViewModel::cancelLoad,
        )
    }

    val confirmation by slotViewModel.confirmation.collectAsStateWithLifecycle()
    confirmation?.let {
        ConfirmLoadDialog(
            it,
            onConfirm = {
                slotViewModel.confirmLoad()
                amsViewModel.dismissSlot()
            },
            onCancel = slotViewModel::cancelConfirmation,
        )
    }

    (intake as? IntakeState.AskConsent)?.let { ask ->
        OnlineLookupDialog(
            ean = ask.ean,
            onAnswer = { allow, consent -> barcodeViewModel.answerConsent(ask.ean, allow, consent) },
        )
    }

    if (add.visible) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeAdd,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) { AddSpoolSheet(add, viewModel, onScan = barcodeViewModel::scan, scanning = intake is IntakeState.Busy) }
    }
}

@Composable
private fun SpoolCard(spool: Spool, loadedIn: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(Dimens.Space.l), verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch(spool.colorHex, Modifier.size(40.dp))
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Spoken(
                    if (spool.isLow) "⚠ LOW · ${spool.title}" else spool.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (spool.isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = nameMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
                if (spool.colorName.isNotBlank()) {
                    Text(spool.colorName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                loadedIn?.let { LoadedNote("Loaded: $it") }
                Spacer(Modifier.height(Dimens.Space.s))
                LinearProgressIndicator(
                    progress = { spool.remainingPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.extraSmall),
                )
                Spacer(Modifier.height(Dimens.Space.xs))
                Text(
                    "${spool.remainingGrams} g left of ${spool.labelWeightGrams} g",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A round swatch of a spool colour given as RRGGBB or RRGGBBAA; an unreadable colour shows a neutral grey. */
@Composable
internal fun ColorSwatch(hex: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(parseHexColor(hex) ?: MaterialTheme.colorScheme.surfaceVariant))
}

/** RRGGBB or RRGGBBAA (no '#') -> Color, ignoring alpha. */
internal fun parseHexColor(hex: String): Color? {
    val h = hex.trim().removePrefix("#")
    if (h.length < 6) return null
    return try {
        Color(("#" + h.take(6)).toColorInt())
    } catch (_: IllegalArgumentException) {
        null
    }
}
