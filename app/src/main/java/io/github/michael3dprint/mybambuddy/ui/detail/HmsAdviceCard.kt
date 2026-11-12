package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.HmsStatus
import io.github.michael3dprint.mybambuddy.domain.policy.HmsExplainer
import io.github.michael3dprint.mybambuddy.domain.policy.HmsSeverity
import io.github.michael3dprint.mybambuddy.domain.policy.HmsSummary
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.Spoken
import io.github.michael3dprint.mybambuddy.ui.common.nameMaxLines
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens
import io.github.michael3dprint.mybambuddy.ui.theme.statusColors

/**
 * Printer errors, explained. Collapsed by default to one line per error so the card stays small; tapping it shows how serious each one is,
 * what the printer offers and the wiki link. Clearing the errors asks for confirmation (design 32).
 */
@Composable
fun HmsAdviceCard(
    errors: List<HmsStatus>,
    clearEnabled: Boolean,
    clearReason: String?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val colors = hmsColors(HmsSummary.worst(errors))
    val uriHandler = LocalUriHandler.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val advice = errors.take(MAX_SHOWN).map { HmsExplainer.explain(it) }
    if (confirmClear) {
        ClearErrorsDialog(onConfirm = {
            confirmClear = false
            onClear()
        }, onCancel = { confirmClear = false })
    }
    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = colors.first),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
    ) {
        Column(
            Modifier.padding(horizontal = Dimens.Space.l, vertical = Dimens.Space.s),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.xs),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Heading(
                    "Printer: ${HmsSummary.words(errors)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.second,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide details" else "Show details",
                    tint = colors.second,
                )
            }
            if (!expanded) {
                advice.forEach {
                    Spoken(
                        collapsedLine(severityLabel(it.severity), it.area, it.title),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.second,
                        maxLines = nameMaxLines(),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                advice.forEach { item ->
                    Column(Modifier.padding(bottom = Dimens.Space.s), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Spoken(
                            "${severityLabel(item.severity)} · ${item.area}",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.second,
                        )
                        Text(item.title, style = MaterialTheme.typography.titleSmall, color = colors.second)
                        if (item.options.isNotEmpty()) {
                            Spoken(
                                "The printer offers: ${item.options.joinToString(" · ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.second,
                            )
                        }
                        Text(
                            "Code ${item.codeLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.second,
                        )
                        item.learnMoreUrl?.let { url ->
                            TextButton(onClick = {
                                uriHandler.openUri(url)
                            }) { Text(stringResource(R.string.detail_learn_more_bambu_wiki)) }
                        }
                    }
                }
            }
            if (!expanded) {
                Text("Tap for details or to clear.", style = MaterialTheme.typography.labelSmall, color = colors.second)
            } else {
                Button(onClick = {
                    confirmClear = true
                }, enabled = clearEnabled, modifier = Modifier.fillMaxWidth()) { Text("Clear errors") }
                if (!clearEnabled &&
                    !clearReason.isNullOrBlank()
                ) {
                    Text(clearReason, style = MaterialTheme.typography.bodySmall, color = colors.second)
                }
                Text(
                    "Clearing tells the printer to drop these errors; it does not fix their cause.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.second,
                )
            }
            if (errors.size > MAX_SHOWN) {
                Text(
                    "+ ${errors.size - MAX_SHOWN} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.second,
                )
            }
        }
    }
}

/** The one line shown per error while the card is collapsed. */
internal fun collapsedLine(severity: String, area: String, title: String): String = "$severity · $area: $title"

/** The word for an error's severity (Error, Warning or Notice), shared by the error card and the Printer Status section. */
internal fun severityLabel(severity: HmsSeverity): String = when (severity) {
    HmsSeverity.ERROR -> "Error"
    HmsSeverity.WARNING -> "Warning"
    HmsSeverity.NOTICE -> "Notice"
}

private const val MAX_SHOWN = 5

/** The card's container and content colours for the worst level present: red for an error, amber for a warning, neutral for notices only. */
@Composable
internal fun hmsColors(worst: HmsSeverity?): Pair<Color, Color> = when (worst) {
    HmsSeverity.WARNING -> MaterialTheme.statusColors.paused.container to MaterialTheme.statusColors.paused.content
    HmsSeverity.NOTICE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
}
