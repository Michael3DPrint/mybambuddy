package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.policy.SectionStatuses
import io.github.michael3dprint.mybambuddy.ui.common.StatusSection
import io.github.michael3dprint.mybambuddy.ui.filament.AmsDryingControls
import io.github.michael3dprint.mybambuddy.ui.filament.AmsStatusLine
import io.github.michael3dprint.mybambuddy.ui.filament.DryingViewModel
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** The AMS part of the Tools tab (design 45): one expandable section per AMS unit of this printer, with the same status line and drying controls as the popup. */
@Composable
fun AmsToolsSection(units: List<AmsUnit>, dryingViewModel: DryingViewModel, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
        if (units.isEmpty()) {
            Text("This printer has no AMS.")
        } else {
            units.forEach { unit ->
                StatusSection(unit.name, SectionStatuses.ams(unit)) {
                    AmsStatusLine(unit)
                    AmsDryingControls(unit, dryingViewModel)
                }
            }
        }
    }
}
