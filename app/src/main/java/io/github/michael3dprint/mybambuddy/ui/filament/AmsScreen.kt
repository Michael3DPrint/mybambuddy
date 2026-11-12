package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.ui.common.EmptyState
import io.github.michael3dprint.mybambuddy.ui.common.ErrorNotice
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.ListSkeleton
import io.github.michael3dprint.mybambuddy.ui.common.ToolsBanner
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** The "AMS & Drying" section of the Filament tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmsScreen(
    viewModel: AmsViewModel,
    slotViewModel: SlotAssignmentViewModel,
    dryingViewModel: DryingViewModel,
    actions: SlotActions,
    snackbar: SnackbarHostState,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spoolsById by viewModel.spoolsById.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state,
            // Fades between loading, error and content; new data inside the same kind updates in place with no animation.
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "ams",
        ) { s ->
            when (s) {
                is AmsListState.Loading -> ListSkeleton()
                is AmsListState.Error -> ErrorNotice(s.error, viewModel::refresh, Modifier.align(Alignment.Center).padding(Dimens.Space.xl))
                is AmsListState.Success ->
                    if (s.units.isEmpty()) {
                        EmptyState(
                            Icons.Default.Print,
                            "No AMS found",
                            "None of your printers report an AMS unit.",
                            Modifier.align(Alignment.Center),
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(Dimens.Space.l),
                            verticalArrangement = Arrangement.spacedBy(Dimens.Space.l),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Shown only when Developer LAN mode is off on a printer that has an AMS.
                            val limitedPrinters = s.units.filter {
                                it.capabilities.developerMode == false && it.capabilities.connected != false
                            }
                                .map { it.printerName }.distinct()
                            if (limitedPrinters.isNotEmpty()) item { ToolsBanner(limitedPrinters) }
                            items(s.units, key = { "${it.printerId}:${it.amsId}" }) { unit ->
                                AmsUnitCard(
                                    unit = unit,
                                    spoolsById = spoolsById,
                                    dryingViewModel = dryingViewModel,
                                    onSlot = { viewModel.chooseSlot(unit, it.trayId) },
                                )
                            }
                        }
                    }
            }
        }
    }

    SlotPickerHost(viewModel, slotViewModel, actions, snackbar)
}

@Composable
private fun AmsUnitCard(unit: AmsUnit, spoolsById: Map<Int, Spool>, dryingViewModel: DryingViewModel, onSlot: (AmsTray) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(Dimens.Space.l), verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
            Heading(unit.printerName, style = MaterialTheme.typography.titleMedium)
            AmsStatusLine(unit)
            AmsSlotRow(unit, spoolsById, onSlot = onSlot)
            AmsDryingControls(unit, dryingViewModel)
        }
    }
}
