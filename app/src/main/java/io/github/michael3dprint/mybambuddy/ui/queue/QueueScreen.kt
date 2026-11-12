package io.github.michael3dprint.mybambuddy.ui.queue

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.model.QueueStatus
import io.github.michael3dprint.mybambuddy.domain.repo.QueueRepository
import io.github.michael3dprint.mybambuddy.ui.common.ConfirmDialog
import io.github.michael3dprint.mybambuddy.ui.common.EmptyState
import io.github.michael3dprint.mybambuddy.ui.common.ErrorNotice
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.ListSkeleton
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.common.showMessage
import io.github.michael3dprint.mybambuddy.ui.print.LocalOpenPrintFlow
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** What a queue action asks before it is sent: the confirmation's title and the verb on its button. */
internal enum class QueueAction(val title: String, val verb: String) {
    START("Start now?", "Start"),
    CANCEL("Cancel this job?", "Cancel job"),
    REMOVE("Remove from the queue?", "Remove"),
    RESUME("Resume this printer's queue?", "Resume"),
}

/** The print queue per printer, with reorder, start, cancel and remove. Each action is confirmed first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(viewModel: QueueViewModel, title: String? = null, printerId: String? = null) {
    val openPrintFlow = LocalOpenPrintFlow.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val startBlocked by viewModel.startBlocked.collectAsStateWithLifecycle()
    val pictures by viewModel.pictures.collectAsStateWithLifecycle()
    val openJob = LocalOpenQueueJob.current
    val snackbar = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<Pair<QueueAction, QueueItem>?>(null) }
    var clearing by remember { mutableStateOf<List<QueueItem>?>(null) }
    var tab by rememberSaveable { mutableStateOf(QueueTab.QUEUE) }
    var shortfall by remember { mutableStateOf<ShortfallPrompt?>(null) }
    LaunchedEffect(Unit) { viewModel.shortfalls.collect { shortfall = it } }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showMessage(it) } }

    Scaffold(
        // The all-printers tab shows a title bar; inside a printer page (no title) the page already has its own bar (design 30).
        topBar = {
            if (title != null) {
                TopAppBar(
                    title = { Heading(title) },
                    actions = {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.filament_refresh))
                        }
                    },
                )
            }
        },
        contentWindowInsets = if (title == null) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // Keeps clear of the system navigation bar (the back, home and recents buttons), which the printer page draws under.
            ExtendedFloatingActionButton(onClick = { openPrintFlow(printerId) }, modifier = Modifier.navigationBarsPadding()) {
                Text("Print a file")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            QueueTabRow(tab) { tab = it }
            if (tab == QueueTab.PREVIOUS) {
                val factory = LocalPreviousPrintsFactory.current(printerId?.toIntOrNull())
                val previous: PreviousPrintsViewModel = viewModel(key = "previous-$printerId", factory = factory)
                PreviousPrintsTab(previous, printerId)
                return@Column
            }
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = state,
                    // Fades between loading, error and content; new data inside the same kind updates in place with no animation.
                    contentKey = { it::class },
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "queue",
                ) { s ->
                    when (s) {
                        QueueState.Loading -> ListSkeleton()
                        is QueueState.Error -> ErrorNotice(
                            s.error,
                            viewModel::refresh,
                            Modifier.align(Alignment.Center).padding(Dimens.Space.xl),
                        )
                        is QueueState.Success -> QueueList(s.items, viewModel, startBlocked, pictures, openJob, {
                            clearing = it
                        }) { action, item ->
                            pending =
                                action to item
                        }
                    }
                }
            }
        }
    }

    shortfall?.let { prompt ->
        ConfirmDialog(
            title = "Bambuddy reports a filament problem",
            confirmLabel = "Print anyway",
            cancelLabel = "Don't print",
            onConfirm = {
                shortfall = null
                viewModel.start(prompt.item, skipFilamentCheck = true)
            },
            onCancel = { shortfall = null },
        ) { Text("${prompt.text}\n\nThe job stays in the queue and has not started.") }
    }
    clearing?.let { list ->
        ConfirmDialog(
            title = "Clear finished jobs?",
            confirmLabel = "Clear",
            destructive = true,
            cancelLabel = stringResource(R.string.alerts_back),
            onConfirm = {
                clearing = null
                viewModel.clearFinished(list)
            },
            onCancel = { clearing = null },
        ) {
            Text(
                "${list.size} finished, failed or cancelled job(s) will be removed from the list. Jobs that are waiting or printing are not touched.",
            )
        }
    }
    pending?.let { (action, item) ->
        ConfirmDialog(
            title = action.title,
            confirmLabel = action.verb,
            destructive = action == QueueAction.CANCEL || action == QueueAction.REMOVE,
            cancelLabel = stringResource(R.string.alerts_back),
            onConfirm = {
                pending = null
                when (action) {
                    QueueAction.START -> viewModel.start(item)
                    QueueAction.CANCEL -> viewModel.cancel(item)
                    QueueAction.REMOVE -> viewModel.remove(item)
                    QueueAction.RESUME -> item.printerId?.let(viewModel::resume)
                }
            },
            onCancel = { pending = null },
        ) {
            Spoken(item.name + " · " + item.printerName)
        }
    }
}

@Composable
private fun QueueList(
    items: List<QueueItem>,
    viewModel: QueueViewModel,
    startBlocked: Map<Int, String>,
    pictures: Map<Int, ByteArray>,
    openJob: (QueueItem, () -> Unit) -> Unit,
    onClearFinished: (List<QueueItem>) -> Unit,
    onAction: (QueueAction, QueueItem) -> Unit,
) {
    val groups = items.groupBy { it.printerName }
    LazyColumn(
        // Extra room at the bottom so the last card is not hidden behind the Print a file button.
        contentPadding = PaddingValues(start = Dimens.Space.l, top = Dimens.Space.l, end = Dimens.Space.l, bottom = FAB_CLEARANCE),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space.m),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (items.isEmpty()) {
            item {
                EmptyState(
                    Icons.Default.Queue,
                    "Nothing is queued",
                    "Add jobs from Bambuddy and they will show up here.",
                    fillScreen = false,
                )
            }
        }
        groups.forEach { (printer, jobs) ->
            val waiting = jobs.filter { it.isPending }.sortedBy { it.position }
            item(key = "h$printer") { Heading(printer, style = MaterialTheme.typography.titleMedium) }
            val finished = jobs.filter { it.isFinished }
            if (finished.isNotEmpty()) {
                item(key = "c$printer") {
                    OutlinedButton(onClick = { onClearFinished(finished) }) { Text("Clear finished jobs (${finished.size})") }
                }
            }
            jobs.sortedBy { it.position }.forEach { job ->
                item(key = "j${job.id}") {
                    val index = waiting.indexOfFirst { it.id == job.id }
                    QueueCard(
                        job = job,
                        picture = pictures[job.id],
                        canMoveUp = index > 0,
                        canMoveDown = index in 0 until waiting.size - 1,
                        startOff = job.printerId == null || job.printerId in startBlocked,
                        blockedReason =
                        job.printerId?.let { startBlocked[it] } ?: QueueRepository.NO_PRINTER_MESSAGE.takeIf { job.printerId == null },
                        onMove = { up -> viewModel.move(job.id, up) },
                        onAction = { action -> onAction(action, job) },
                        onOpen = { openJob(job) { viewModel.refresh() } },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (jobs.any { it.status == QueueStatus.FAILED } && jobs.first().printerId != null) {
                item(key = "r$printer") {
                    TextButton(onClick = {
                        onAction(QueueAction.RESUME, jobs.first { it.printerId != null })
                    }) { Text(stringResource(R.string.queue_resume_queue_after_failure)) }
                }
            }
        }
    }
}

/** The status line of a job in words, such as "Waiting · #1". */
internal fun statusLabel(job: QueueItem): String = when (job.status) {
    QueueStatus.PENDING -> "Waiting · #${job.position}"
    QueueStatus.PRINTING -> "Printing"
    QueueStatus.COMPLETED -> "Completed"
    QueueStatus.FAILED -> "Failed"
    QueueStatus.SKIPPED -> "Skipped"
    QueueStatus.CANCELLED -> "Cancelled"
    QueueStatus.UNKNOWN -> "Unknown"
}

/** Space kept under the last card for the Print a file button and the system navigation bar. */
internal val FAB_CLEARANCE = androidx.compose.ui.unit.Dp(120f)
