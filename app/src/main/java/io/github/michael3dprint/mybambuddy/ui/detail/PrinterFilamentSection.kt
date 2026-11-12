package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.policy.PrinterFilter
import io.github.michael3dprint.mybambuddy.ui.common.ErrorNotice
import io.github.michael3dprint.mybambuddy.ui.common.showMessage
import io.github.michael3dprint.mybambuddy.ui.filament.AmsListState
import io.github.michael3dprint.mybambuddy.ui.filament.AmsSlotRow
import io.github.michael3dprint.mybambuddy.ui.filament.AmsUnitSheet
import io.github.michael3dprint.mybambuddy.ui.filament.AmsViewModel
import io.github.michael3dprint.mybambuddy.ui.filament.ConfirmLoadDialog
import io.github.michael3dprint.mybambuddy.ui.filament.DryingViewModel
import io.github.michael3dprint.mybambuddy.ui.filament.SlotActions
import io.github.michael3dprint.mybambuddy.ui.filament.SlotAssignmentViewModel
import io.github.michael3dprint.mybambuddy.ui.filament.SlotPickerHost
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * What is loaded in this printer's AMS, with a way to change a slot from your inventory (design 32). Reading is a plain GET; a change goes
 * through the same picker, checks and confirmation as the Filament tab, so nothing is sent without a confirmed pick. Messages from the
 * change are shown through [snackbar].
 */
@Composable
fun PrinterFilamentSection(
    printerId: Int,
    amsViewModel: AmsViewModel,
    slotViewModel: SlotAssignmentViewModel,
    actions: SlotActions,
    snackbar: SnackbarHostState,
    dryingViewModel: DryingViewModel,
) {
    var sheetUnit by rememberSaveable { mutableStateOf<String?>(null) }
    val state by amsViewModel.state.collectAsStateWithLifecycle()
    val spoolsById by amsViewModel.spoolsById.collectAsStateWithLifecycle()
    val confirmation by slotViewModel.confirmation.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { amsViewModel.refresh() }
    LaunchedEffect(Unit) {
        slotViewModel.messages.collect {
            amsViewModel.dismissSlot()
            snackbar.showMessage(it)
        }
    }
    LaunchedEffect(Unit) { slotViewModel.changed.collect { amsViewModel.refresh() } }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
        when (val s = state) {
            is AmsListState.Loading -> Text(stringResource(R.string.printer_filament_loading), style = MaterialTheme.typography.bodyMedium)
            is AmsListState.Error -> ErrorNotice(s.error, amsViewModel::refresh)
            is AmsListState.Success -> {
                val units = PrinterFilter.ams(s.units, printerId)
                if (units.isEmpty()) {
                    Text(stringResource(R.string.printer_no_ams), style = MaterialTheme.typography.bodyMedium)
                } else {
                    units.forEach { unit ->
                        AmsSlotRow(unit, spoolsById, onUnit = { sheetUnit = "${unit.printerId}:${unit.amsId}" }) { tray ->
                            amsViewModel.chooseSlot(unit, tray.trayId)
                        }
                    }
                    units.firstOrNull { "${it.printerId}:${it.amsId}" == sheetUnit }?.let { open ->
                        AmsUnitSheet(open, dryingViewModel, onDismiss = { sheetUnit = null })
                    }
                }
            }
        }
    }
    SlotPickerHost(amsViewModel, slotViewModel, actions, snackbar)
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
}
