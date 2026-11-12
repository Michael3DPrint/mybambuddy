package io.github.michael3dprint.mybambuddy.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import io.github.michael3dprint.mybambuddy.domain.help.HelpTab
import io.github.michael3dprint.mybambuddy.domain.help.HelpTabs
import io.github.michael3dprint.mybambuddy.domain.help.HelpTopicId
import io.github.michael3dprint.mybambuddy.domain.help.InstallMethod

/** Opens one Setup help page; the index it returns to opens on the tab that page belongs to (design 48). */
val LocalOpenHelp = staticCompositionLocalOf<(HelpTopicId) -> Unit> { {} }

/** Opens the Setup help screen on a given tab. */
val LocalOpenHelpTab = staticCompositionLocalOf<(HelpTab) -> Unit> { {} }

private const val CLOSED = ""
private const val INDEX = "index"

/**
 * Hosts Setup help over [content]: any screen opens it through [LocalOpenHelp] or [LocalOpenHelpTab]; Back goes from a page to the index,
 * then closes. [onUseAddress] receives a server address found on the "This phone" tab, to put into the settings.
 */
@Composable
fun ProvideHelp(onUseAddress: (String) -> Unit, content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(CLOSED) }
    var tab by rememberSaveable { mutableStateOf(HelpTab.GUIDE.name) }
    var method by rememberSaveable { mutableStateOf(InstallMethod.DOCKER.name) }
    CompositionLocalProvider(
        LocalOpenHelp provides {
            tab = HelpTabs.forTopic(it).name
            open = it.name
        },
        LocalOpenHelpTab provides {
            tab = it.name
            open = INDEX
        },
    ) {
        content()
        if (open != CLOSED) {
            BackHandler { open = if (open == INDEX) CLOSED else INDEX }
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val topic = HelpTopicId.values().firstOrNull { it.name == open }
                if (topic == null) {
                    HelpIndexScreen(
                        tab = HelpTab.valueOf(tab),
                        onTab = { tab = it.name },
                        method = InstallMethod.valueOf(method),
                        onMethod = { method = it.name },
                        onOpen = { open = it.name },
                        onUseAddress = {
                            onUseAddress(it)
                            open = CLOSED
                        },
                        onClose = { open = CLOSED },
                    )
                } else {
                    HelpTopicScreen(topic, onBack = { open = INDEX })
                }
            }
        }
    }
}
