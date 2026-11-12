package io.github.michael3dprint.mybambuddy.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.print.PrintEstimate
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/**
 * One queue job (design 41): its picture, name, status, the slicer's estimate and any reason it cannot start. A waiting job has one filled
 * Start now button and a More menu holding the reorder moves and the two destructive actions, which keep their confirmations.
 */
@Composable
internal fun QueueCard(
    job: QueueItem,
    picture: ByteArray?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    startOff: Boolean,
    blockedReason: String?,
    onMove: (Boolean) -> Unit,
    onAction: (QueueAction) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(Dimens.Space.m), horizontalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
            QueueThumb(picture, Modifier.size(THUMB_SIZE))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.Space.xs)) {
                Text(job.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spoken(statusLabel(job), style = MaterialTheme.typography.labelMedium)
                PrintEstimate.summary(job.seconds, job.grams, job.filamentType)?.let {
                    Spoken("$it (slicer estimate)", style = MaterialTheme.typography.bodySmall)
                }
                job.waitingReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (job.filamentShort) {
                    Text(
                        "Not enough filament for this job.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (job.isPending) {
                    blockedReason?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
                        Button(
                            onClick = { onAction(QueueAction.START) },
                            enabled = !startOff,
                            modifier = Modifier.weight(1f).heightIn(min = MIN_TARGET),
                        ) { Text("Start now") }
                        MoreMenu(job, canMoveUp, canMoveDown, onMove, onAction)
                    }
                } else if (job.isFinished) {
                    // A finished job can only be removed from the list; without this it would stay on the queue page for good.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { onAction(QueueAction.REMOVE) },
                            modifier = Modifier.heightIn(min = MIN_TARGET),
                        ) { Text("Remove from queue") }
                    }
                }
            }
        }
    }
}

/** The More menu: the moves, then (after a divider) Cancel this job and Remove from queue in the error colour, with icons and words. */
@Composable
private fun MoreMenu(job: QueueItem, canMoveUp: Boolean, canMoveDown: Boolean, onMove: (Boolean) -> Unit, onAction: (QueueAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(MIN_TARGET)) {
        Icon(Icons.Default.MoreVert, contentDescription = "More actions for ${job.name}")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Move up") },
            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
            enabled = canMoveUp,
            onClick = {
                open = false
                onMove(true)
            },
        )
        DropdownMenuItem(
            text = { Text("Move down") },
            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
            enabled = canMoveDown,
            onClick = {
                open = false
                onMove(false)
            },
        )
        HorizontalDivider()
        val danger = MaterialTheme.colorScheme.error
        DropdownMenuItem(
            text = { Text("Cancel this job", color = danger) },
            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = danger) },
            onClick = {
                open = false
                onAction(QueueAction.CANCEL)
            },
        )
        DropdownMenuItem(
            text = { Text("Remove from queue", color = danger) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = danger) },
            onClick = {
                open = false
                onAction(QueueAction.REMOVE)
            },
        )
    }
}

private val THUMB_SIZE = 72.dp
private val MIN_TARGET = 48.dp
