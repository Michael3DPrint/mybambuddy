package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.ui.common.nameMaxLines
import io.github.michael3dprint.mybambuddy.ui.printers.PrinterStateChip
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens
import io.github.michael3dprint.mybambuddy.ui.theme.statusColors

/** State, progress, layers and temperatures. */
@Composable
internal fun PrinterStatusBlock(currentPrinter: Printer) {
    // Status Block Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.Space.l),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.m),
        ) {
            // Header: Print Job Name & Printer State Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentPrinter.printName ?: "No Active Print",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = nameMaxLines(),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(Dimens.Space.s))
                PrinterStateChip(state = currentPrinter.state)
            }

            // Progress Bar & Percentage
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.detail_progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${currentPrinter.progress}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                LinearProgressIndicator(
                    progress = { currentPrinter.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.Space.m)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = when (currentPrinter.state) {
                        PrinterState.PRINTING -> MaterialTheme.colorScheme.primary
                        PrinterState.PAUSED -> MaterialTheme.statusColors.paused.content
                        PrinterState.ERROR, PrinterState.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            // Layer & Time Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (currentPrinter.totalLayers > 0) {
                        "Layer ${currentPrinter.currentLayer} of ${currentPrinter.totalLayers}"
                    } else {
                        "Layer ${currentPrinter.currentLayer}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                val timeDetails = if (currentPrinter.estimatedFinishTime.isNotEmpty() &&
                    currentPrinter.estimatedFinishTime != "--"
                ) {
                    "${currentPrinter.remainingTimeFormatted} • Est. finish ${currentPrinter.estimatedFinishTime}"
                } else {
                    currentPrinter.remainingTimeFormatted
                }
                Text(
                    text = timeDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = Dimens.Space.xs),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Temperatures Block
            Text(
                text = stringResource(R.string.detail_temperatures),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                TemperatureMetricItem(
                    label = "Nozzle",
                    temp = currentPrinter.nozzleTemp,
                )
                TemperatureMetricItem(
                    label = "Bed",
                    temp = currentPrinter.bedTemp,
                )
                TemperatureMetricItem(
                    label = "Chamber",
                    temp = currentPrinter.chamberTemp,
                )
            }
        }
    }
}
