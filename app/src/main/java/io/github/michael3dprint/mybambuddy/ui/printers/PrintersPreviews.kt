package io.github.michael3dprint.mybambuddy.ui.printers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.ui.theme.MyBambuddyTheme

/** Android Studio preview of the Printers tab in the light theme. */
@Preview(showBackground = true)
@Composable
fun PrintersScreenPreviewLight() {
    val samplePrinters = listOf(
        Printer(
            id = "p1",
            name = "Bambu X1 Carbon",
            model = "X1C",
            state = PrinterState.PRINTING,
            progress = 68,
            remainingTimeSeconds = 4320,
            remainingTimeFormatted = "1h 12m left",
            currentLayer = 180,
            totalLayers = 265,
            hasHmsWarning = true,
            hmsCount = 1,
            snapshotUrl = "",
        ),
        Printer(
            id = "p2",
            name = "Bambu P1S Lab",
            model = "P1S",
            state = PrinterState.IDLE,
            progress = 0,
            remainingTimeSeconds = 0,
            remainingTimeFormatted = "0m left",
            currentLayer = 0,
            totalLayers = 0,
            hasHmsWarning = false,
            hmsCount = 0,
            snapshotUrl = "",
        ),
    )
    MyBambuddyTheme {
        PrintersScreenContent(
            uiState = PrintersUiState.Success(printers = samplePrinters),
        )
    }
}

/** Android Studio preview of the Printers tab in the dark theme. */
@Preview(showBackground = true)
@Composable
fun PrintersScreenPreviewDark() {
    val samplePrinters = listOf(
        Printer(
            id = "p1",
            name = "Bambu X1 Carbon",
            model = "X1C",
            state = PrinterState.PRINTING,
            progress = 45,
            remainingTimeSeconds = 2700,
            remainingTimeFormatted = "45m left",
            currentLayer = 100,
            totalLayers = 220,
            hasHmsWarning = true,
            hmsCount = 2,
            snapshotUrl = "",
        ),
    )
    MyBambuddyTheme(darkTheme = true) {
        PrintersScreenContent(
            uiState = PrintersUiState.Success(printers = samplePrinters),
        )
    }
}
