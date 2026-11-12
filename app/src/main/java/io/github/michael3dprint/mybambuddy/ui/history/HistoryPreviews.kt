package io.github.michael3dprint.mybambuddy.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import io.github.michael3dprint.mybambuddy.domain.model.HistoryItem
import io.github.michael3dprint.mybambuddy.domain.model.HistoryStatus
import io.github.michael3dprint.mybambuddy.ui.theme.MyBambuddyTheme

/** Android Studio preview of the history screen with sample data. */
@Preview(showBackground = true)
@Composable
fun HistoryContentPreview() {
    MyBambuddyTheme {
        HistoryContent(
            uiState = HistoryUiState(
                isLoading = false,
                items = listOf(
                    HistoryItem(
                        id = "1",
                        name = "Voron Cube Benchie.gcode",
                        printerName = "Bambu X1-Carbon",
                        status = HistoryStatus.SUCCESS,
                        formattedDate = "Jan 15, 2025 2:30 PM",
                        formattedDuration = "2h 40m",
                    ),
                    HistoryItem(
                        id = "2",
                        name = "Phone Stand V2.gcode",
                        printerName = "Bambu P1S",
                        status = HistoryStatus.FAILED,
                        formattedDate = "Jan 14, 2025 11:15 AM",
                        formattedDuration = "45m",
                    ),
                    HistoryItem(
                        id = "3",
                        name = "Articulated Dragon.gcode",
                        printerName = "Bambu A1 Mini",
                        status = HistoryStatus.CANCELLED,
                        formattedDate = "Jan 13, 2025 6:00 PM",
                        formattedDuration = "1h 12m",
                    ),
                ),
            ),
            onRefresh = {},
            onLoadNextPage = {},
        )
    }
}
