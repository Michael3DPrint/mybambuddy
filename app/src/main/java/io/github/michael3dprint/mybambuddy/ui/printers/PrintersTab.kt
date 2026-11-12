package io.github.michael3dprint.mybambuddy.ui.printers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import coil.ImageLoader
import io.github.michael3dprint.mybambuddy.R

/** The share of the width the printer list takes in the two-pane layout; the selected printer takes the rest. */
private const val LIST_PANE_WEIGHT = 0.4f

/**
 * The Printers tab. On a phone it is the list alone. On a wide screen (design 28) the list sits beside [detail], which draws the selected
 * printer, or a hint when none is selected.
 */
@Composable
fun PrintersTab(
    wide: Boolean,
    viewModel: PrintersViewModel,
    imageLoader: ImageLoader?,
    selectedPrinterId: String?,
    onPrinterClick: (String) -> Unit,
    onOpenTools: (String) -> Unit,
    onOpenSettings: () -> Unit,
    detail: @Composable (printerId: String) -> Unit,
) {
    Row {
        Box(Modifier.weight(LIST_PANE_WEIGHT)) {
            PrintersScreen(
                viewModel = viewModel,
                imageLoader = imageLoader,
                onPrinterClick = onPrinterClick,
                onOpenTools = onOpenTools,
                onOpenSettings = onOpenSettings,
            )
        }
        if (wide) {
            Box(Modifier.weight(1f - LIST_PANE_WEIGHT)) {
                if (selectedPrinterId != null) {
                    detail(selectedPrinterId)
                } else {
                    Text(stringResource(R.string.tablet_select_printer), Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
