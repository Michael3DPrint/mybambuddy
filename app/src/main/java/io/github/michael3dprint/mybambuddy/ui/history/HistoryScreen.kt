package io.github.michael3dprint.mybambuddy.ui.history

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.ui.common.EmptyState
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.ListSkeleton
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** The History tab. Connects the view model to [HistoryContent]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    statsViewModel: StatsViewModel,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HistoryContent(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onLoadNextPage = viewModel::loadNextPage,
        imageLoader = imageLoader,
        modifier = modifier,
        header = { StatsCard(statsViewModel) },
    )
}

/** Draws the history list from its state, loading the next page when the end is near. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    uiState: HistoryUiState,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader? = null,
    /** Drawn above the first history card, or null for nothing. */
    header: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()

    // Automatic load more when reaching near end of list
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && uiState.canLoadMore && !uiState.isLoadingNextPage && !uiState.isLoading) {
            onLoadNextPage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(Dimens.Space.m))
                        Heading(
                            text = stringResource(R.string.history_print_history),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !uiState.isLoading && !uiState.isRefreshing,
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.history_refresh_history),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> ListSkeleton(rowHeight = 96.dp, rows = 5)

                uiState.errorState != null && uiState.items.isEmpty() -> {
                    val errState = uiState.errorState
                    Box(
                        modifier = Modifier
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
                                    imageVector = Icons.Rounded.Error,
                                    contentDescription = stringResource(R.string.history_error),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(56.dp),
                                )
                                Spacer(modifier = Modifier.height(Dimens.Space.l))
                                Heading(
                                    text = errState.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(Dimens.Space.s))
                                Text(
                                    text = errState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space.m),
                                ) {
                                    Button(
                                        onClick = onRefresh,
                                        shape = MaterialTheme.shapes.medium,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(Dimens.Space.s))
                                        Text(errState.primaryActionLabel ?: "Retry")
                                    }
                                    io.github.michael3dprint.mybambuddy.ui.help.HelpButton(
                                        io.github.michael3dprint.mybambuddy.domain.help.ErrorHelp.topicFor(errState.kind),
                                    )
                                }
                            }
                        }
                    }
                }

                uiState.items.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Rounded.History,
                        title = "No print history found",
                        body = "Completed, cancelled, or failed print jobs will appear here.",
                        action = { FilledTonalButton(onClick = onRefresh) { Text(stringResource(R.string.filament_refresh)) } },
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Space.m),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 12.dp,
                        ),
                    ) {
                        header?.let { item(key = "stats-header") { it() } }
                        itemsIndexed(
                            items = uiState.items,
                            key = { index, item -> "${item.id}_$index" },
                        ) { _, item ->
                            HistoryCard(
                                item = item,
                                imageLoader = imageLoader,
                                modifier = Modifier.animateItem(),
                            )
                        }

                        if (uiState.isLoadingNextPage) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Dimens.Space.l),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        } else if (uiState.canLoadMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Dimens.Space.s),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    OutlinedButton(
                                        onClick = onLoadNextPage,
                                        shape = MaterialTheme.shapes.medium,
                                    ) {
                                        Text(stringResource(R.string.history_load_more))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
