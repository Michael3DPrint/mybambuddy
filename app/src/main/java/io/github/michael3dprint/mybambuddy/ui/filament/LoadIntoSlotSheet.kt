package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.ui.common.EmptyState
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** "Load <spool> into which slot?" Opened from a spool's detail sheet or after scanning its NFC tag. Confirmation happens next. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadIntoSlotSheet(pending: PendingLoad, onPick: (AmsUnit, AmsTray) -> Unit, onCancel: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(Dimens.Space.l), verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
            Heading("Load ${pending.spool.title}", style = MaterialTheme.typography.titleLarge)
            when {
                pending.loading -> CircularProgressIndicator(Modifier.size(24.dp))
                pending.units.isEmpty() -> EmptyState(
                    Icons.Default.Print,
                    "No AMS found",
                    "None of your printers report an AMS unit.",
                    fillScreen = false,
                )
                else -> pending.units.forEach { unit ->
                    Spoken("${unit.printerName} · ${unit.name}", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
                        unit.trays.forEach { tray ->
                            Column(
                                Modifier.weight(1f).clickable(role = Role.Button) { onPick(unit, tray) }.padding(vertical = Dimens.Space.s),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                ColorSwatch(tray.colorHex, Modifier.size(36.dp))
                                Text("Slot ${tray.trayId + 1}", style = MaterialTheme.typography.labelMedium)
                                Text(tray.label, style = MaterialTheme.typography.labelSmall)
                                if (tray.loadedSpoolId !=
                                    null
                                ) {
                                    Text(stringResource(R.string.filament_in_use), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimens.Space.l))
        }
    }
}
