package io.github.michael3dprint.mybambuddy.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import io.github.michael3dprint.mybambuddy.ui.theme.MyBambuddyTheme

/** Android Studio preview of the Setup screen with sample data. */
@Preview(showBackground = true)
@Composable
fun SetupContentPreview() {
    MyBambuddyTheme {
        SetupContent(
            uiState = SetupUiState(
                serverUrl = "http://100.64.0.1:8000",
                apiKey = "example",
                testResultSuccess = true,
                testResultMessage = "Connected to Bambuddy Server (v1.0) successfully!",
                isSaved = true,
                saveMessage = "Settings saved securely.",
            ),
            onServerUrlChanged = {},
            onApiKeyChanged = {},
            onToggleApiKeyVisibility = {},
            onTestConnection = {},
            onSaveSettings = {},
        )
    }
}
