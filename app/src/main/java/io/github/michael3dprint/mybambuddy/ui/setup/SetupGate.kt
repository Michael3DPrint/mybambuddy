package io.github.michael3dprint.mybambuddy.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.michael3dprint.mybambuddy.domain.network.BambuddyFinder

/** First launch: the welcome screen first, then the server setup screen. Finding Bambuddy fills in the address before setup opens. */
@Composable
fun SetupGate(setupViewModel: SetupViewModel, onSetupComplete: () -> Unit) {
    var showWelcome by rememberSaveable { mutableStateOf(true) }
    if (showWelcome) {
        WelcomeScreen(
            viewModel = rememberWelcomeViewModel(),
            onUseHost = {
                setupViewModel.onServerUrlChanged("http://$it:${BambuddyFinder.DEFAULT_PORT}")
                showWelcome = false
            },
            onContinue = { showWelcome = false },
        )
    } else {
        SetupScreen(viewModel = setupViewModel, onSetupComplete = onSetupComplete)
    }
}
