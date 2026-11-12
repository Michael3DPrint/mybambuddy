package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.PrintActivity
import io.github.michael3dprint.mybambuddy.domain.model.PrinterTool
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.policy.AmsTrayAddress
import io.github.michael3dprint.mybambuddy.domain.policy.SlotFillRule
import io.github.michael3dprint.mybambuddy.domain.policy.ToolAvailability
import io.github.michael3dprint.mybambuddy.ui.common.ConfirmDialog
import io.github.michael3dprint.mybambuddy.ui.common.EmptyState
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.common.nameMaxLines
import io.github.michael3dprint.mybambuddy.ui.common.showMessage
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * Everything that opens when a slot is tapped, and what its buttons lead to (design 33): the slot sheet, the confirmation for a load or
 * unload, the tag-scan wait, and the messages. A pick or a scanned tag goes to [SlotAssignmentViewModel], which runs the usual checks and
 * the confirmation. Used by the Filament tab and by each printer's page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotPickerHost(viewModel: AmsViewModel, slotViewModel: SlotAssignmentViewModel, actions: SlotActions, snackbar: SnackbarHostState) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val target by viewModel.slotTarget.collectAsStateWithLifecycle()
    val choices by viewModel.spoolChoices.collectAsStateWithLifecycle()
    val spoolsById by viewModel.spoolsById.collectAsStateWithLifecycle()
    val units = (state as? AmsListState.Success)?.units.orEmpty()
    // The message bar sits beneath the sheet, so a message closes the sheet first.
    LaunchedEffect(Unit) {
        actions.feed.messages.collect {
            viewModel.dismissSlot()
            snackbar.showMessage(it)
        }
    }
    LaunchedEffect(Unit) { actions.feed.changed.collect { viewModel.refresh() } }
    LaunchedEffect(Unit) {
        actions.sync.messages.collect {
            viewModel.dismissSlot()
            snackbar.showMessage(it)
        }
    }
    LaunchedEffect(Unit) { actions.sync.changed.collect { viewModel.refresh() } }
    LaunchedEffect(units) {
        actions.scan.found.collect { found -> slotViewModel.requestLoad(found.spool, found.target.unit, found.target.trayId, units) }
    }

    target?.let { slot ->
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSlot,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SlotSheet(
                slot = slot,
                units = units,
                spools = choices,
                spoolsById = spoolsById,
                nfcAvailable = actions.scan.isAvailable,
                // The sheet stays open under the dialogs these start, so Cancel shows it again (design 33).
                onFeed = { action -> actions.feed.request(action, slot.unit, slot.trayId) },
                onScan = { actions.scan.scan(slot) },
                onUpdateFromPrinter = {
                    slot.unit.trays.firstOrNull { it.trayId == slot.trayId }?.let {
                        actions.sync.prepare(it, spoolsById.values, slot.unit.capabilities.activity == PrintActivity.BUSY)
                    }
                },
                onPick = { slotViewModel.requestLoad(it, slot.unit, slot.trayId, units) },
                onRemoveRecord = { spoolId ->
                    viewModel.dismissSlot()
                    slotViewModel.unload(slot.unit, slot.trayId, spoolId)
                },
            )
        }
    }
    FeedConfirmation(actions.feed, onConfirmed = viewModel::dismissSlot)
    SyncConfirmation(actions.sync, onConfirmed = viewModel::dismissSlot)
    ScanWait(actions.scan)
}

