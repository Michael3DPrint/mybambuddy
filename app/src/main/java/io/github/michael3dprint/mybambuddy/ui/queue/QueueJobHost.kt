package io.github.michael3dprint.mybambuddy.ui.queue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem

/** Opens a queue job's page. The second argument is called when the page closes, so the queue can reload. */
val LocalOpenQueueJob = staticCompositionLocalOf<(QueueItem, () -> Unit) -> Unit> { { _, _ -> } }

/** Hosts the queue job page over [content] and lets the queue open it through [LocalOpenQueueJob] (design 42). */
@Composable
fun ProvideQueueJob(factory: ViewModelProvider.Factory, content: @Composable () -> Unit) {
    val viewModel: QueueJobViewModel = viewModel(factory = factory)
    var open by rememberSaveable { mutableStateOf(false) }
    var afterClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    CompositionLocalProvider(
        LocalOpenQueueJob provides { item, onClosed ->
            viewModel.open(item)
            afterClose = onClosed
            open = true
        },
    ) {
        content()
        if (open) {
            QueueJobScreen(viewModel) {
                open = false
                afterClose?.invoke()
            }
        }
    }
}
