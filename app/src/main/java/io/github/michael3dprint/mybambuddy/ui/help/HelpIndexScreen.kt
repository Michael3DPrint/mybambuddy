package io.github.michael3dprint.mybambuddy.ui.help

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michael3dprint.mybambuddy.domain.help.HelpLibrary
import io.github.michael3dprint.mybambuddy.domain.help.HelpShare
import io.github.michael3dprint.mybambuddy.domain.help.HelpTab
import io.github.michael3dprint.mybambuddy.domain.help.HelpTopicId
import io.github.michael3dprint.mybambuddy.domain.help.InstallMethod
import io.github.michael3dprint.mybambuddy.domain.network.BambuddyFinder
import io.github.michael3dprint.mybambuddy.ui.common.AppTabRow
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.NavRow
import io.github.michael3dprint.mybambuddy.ui.common.StatusMark
import io.github.michael3dprint.mybambuddy.ui.setup.FindBambuddySection
import io.github.michael3dprint.mybambuddy.ui.setup.checkRows
import io.github.michael3dprint.mybambuddy.ui.setup.rememberWelcomeViewModel
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** The Setup help screen (design 48): a title bar and three tabs, Guide, Fix a problem and This phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpIndexScreen(
    tab: HelpTab,
    onTab: (HelpTab) -> Unit,
    method: InstallMethod,
    onMethod: (InstallMethod) -> Unit,
    onOpen: (HelpTopicId) -> Unit,
    onUseAddress: (String) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Heading("Setup help") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close Setup help")
                        }
                    },
                )
                AppTabRow(HelpTab.entries.map { it.title }, tab.ordinal) { onTab(HelpTab.entries[it]) }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = Dimens.Space.l),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s),
        ) {
            when (tab) {
                HelpTab.GUIDE -> GuideTab(method, onMethod, onOpen)
                HelpTab.FIX -> FixTab(onOpen)
                HelpTab.PHONE -> PhoneTab(onUseAddress)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuideTab(method: InstallMethod, onMethod: (InstallMethod) -> Unit, onOpen: (HelpTopicId) -> Unit) {
    val guide = HelpLibrary.guide(method)
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
        Text(
            "Setting up the server is done on a computer. Read these on the phone beside it, or send them to the computer.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Heading("How is the server installed?", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            InstallMethod.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = m == method,
                    onClick = { onMethod(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, InstallMethod.entries.size),
                ) { Text(m.label) }
            }
        }
        Heading("${method.label} guide, in order", style = MaterialTheme.typography.titleMedium)
    }
    guide.forEachIndexed { i, t -> NavRow("${i + 1}. ${t.title}", t.summary, Icons.AutoMirrored.Rounded.MenuBook) { onOpen(t.id) } }
    OutlinedButton(
        onClick = { send(context, HelpShare.textFor(guide)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.padding(end = Dimens.Space.s))
        Text("Send this guide to my computer")
    }
}

@Composable
private fun FixTab(onOpen: (HelpTopicId) -> Unit) {
    Text(
        "Pick what you are seeing. Each page lists the quickest check first.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    HelpLibrary.troubleshooting().forEach { t -> NavRow(t.title, t.summary, Icons.AutoMirrored.Rounded.HelpOutline) { onOpen(t.id) } }
}

@Composable
private fun PhoneTab(onUseAddress: (String) -> Unit) {
    val viewModel = rememberWelcomeViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
        Heading("Checks on this phone", style = MaterialTheme.typography.titleMedium)
        checkRows(state.checks).forEach { (text, level) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s)) {
                StatusMark(level)
                Text(text)
            }
        }
        Heading("Find Bambuddy", style = MaterialTheme.typography.titleMedium)
        Text(
            "Looks for a Bambuddy server on the Wi-Fi this phone is on. Pick the one found, then press Save settings in Settings.",
            style = MaterialTheme.typography.bodyMedium,
        )
        FindBambuddySection(viewModel) { onUseAddress("http://$it:${BambuddyFinder.DEFAULT_PORT}") }
    }
}

/** Opens Android's share sheet with [text] (titles and source links only), so the user can send it to a computer. */
internal fun send(context: android.content.Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, "Send to my computer"))
}