@Composable
private fun SlotSheet(
    slot: SlotTarget,
    units: List<AmsUnit>,
    spools: List<Spool>,
    spoolsById: Map<Int, Spool>,
    nfcAvailable: Boolean,
    onFeed: (FeedAction) -> Unit,
    onScan: () -> Unit,
    onUpdateFromPrinter: () -> Unit,
    onPick: (Spool) -> Unit,
    onRemoveRecord: (Int) -> Unit,
) {
    val tray = slot.unit.trays.firstOrNull { it.trayId == slot.trayId }
    val feedStatus = ToolAvailability.statusOf(PrinterTool.FEED_FILAMENT, slot.unit.capabilities, slot.unit.printerName)
    val canFeed = feedStatus.isEnabled && AmsTrayAddress.forFeed(slot.unit.amsId, slot.unit.isHt, slot.trayId) != null
    var query by remember { mutableStateOf("") }
    val elsewhere = remember(units, slot) { whereLoaded(units, slot) }
    val shown = remember(spools, query, tray) { filterAndRank(spools, query, tray?.material.orEmpty()) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Dimens.Space.l, vertical = Dimens.Space.s),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space.s),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item { Heading("${slot.unit.name} · Slot ${slot.trayId + 1}", style = MaterialTheme.typography.titleLarge) }
        item { CurrentFilament(tray, tray?.loadedSpoolId?.let(spoolsById::get)) }
        item { SectionLabel("On the printer") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
                OutlinedButton(onClick = {
                    onFeed(FeedAction.LOAD)
                }, enabled = canFeed, modifier = Modifier.weight(1f)) { Text("Load filament") }
                OutlinedButton(onClick = {
                    onFeed(FeedAction.UNLOAD)
                }, enabled = canFeed, modifier = Modifier.weight(1f)) { Text("Unload filament") }
            }
        }
        item {
            val note = if (canFeed) {
                "Moves the filament in or out of the printer. You are asked first."
            } else {
                feedStatus.reason
                    ?: "Not available for this slot."
            }
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = if (canFeed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        item { SectionLabel("In your inventory") }
        if (nfcAvailable) {
            item {
                FilledTonalButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(Dimens.Space.s))
                    Text("Scan NFC tag to load a spool")
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search your spools") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (spools.isEmpty()) {
            item {
                EmptyState(
                    Icons.Default.Inventory2,
                    "No spools yet",
                    "Add one in the Spools section, then load it here.",
                    fillScreen = false,
                )
            }
        } else if (shown.isEmpty()) {
            item { Text("No spool matches \"$query\".", style = MaterialTheme.typography.bodyMedium) }
        }
        items(shown, key = { it.id }) { spool -> SpoolRow(spool, elsewhere[spool.id], onClick = { onPick(spool) }) }
        if (tray?.trayUuid?.isNotBlank() == true) {
            item {
                Column(Modifier.padding(top = Dimens.Space.m)) {
                    OutlinedButton(onClick = onUpdateFromPrinter, modifier = Modifier.fillMaxWidth()) {
                        Text("Update inventory from printer")
                    }
                    Text(
                        "Sets this Bambu spool's remaining amount in your inventory to what the printer reports. You see the numbers first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        tray?.loadedSpoolId?.let { id ->
            item {
                Column(Modifier.padding(top = Dimens.Space.m)) {
                    TextButton(onClick = { onRemoveRecord(id) }) { Text("Remove the record of this slot's spool") }
                    Text(
                        "Only clears the link in Bambuddy. It does not move any filament.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(Dimens.Space.xl)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Heading(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Dimens.Space.m),
    )
}

@Composable
private fun CurrentFilament(tray: AmsTray?, linked: Spool?) {
    val fill = tray?.let { SlotFillRule.estimate(it, linked) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
        ColorSwatch(if (tray == null || tray.isEmpty) "" else tray.colorHex, Modifier.size(28.dp))
        Spoken(
            if (tray == null ||
                tray.isEmpty
            ) {
                "Empty slot"
            } else {
                listOfNotNull(tray.label, linked?.let { "spool: ${it.title}" }, fill?.let { "~${it.grams} g left" }).joinToString(" · ")
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SpoolRow(spool: Spool, loadedIn: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = Dimens.Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorSwatch(spool.colorHex, Modifier.size(28.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(spool.title, maxLines = nameMaxLines(), overflow = TextOverflow.Ellipsis)
            Spoken(
                listOfNotNull("${spool.remainingGrams} g left", spool.colorName.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        loadedIn?.let { LoadedNote(it, loaded = true) }
    }
}

@Composable
private fun FeedConfirmation(feed: AmsFilamentViewModel, onConfirmed: () -> Unit) {
    val pending by feed.pending.collectAsStateWithLifecycle()
    pending?.let { p ->
        val load = p.action == FeedAction.LOAD
        ConfirmDialog(
            title = if (load) "Load filament?" else "Unload filament?",
            confirmLabel = if (load) "Load" else "Unload",
            onConfirm = {
                feed.confirm()
                onConfirmed()
            },
            onCancel = feed::cancel,
        ) {
            val what = if (load) {
                "This feeds filament from ${p.slotLabel} into the printer."
            } else {
                "This pulls the filament from ${p.slotLabel} back out."
            }
            Text("$what The printer may heat the nozzle and move filament.")
        }
    }
}

@Composable
private fun SyncConfirmation(sync: SlotWeightSyncViewModel, onConfirmed: () -> Unit) {
    val pending by sync.pending.collectAsStateWithLifecycle()
    pending?.let { plan ->
        ConfirmDialog(
            title = "Update the inventory?",
            confirmLabel = "Update",
            onConfirm = {
                sync.confirm()
                onConfirmed()
            },
            onCancel = sync::cancel,
        ) {
            Text(
                "${plan.spool.title}: your inventory says about ${plan.currentRemainingGrams} g left. " +
                    "The printer reports ${plan.percent}%, about ${plan.newRemainingGrams} g. " +
                    "The inventory will be set to about ${plan.newRemainingGrams} g. " +
                    "The printer's percent is coarse (roughly 10 g steps on a 1 kg spool).",
            )
        }
    }
}

@Composable
private fun ScanWait(scan: SlotNfcScanViewModel) {
    val state by scan.state.collectAsStateWithLifecycle()
    when (val s = state) {
        SlotScanState.Idle -> Unit
        is SlotScanState.Waiting -> ConfirmDialog(
            title = "Scan NFC tag",
            confirmLabel = null,
            onConfirm = {},
            onCancel = scan::cancel,
        ) {
            Text("Hold the spool's tag against the back of the phone to load it into ${s.target.unit.name} slot ${s.target.trayId + 1}.")
        }
        is SlotScanState.Problem -> ConfirmDialog(
            title = "Couldn't use that tag",
            confirmLabel = null,
            cancelLabel = "OK",
            onConfirm = {},
            onCancel = scan::cancel,
        ) {
            Text(s.message)
        }
    }
}

/** Where each spool is loaded, as short text: "In this slot" for the tapped slot, "In AMS 1 slot 2" for the others (design 49). */
internal fun whereLoaded(units: List<AmsUnit>, slot: SlotTarget): Map<Int, String> {
    val result = mutableMapOf<Int, String>()
    units.forEach { unit ->
        unit.trays.forEach { tray ->
            val id = tray.loadedSpoolId ?: return@forEach
            val here = unit.printerId == slot.unit.printerId && unit.amsId == slot.unit.amsId && tray.trayId == slot.trayId
            result[id] = if (here) "In this slot" else "In ${unit.name} slot ${tray.trayId + 1}"
        }
    }
    return result
}

/** The spools that match [query] (title, colour name or material), those of the slot's own material first, then by title. */
internal fun filterAndRank(spools: List<Spool>, query: String, slotMaterial: String): List<Spool> {
    val q = query.trim()
    val matching = if (q.isEmpty()) {
        spools
    } else {
        spools.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.colorName.contains(q, ignoreCase = true) ||
                it.material.contains(q, ignoreCase = true)
        }
    }
    return matching.sortedWith(
        compareByDescending<Spool> {
            slotMaterial.isNotBlank() && it.material.equals(slotMaterial, ignoreCase = true)
        }.thenBy { it.title.lowercase() },
    )
}
