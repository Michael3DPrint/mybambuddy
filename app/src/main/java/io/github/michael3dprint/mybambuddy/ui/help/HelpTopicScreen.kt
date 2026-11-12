package io.github.michael3dprint.mybambuddy.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.domain.help.COMMUNITY_NOTICE
import io.github.michael3dprint.mybambuddy.domain.help.HelpLibrary
import io.github.michael3dprint.mybambuddy.domain.help.HelpShare
import io.github.michael3dprint.mybambuddy.domain.help.HelpStep
import io.github.michael3dprint.mybambuddy.domain.help.HelpTopicId
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens
import kotlinx.coroutines.launch

/** One help page: what it is about, numbered steps tagged with where each is done, what could not be confirmed, and where it came from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpTopicScreen(id: HelpTopicId, onBack: () -> Unit) {
    val topic = HelpLibrary.topic(id)
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Heading(topic.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Setup help") } },
                actions = {
                    IconButton(onClick = { send(context, HelpShare.textFor(topic)) }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Send this page's links to my computer")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(
                padding,
            ).padding(horizontal = 20.dp, vertical = Dimens.Space.l).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.l),
        ) {
            Text(topic.summary, style = MaterialTheme.typography.bodyLarge)
            if (topic.community) Notice(COMMUNITY_NOTICE, container = MaterialTheme.colorScheme.tertiaryContainer)
            topic.steps.forEachIndexed { i, step -> StepRow(i + 1, step) }
            if (topic.unverified.isNotEmpty()) {
                Heading("Not confirmed", style = MaterialTheme.typography.titleMedium)
                topic.unverified.forEach { Notice(it, container = MaterialTheme.colorScheme.surfaceVariant) }
            }
            Sources(topic)
        }
    }
}

@Composable
private fun StepRow(number: Int, step: HelpStep) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space.xs)) {
        Text(step.where.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("$number. ${step.text}", style = MaterialTheme.typography.bodyMedium)
        step.command?.let { command ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        scope.launch { clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("command", command))) }
                    },
                ) { Text("Copy") }
            }
        }
    }
}

@Composable
private fun Notice(text: String, container: androidx.compose.ui.graphics.Color) {
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(Dimens.Space.l), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Sources(topic: io.github.michael3dprint.mybambuddy.domain.help.HelpTopic) {
    val uri = LocalUriHandler.current
    if (topic.sources.isNotEmpty()) {
        Heading("Sources (read ${topic.readOn})", style = MaterialTheme.typography.titleMedium)
        topic.sources.forEach { OutlinedButton(onClick = { uri.openUri(it.url) }, modifier = Modifier.fillMaxWidth()) { Text(it.title) } }
    }
}
