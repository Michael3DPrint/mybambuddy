package io.github.michael3dprint.mybambuddy.ui.printers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.ImageLoader
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.UiErrorState
import io.github.michael3dprint.mybambuddy.ui.common.EmptyState
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.ListSkeleton
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** The Printers tab. Starts polling while visible and connects the view model to [PrintersScreenContent]. */
@Composable
fun PrintersScreen(
    viewModel: PrintersViewModel,
    imageLoader: ImageLoader? = null,
    onPrinterClick: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenTools: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val maintenanceBadges by viewModel.maintenanceBadges.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.startPolling()
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                viewModel.stopPolling()
            }
        }
    }

    var snapshotTick by remember { mutableLongStateOf(0L) }

    PrintersScreenContent(
        uiState = uiState,
        imageLoader = imageLoader,
        snapshotTick = snapshotTick,
        maintenanceBadges = maintenanceBadges,
        onRefresh = {
            snapshotTick = System.currentTimeMillis()
            viewModel.refresh()
        },
        onPrinterClick = onPrinterClick,
        onOpenSettings = onOpenSettings,
        onOpenTools = onOpenTools,
    )
}

/** Draws the Printers tab from its state: the readiness card, one card per printer, and the loading, error and empty states. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintersScreenContent(
    uiState: PrintersUiState,
    imageLoader: ImageLoader? = null,
    snapshotTick: Long = 0L,
    maintenanceBadges: Map<String, String> = emptyMap(),
    onRefresh: () -> Unit = {},
    onPrinterClick: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenTools: (String) -> Unit = {},
) {
    val isRefreshing = when (uiState) {
        is PrintersUiState.Success -> uiState.isRefreshing
        is PrintersUiState.Error -> uiState.isRefreshing
        else -> false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Heading(
                        text = stringResource(R.string.printers_printdeck),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.filament_refresh),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (uiState) {
                is PrintersUiState.Loading -> {
                    ListSkeleton(rowHeight = 260.dp, rows = 2)
                }
                is PrintersUiState.Error -> {
                    ErrorStateView(
                        errorState = uiState.errorState,
                        onPrimaryAction = onRefresh,
                        onSecondaryAction = onOpenSettings,
                    )
                }
                is PrintersUiState.Success -> {
                    if (uiState.printers.isEmpty()) {
                        EmptyPrintersView(onRefresh = onRefresh)
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            uiState.staleError?.let { err ->
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                                ) {
                                    Text(
                                        text = "Couldn't refresh — showing the last data. ${err.title}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = Dimens.Space.l, vertical = Dimens.Space.s),
                                    )
                                }
                            }
                            LazyColumn(
                                contentPadding = PaddingValues(Dimens.Space.l),
                                verticalArrangement = Arrangement.spacedBy(Dimens.Space.l),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(
                                    items = uiState.printers,
                                    key = { printer -> printer.id },
                                ) { printer ->
                                    PrinterCard(
                                        printer = printer,
                                        imageLoader = imageLoader,
                                        snapshotTick = snapshotTick,
                                        maintenanceBadge = maintenanceBadges[printer.id],
                                        // Derived from the status already polled, so it costs no extra request.
                                        readiness = uiState.readiness.firstOrNull { it.printerId == printer.id },
                                        onOpenTools = { onOpenTools(printer.id) },
                                        onClick = { onPrinterClick(printer.id) },
                                        // Rows slide to their new place when a printer appears or goes away.
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A full-screen error with a title, a message and up to two buttons. */
@Composable
fun ErrorStateView(errorState: UiErrorState, onPrimaryAction: () -> Unit, onSecondaryAction: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.Space.xl),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.Space.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(modifier = Modifier.height(Dimens.Space.l))
                Heading(
                    text = errorState.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(Dimens.Space.s))
                Text(
                    text = errorState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(Dimens.Space.xl))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space.m),
                ) {
                    errorState.primaryActionLabel?.let { label ->
                        Button(
                            onClick = onPrimaryAction,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(text = label)
                        }
                    }
                    errorState.secondaryActionLabel?.let { label ->
                        OutlinedButton(
                            onClick = onSecondaryAction,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(text = label)
                        }
                    }
                    io.github.michael3dprint.mybambuddy.ui.help.HelpButton(
                        io.github.michael3dprint.mybambuddy.domain.help.ErrorHelp.topicFor(errorState.kind),
                    )
                }
            }
        }
    }
}

/** Shown when Bambuddy has no printers: an explanation and a Refresh button. */
@Composable
fun EmptyPrintersView(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Default.Print,
        title = "No printers found",
        body = "Pull down to refresh or check the server settings.",
        modifier = modifier,
        action = { Button(onClick = onRefresh) { Text(stringResource(R.string.filament_refresh)) } },
    )
}
