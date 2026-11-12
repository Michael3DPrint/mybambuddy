package io.github.michael3dprint.mybambuddy.ui.print

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Opens the print flow. The argument is the printer to print on, or null to let the review ask (design 38). */
val LocalOpenPrintFlow = staticCompositionLocalOf<(String?) -> Unit> { {} }

/** Opens the print review for a previous print (its archive and the printer it ran on, or null to ask), to run it again (design 44). */
val LocalRunAgain = staticCompositionLocalOf<(io.github.michael3dprint.mybambuddy.domain.model.ArchiveRef, String?) -> Unit> { { _, _ -> } }

/** Hosts the print flow over [content] and lets any screen open it through [LocalOpenPrintFlow]. */
@Composable
fun ProvidePrintFlow(
    viewModel: PrintFlowViewModel,
    queueJobFactory: androidx.lifecycle.ViewModelProvider.Factory,
    previousPrintsFactory: (Int?) -> androidx.lifecycle.ViewModelProvider.Factory,
    content: @Composable () -> Unit,
) {
    // "" means open for any printer; null means closed.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    CompositionLocalProvider(
        LocalOpenPrintFlow provides { printerId ->
            viewModel.begin(printerId)
            open = printerId.orEmpty()
        },
        LocalRunAgain provides { ref, printerId ->
            viewModel.begin(printerId, ref)
            open = printerId.orEmpty()
        },
        io.github.michael3dprint.mybambuddy.ui.queue.LocalPreviousPrintsFactory provides previousPrintsFactory,
    ) {
        io.github.michael3dprint.mybambuddy.ui.queue.ProvideQueueJob(queueJobFactory) {
            content()
            if (open != null) PrintFlowScreen(viewModel, onClose = { open = null })
        }
    }
}
