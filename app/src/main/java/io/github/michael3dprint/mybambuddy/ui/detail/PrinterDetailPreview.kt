package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.model.Temperatures
import io.github.michael3dprint.mybambuddy.ui.theme.MyBambuddyTheme

/** Android Studio preview of the printer detail screen with sample data. */
@Preview(showBackground = true)
@Composable
fun PrinterDetailScreenPreview() {
    val samplePrinter = Printer(
        id = "printer_001",
        name = "Bambu X1-Carbon",
        model = "X1C",
        state = PrinterState.PRINTING,
        progress = 68,
        remainingTimeSeconds = 4320,
        remainingTimeFormatted = "1h 12m left",
        estimatedFinishTime = "4:15 PM",
        currentLayer = 150,
        totalLayers = 220,
        hasHmsWarning = true,
        hmsCount = 1,
        hmsMessages = listOf("HMS_0300_0100: Filament runout detected"),
        snapshotUrl = "",
        printName = "Benchy_3D_v2.gcode",
        temperatures = Temperatures(
            nozzle = 220.5f,
            bed = 60.0f,
            chamber = 35.0f,
        ),
    )
    MyBambuddyTheme {
        PrinterDetailScreenContent(
            uiState = PrinterDetailUiState.Success(printer = samplePrinter),
        )
    }
}
