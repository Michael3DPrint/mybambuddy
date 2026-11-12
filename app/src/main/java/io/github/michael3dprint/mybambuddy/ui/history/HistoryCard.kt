package io.github.michael3dprint.mybambuddy.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import io.github.michael3dprint.mybambuddy.domain.model.HistoryItem
import io.github.michael3dprint.mybambuddy.domain.model.HistoryStatus
import io.github.michael3dprint.mybambuddy.ui.common.StateChip
import io.github.michael3dprint.mybambuddy.ui.common.nameMaxLines
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens
import io.github.michael3dprint.mybambuddy.ui.theme.statusColors

/** One archived print: thumbnail, name, printer, date, duration and result. */
@Composable
fun HistoryCard(item: HistoryItem, modifier: Modifier = Modifier, imageLoader: ImageLoader? = null) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Optional Thumbnail Image
            if (!item.thumbnailUrl.isNullOrBlank()) {
                if (imageLoader != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        imageLoader = imageLoader,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                } else {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
                Spacer(modifier = Modifier.width(Dimens.Space.l))
            }

            // Main Info
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = nameMaxLines(),
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(Dimens.Space.s))
                    StatusChip(status = item.status)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Print,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.printerName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.Space.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(Dimens.Space.xs))
                        Text(
                            text = item.formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(Dimens.Space.xs))
                        Text(
                            text = item.formattedDuration,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** The pill showing how an archived print ended. */
@Composable
fun StatusChip(status: HistoryStatus, modifier: Modifier = Modifier) {
    val (bgColor, contentColor, label, icon) = when (status) {
        HistoryStatus.SUCCESS -> StatusChipConfig(
            backgroundColor = MaterialTheme.statusColors.printing.container,
            contentColor = MaterialTheme.statusColors.printing.content,
            label = "Completed",
            icon = Icons.Rounded.CheckCircle,
        )
        HistoryStatus.CANCELLED -> StatusChipConfig(
            backgroundColor = MaterialTheme.statusColors.paused.container,
            contentColor = MaterialTheme.statusColors.paused.content,
            label = "Cancelled",
            icon = Icons.Rounded.Cancel,
        )
        HistoryStatus.FAILED -> StatusChipConfig(
            backgroundColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            label = "Failed",
            icon = Icons.Rounded.Error,
        )
        HistoryStatus.IN_PROGRESS -> StatusChipConfig(
            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            label = "Printing",
            icon = Icons.Rounded.CheckCircle,
        )
        HistoryStatus.UNKNOWN -> StatusChipConfig(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            label = "Unknown",
            icon = Icons.AutoMirrored.Rounded.Help,
        )
    }

    StateChip(label = label, container = bgColor, content = contentColor, modifier = modifier, icon = icon)
}

private data class StatusChipConfig(val backgroundColor: Color, val contentColor: Color, val label: String, val icon: ImageVector)
