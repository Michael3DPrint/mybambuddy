package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.barcode.OnlineLookupConsent
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.domain.nfc.OpenTag3dData
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** Details of one spool plus the NFC action. Loading into an AMS slot is done from the AMS & Drying section. */
@Composable
fun SpoolDetailSheet(
    spool: Spool,
    usage: UsageState,
    nfcAvailable: Boolean,
    canWeigh: Boolean,
    onWeigh: () -> Unit,
    onLoadIntoAms: () -> Unit,
    onWriteTag: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(Dimens.Space.l), verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch(spool.colorHex, Modifier.size(48.dp))
            Spacer(Modifier.size(16.dp))
            Column {
                Heading(spool.title, style = MaterialTheme.typography.titleLarge)
                if (spool.colorName.isNotBlank()) Text(spool.colorName, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("${spool.remainingGrams} g left of ${spool.labelWeightGrams} g (${spool.remainingPercent} %)")
        Text(
            spool.tagUid?.let { "NFC tag linked: $it" } ?: "No NFC tag linked yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (spool.isLow) Text(stringResource(R.string.filament_running_low_consider_ordering_a), color = MaterialTheme.colorScheme.error)
        Heading(stringResource(R.string.filament_usage), style = MaterialTheme.typography.titleSmall)
        when (usage) {
            UsageState.Idle, UsageState.Loading -> CircularProgressIndicator(Modifier.size(20.dp))
            is UsageState.Failed -> Text(usage.message, style = MaterialTheme.typography.bodySmall)
            is UsageState.Loaded ->
                if (usage.entries.isEmpty()) {
                    Text(stringResource(R.string.filament_no_usage_recorded_yet), style = MaterialTheme.typography.bodySmall)
                } else {
                    usage.entries.take(10).forEach {
                        Text("${it.date}  ${it.printName}  ${it.grams} g", style = MaterialTheme.typography.bodySmall)
                    }
                }
        }
        if (canWeigh) {
            OutlinedButton(onClick = onWeigh, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.weigh_button)) }
        }
        OutlinedButton(onClick = onLoadIntoAms, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.filament_load_into_ams))
        }
        if (nfcAvailable) {
            Button(onClick = onWriteTag, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.filament_write_nfc_tag_ntag215))
            }
        } else {
            Text(stringResource(R.string.filament_this_phone_has_no_nfc), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(Dimens.Space.l))
    }
}

/** The tag write/read dialog. Shown for every state except Closed. */
@Composable
fun NfcDialog(
    state: NfcDialogState,
    onClose: () -> Unit,
    onOpenSpool: (Spool) -> Unit,
    onCreateFromTag: (SpoolDraft) -> Unit,
    onLoadIntoAms: (Spool) -> Unit,
) {
    if (state is NfcDialogState.Closed) return
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(titleFor(state)) },
        text = { NfcDialogBody(state) },
        confirmButton = {
            when (state) {
                is NfcDialogState.Scanned -> when {
                    state.spool != null -> Row {
                        TextButton(onClick = { onOpenSpool(state.spool) }) { Text(stringResource(R.string.filament_open)) }
                        TextButton(onClick = { onLoadIntoAms(state.spool) }) { Text(stringResource(R.string.filament_load_into_ams)) }
                    }
                    state.fromTag != null -> TextButton(onClick = {
                        onCreateFromTag(state.fromTag.toDraft())
                    }) { Text(stringResource(R.string.filament_add_as_new_spool)) }
                    else -> TextButton(onClick = onClose) { Text(stringResource(R.string.filament_ok)) }
                }
                is NfcDialogState.Done, is NfcDialogState.Problem -> TextButton(onClick = onClose) {
                    Text(stringResource(R.string.filament_ok))
                }
                else -> {}
            }
        },
        dismissButton = {
            if (state is NfcDialogState.WaitingToWrite || state is NfcDialogState.WaitingToRead || state is NfcDialogState.Working) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.detail_cancel)) }
            } else if (state is NfcDialogState.Scanned) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.detail_close)) }
            }
        },
    )
}

private fun titleFor(state: NfcDialogState): String = when (state) {
    is NfcDialogState.WaitingToWrite -> "Write NFC tag"
    is NfcDialogState.WaitingToRead -> "Scan NFC tag"
    is NfcDialogState.Working -> "Working…"
    is NfcDialogState.Done -> "Done"
    is NfcDialogState.Problem -> "Problem"
    is NfcDialogState.Scanned -> "Tag read"
    NfcDialogState.Closed -> ""
}

@Composable
private fun NfcDialogBody(state: NfcDialogState) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
        when (state) {
            is NfcDialogState.WaitingToWrite -> {
                Text("${state.spool.title}\nHold an NTAG215 tag against the back of the phone.")
                Text(stringResource(R.string.filament_anything_already_on_the_tag), color = MaterialTheme.colorScheme.error)
                CircularProgressIndicator(Modifier.size(24.dp))
            }
            is NfcDialogState.WaitingToRead -> {
                Text(stringResource(R.string.filament_hold_a_tag_against_the))
                CircularProgressIndicator(Modifier.size(24.dp))
            }
            is NfcDialogState.Working -> {
                Text(stringResource(R.string.filament_keep_the_tag_in_place))
                CircularProgressIndicator(Modifier.size(24.dp))
            }
            is NfcDialogState.Done -> Text(state.message)
            is NfcDialogState.Problem -> Text(state.message)
            is NfcDialogState.Scanned -> when {
                state.spool != null -> Text("This tag belongs to ${state.spool.title}.")
                state.fromTag != null -> Text(
                    "Not linked to a spool yet. The tag says: ${state.fromTag.manufacturer} ${state.fromTag.material} " +
                        "${state.fromTag.modifiers} ${state.fromTag.colorName}".trim(),
                )
                else -> Text("This tag isn't linked to any spool and holds no filament data.\nTag ID: ${state.uid}")
            }
            NfcDialogState.Closed -> {}
        }
    }
}

/** Turns the data read from a tag into a draft for the Add spool form. */
internal fun OpenTag3dData.toDraft(): SpoolDraft = SpoolDraft(
    brand = manufacturer,
    material = material,
    subtype = modifiers,
    colorName = colorName,
    colorHex = rgba.take(6),
    labelWeightGrams = weightGrams.takeIf { it > 0 } ?: 1000,
)

/** Asks before any barcode digits leave the phone. */
@Composable
fun OnlineLookupDialog(ean: String, onAnswer: (allow: Boolean, consent: OnlineLookupConsent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false, OnlineLookupConsent.ASK) },
        title = { Text(stringResource(R.string.filament_look_up_this_barcode_online)) },
        text = {
            Text(
                "Barcode $ean isn't known on your own servers. I can ask upcitemdb.com what product it is. " +
                    "Only these digits are sent (over https), nothing else.",
            )
        },
        confirmButton = {
            Column {
                TextButton(onClick = { onAnswer(true, OnlineLookupConsent.ASK) }) { Text(stringResource(R.string.filament_look_up_once)) }
                TextButton(onClick = {
                    onAnswer(true, OnlineLookupConsent.ALWAYS)
                }) { Text(stringResource(R.string.filament_always_allow)) }
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = { onAnswer(false, OnlineLookupConsent.ASK) }) { Text(stringResource(R.string.filament_not_now)) }
                TextButton(onClick = { onAnswer(false, OnlineLookupConsent.NEVER) }) { Text(stringResource(R.string.filament_never)) }
            }
        },
    )
}
