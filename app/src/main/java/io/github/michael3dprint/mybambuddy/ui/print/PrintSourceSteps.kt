package io.github.michael3dprint.mybambuddy.ui.print

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.michael3dprint.mybambuddy.domain.model.MakerWorldAccess
import io.github.michael3dprint.mybambuddy.domain.print.MakerWorldLink
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.common.politeLive
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** Step 1: where the file comes from. */
@Composable
internal fun PrintSourceStep(state: PrintFlowState, onPickFile: () -> Unit, onMakerWorld: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
        Heading("Print a file", style = MaterialTheme.typography.titleLarge)
        Text("Choose a sliced 3MF (.gcode.3mf) from this phone, or give a MakerWorld link.", style = MaterialTheme.typography.bodyMedium)
        state.message?.let { Text(it, Modifier.politeLive(), color = MaterialTheme.colorScheme.error) }
        Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) { Text("From this phone") }
        OutlinedButton(onClick = onMakerWorld, modifier = Modifier.fillMaxWidth()) { Text("From a MakerWorld link") }
        Text(
            "The file goes only to your own Bambuddy server. Nothing starts until you confirm on the next screens.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Waiting on the server, with progress for an upload and a Cancel that stops it. */
@Composable
internal fun PrintWorkingStep(state: PrintFlowState, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
        Heading(state.workingLabel, style = MaterialTheme.typography.titleLarge)
        val progress = state.progress
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spoken("${(progress * PERCENT).toInt()} %", Modifier.politeLive())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}

/** MakerWorld: paste a link, look it up, choose a plate configuration, import. */
@Composable
internal fun PrintLinkStep(state: PrintFlowState, onLookUp: (String) -> Unit, onImport: (Int) -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    var profile by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
        Heading("MakerWorld link", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            label = { Text("Link to the model") },
            singleLine = true,
            isError = link.isNotBlank() && MakerWorldLink.parse(link) == null,
            supportingText = { if (link.isNotBlank() && MakerWorldLink.parse(link) == null) Text("Not a MakerWorld model link.") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onLookUp(link) }, enabled = MakerWorldLink.parse(link) != null) { Text("Look up") }
        state.message?.let { Text(it, Modifier.politeLive(), color = MaterialTheme.colorScheme.error) }
        when (state.access) {
            MakerWorldAccess.NOT_SIGNED_IN -> AccessHelp(
                "Bambuddy isn't signed in to Bambu Cloud, which it needs to download from MakerWorld. Sign in on Bambuddy's Cloud page, then look up the link again.",
            )
            MakerWorldAccess.SIGN_IN_EXPIRED -> AccessHelp(
                "Bambuddy's Bambu Cloud sign-in has expired. Sign in again on Bambuddy's Cloud page, then look up the link again.",
            )
            else -> Unit
        }
        state.model?.let { model ->
            Heading(model.title, style = MaterialTheme.typography.titleMedium)
            val picked = model.instances.firstOrNull { it.profileId == profile }
            val cover = picked?.coverUrl ?: model.coverUrl
            PrintPreview(cover?.let { state.images[it] }, emptyText = "Loading the picture from MakerWorld, or none is available.")
            Text(
                "This picture comes from MakerWorld. Check it is the model you meant before importing.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (model.alreadyImported.isNotEmpty()) {
                Text(
                    "Already in your library; importing reuses that file.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(Modifier.selectableGroup()) {
                model.instances.forEach { instance ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(
                            min = MIN_TARGET,
                        ).selectable(selected = profile == instance.profileId, role = Role.RadioButton, onClick = {
                            profile =
                                instance.profileId
                        }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = profile == instance.profileId, onClick = null)
                        Text(instance.title, modifier = Modifier.padding(start = Dimens.Space.s))
                    }
                }
            }
            Button(onClick = { profile?.let(onImport) }, enabled = profile != null) { Text("Yes, import this") }
        }
    }
}

@Composable
private fun AccessHelp(text: String) {
    Text(text, Modifier.politeLive(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    io.github.michael3dprint.mybambuddy.ui.help.HelpButton(io.github.michael3dprint.mybambuddy.domain.help.HelpTopicId.T_CLOUD)
}

private const val PERCENT = 100

private val MIN_TARGET = androidx.compose.ui.unit.Dp(48f)
