package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * The popup for one AMS unit on the printer page (design 45): its humidity, temperature and drying state, and the drying panel with the same
 * gate, reason and confirmation as the Filament tab and Tools.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmsUnitSheet(unit: AmsUnit, dryingViewModel: DryingViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.navigationBarsPadding().padding(
                horizontal = Dimens.Space.l,
            ).padding(bottom = Dimens.Space.l).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.m),
        ) {
            Heading("${unit.printerName} · ${unit.name}", style = MaterialTheme.typography.titleLarge)
            AmsStatusLine(unit)
            AmsDryingControls(unit, dryingViewModel)
        }
    }
}
