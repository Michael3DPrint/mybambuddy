package io.github.michael3dprint.mybambuddy.ui.readiness

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.michael3dprint.mybambuddy.domain.model.PrinterReadiness
import io.github.michael3dprint.mybambuddy.domain.policy.RemoteControlWords
import io.github.michael3dprint.mybambuddy.domain.policy.SectionStatuses
import io.github.michael3dprint.mybambuddy.ui.common.RemoteControlSection
import io.github.michael3dprint.mybambuddy.ui.common.StatusSection

/** The title of the group on the Tools tab. */
const val DIAGNOSTICS_GROUP = "Diagnostics"

/**
 * The Diagnostics section of the Tools tab (design 45): the connection test, Remote control and Slicing as sub-sections of one expandable
 * section, whose header status is the worst of the three. [readiness] is null until the printer's status has been read, which leaves Remote
 * control out.
 */
@Composable
fun DiagnosticsGroup(
    diagnostics: DiagnosticsState?,
    onRun: () -> Unit,
    readiness: PrinterReadiness?,
    slicing: SlicingState,
    printerName: String,
    onCheckSlicing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = (diagnostics as? DiagnosticsState.Done)?.report
    val slicingReadiness = (slicing as? SlicingState.Done)?.readiness?.forPrinter(printerName)
    val parts = listOfNotNull(
        SectionStatuses.diagnostics(diagnostics is DiagnosticsState.Running, diagnostics is DiagnosticsState.Failed, report),
        readiness?.let { RemoteControlWords.status(it.mode) },
        SectionStatuses.slicing(slicingReadiness, slicing is SlicingState.Error),
    )
    StatusSection(DIAGNOSTICS_GROUP, SectionStatuses.group(parts), modifier) {
        DiagnosticsSection(diagnostics, onRun, nested = true)
        readiness?.let { RemoteControlSection(it, nested = true) }
        SlicingSection(slicing, printerName, onCheckSlicing, nested = true)
    }
}
