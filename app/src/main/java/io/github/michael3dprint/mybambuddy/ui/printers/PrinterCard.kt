package io.github.michael3dprint.mybambuddy.ui.printers

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.Printer
import io.github.michael3dprint.mybambuddy.domain.model.PrinterReadiness
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import io.github.michael3dprint.mybambuddy.domain.policy.HmsSummary
import io.github.michael3dprint.mybambuddy.ui.common.RefreshingImage
import io.github.michael3dprint.mybambuddy.ui.common.StateChip
import io.github.michael3dprint.mybambuddy.ui.common.nameMaxLines
import io.github.michael3dprint.mybambuddy.ui.detail.hmsColors
import io.github.michael3dprint.mybambuddy.ui.readiness.RemoteControlLine
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens
import io.github.michael3dprint.mybambuddy.ui.theme.statusColors

/**
 * One printer on the Printers tab: name, state, camera thumbnail, progress or a status line, temperatures and a maintenance badge.
 * Tapping opens the details.
 */
@Composable
fun PrinterCard(
    printer: Printer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader? = null,
    /** Thumbnails are fetched once, and again only when this value changes (pull-to-refresh). */
    snapshotTick: Long = 0L,
    /** e.g. "2 due"; null when no upkeep is due. */
    maintenanceBadge: String? = null,
    /** This printer's Developer LAN mode state, shown as one line; null shows no line. */
    readiness: PrinterReadiness? = null,
    onOpenTools: () -> Unit = {},
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.Space.l),
        ) {
            // Slot 1: Header Row (Name/Model & State Chip)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = printer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = nameMaxLines(),
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = printer.model,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    maintenanceBadge?.let {
                        Spacer(modifier = Modifier.height(Dimens.Space.xs))
                        StateChip(
                            label = "Maintenance: $it",
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            icon = Icons.Default.Build,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(Dimens.Space.s))
                PrinterStateChip(state = printer.state)
            }

            readiness?.let {
                Spacer(modifier = Modifier.height(Dimens.Space.s))
                RemoteControlLine(it, onOpenTools)
            }

            Spacer(modifier = Modifier.height(Dimens.Space.m))

            // Slot 2: Camera Snapshot & HMS Warning Badge Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                if (printer.snapshotUrl.isNotEmpty()) {
                    RefreshingImage(
                        url = printer.snapshotUrl,
                        cacheKey = printer.id,
                        refreshKey = snapshotTick,
                        skipInitialFetchIfCached = true,
                        imageLoader = imageLoader,
                        contentDescription = "Camera snapshot for ${printer.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(Dimens.Space.xs))
                        Text(
                            text = stringResource(R.string.printers_no_camera_stream),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // HMS Warning Badge Overlay
                if (printer.hasHmsWarning) {
                    // A plain badge, not a chip: it does nothing of its own, so a tap passes through and opens the printer like the rest of the card.
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = hmsColors(HmsSummary.worst(printer.hmsErrors)).first,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Dimens.Space.s),
                    ) {
                        Row(
                            Modifier.padding(horizontal = Dimens.Space.s, vertical = Dimens.Space.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.Space.xs),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = hmsColors(HmsSummary.worst(printer.hmsErrors)).second,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Printer: ${HmsSummary.words(printer.hmsErrors)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = hmsColors(HmsSummary.worst(printer.hmsErrors)).second,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.Space.m))

            if (showsProgress(printer)) {
                // Moves smoothly to each new value instead of jumping on every poll (clamped: a server value outside 0..100 must not overdraw).
                val animatedProgress by animateFloatAsState(
                    targetValue = (printer.progress / 100f).coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "progress",
                )
                // Slot 3: Progress Bar & Remaining Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${printer.progress}%",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = printer.remainingTimeFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.Space.xs))

                LinearProgressIndicator(
                    // Clamped: a server value outside 0..100 would otherwise draw past the end of the track.
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .semantics { stateDescription = "${printer.progress.coerceIn(0, 100)} percent complete" },
                    color = when (printer.state) {
                        PrinterState.PRINTING -> MaterialTheme.colorScheme.primary
                        PrinterState.PAUSED -> MaterialTheme.statusColors.paused.content
                        PrinterState.ERROR, PrinterState.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(modifier = Modifier.height(Dimens.Space.s))

                // Slot 4: Layer Status
                Text(
                    text = printer.layerFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Idle, finished, offline: there is no print to measure, so say so instead of showing an empty "0%" bar.
                Text(
                    text = idleLine(printer.state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(Dimens.Space.s))

            // Slot 5: Temperatures Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Nozzle: ${printer.temperatures.formattedNozzle}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Bed: ${printer.temperatures.formattedBed}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Chamber: ${printer.temperatures.formattedChamber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The pill showing a printer's state, coloured from the status palette. */
@Composable
fun PrinterStateChip(state: PrinterState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.statusColors.forState(state)
    val backgroundColor = colors.container
    val textColor = colors.content
    val label = when (state) {
        PrinterState.PRINTING -> "PRINTING"
        PrinterState.PAUSED -> "PAUSED"
        PrinterState.IDLE -> "IDLE"
        PrinterState.FINISHED -> "FINISHED"
        PrinterState.ERROR -> "ERROR"
        PrinterState.FAILED -> "LAST PRINT FAILED"
        PrinterState.OFFLINE -> "OFFLINE"
        PrinterState.UNKNOWN -> "UNKNOWN"
    }

    StateChip(label = label, container = backgroundColor, content = textColor, modifier = modifier)
}

/** Whether the card has a print to measure: only while printing or paused, or when an error left a partly finished job. */
internal fun showsProgress(printer: Printer): Boolean = when (printer.state) {
    PrinterState.PRINTING, PrinterState.PAUSED -> true
    PrinterState.ERROR -> printer.progress > 0
    else -> false
}

/** The one line shown in place of the progress block. */
internal fun idleLine(state: PrinterState): String = when (state) {
    PrinterState.IDLE -> "Ready. Nothing is printing."
    PrinterState.FINISHED -> "Print finished."
    PrinterState.OFFLINE -> "Offline."
    PrinterState.ERROR -> "Needs attention."
    PrinterState.FAILED -> "Last print failed. Ready for the next print."
    else -> "Status unknown."
}
