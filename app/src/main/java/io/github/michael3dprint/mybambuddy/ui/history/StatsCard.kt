package io.github.michael3dprint.mybambuddy.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.PrintStats
import io.github.michael3dprint.mybambuddy.domain.model.StatBar
import io.github.michael3dprint.mybambuddy.domain.policy.StatsPeriod
import io.github.michael3dprint.mybambuddy.ui.common.ErrorNotice
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.common.nameMaxLines
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** The statistics card: one line when collapsed (no request is made), the numbers and bars when opened. */
@Composable
fun StatsCard(viewModel: StatsViewModel, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val period by viewModel.period.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(expanded) { if (expanded) viewModel.refresh() }

    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(Dimens.Space.l), verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = MIN_TARGET).clickable(role = Role.Button) { expanded = !expanded }
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Heading(
                    if (expanded) stringResource(R.string.stats_title) else "${stringResource(R.string.stats_title)} · ${period.label}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(if (expanded) R.string.stats_collapse else R.string.stats_expand),
                )
            }
            if (expanded) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
                    StatsPeriod.entries.forEach {
                        FilterChip(selected = it == period, onClick = { viewModel.select(it) }, label = { Text(it.label) })
                    }
                }
                when (val s = state) {
                    StatsState.Loading -> Text(stringResource(R.string.stats_loading), style = MaterialTheme.typography.bodyMedium)
                    is StatsState.Error -> ErrorNotice(s.error, viewModel::refresh)
                    is StatsState.Success -> StatsBody(s.stats)
                }
            }
        }
    }
}

@Composable
private fun StatsBody(stats: PrintStats) {
    if (stats.totalPrints == 0) {
        Text(stringResource(R.string.stats_none), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val summary = buildList {
        add("${stats.totalPrints} prints")
        stats.successPercent?.let { add("$it% succeeded") }
        add("%.1f h".format(java.util.Locale.US, stats.totalHours))
        add("%.1f kg".format(java.util.Locale.US, stats.totalGrams / GRAMS_PER_KG))
    }
    Text(summary.joinToString("   "), style = MaterialTheme.typography.titleSmall)
    Bars(stringResource(R.string.stats_by_printer), stats.byPrinter)
    Bars(stringResource(R.string.stats_by_material), stats.byMaterial)
    Spoken("Failed ${stats.failedPrints} · Cancelled ${stats.cancelledPrints}", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Bars(title: String, bars: List<StatBar>) {
    if (bars.isEmpty()) return
    val most = bars.maxOf { it.count }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.xs)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        bars.forEach { bar ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
                Text(bar.label, Modifier.width(88.dp), style = MaterialTheme.typography.bodySmall, maxLines = nameMaxLines())
                Box(Modifier.weight(1f)) {
                    Box(
                        Modifier.fillMaxWidth(bar.count.toFloat() / most).height(10.dp)
                            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                    )
                }
                Text("${bar.count}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private const val GRAMS_PER_KG = 1000.0

private val MIN_TARGET = 48.dp
