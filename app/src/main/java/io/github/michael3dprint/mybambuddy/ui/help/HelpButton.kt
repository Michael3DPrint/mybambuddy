package io.github.michael3dprint.mybambuddy.ui.help

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.domain.help.HelpTab
import io.github.michael3dprint.mybambuddy.domain.help.HelpTopicId

/** The Help link (design 48, pattern P7): a question-mark icon and the word, opening the page for [topic], or "Fix a problem" when null. */
@Composable
fun HelpButton(topic: HelpTopicId?, label: String = "Help") {
    val openTopic = LocalOpenHelp.current
    val openTab = LocalOpenHelpTab.current
    TextButton(onClick = { if (topic != null) openTopic(topic) else openTab(HelpTab.FIX) }) {
        Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 6.dp))
    }
}
