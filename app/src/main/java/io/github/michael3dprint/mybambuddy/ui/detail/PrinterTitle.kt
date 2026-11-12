package io.github.michael3dprint.mybambuddy.ui.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.nameMaxLines
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** The overview's top-bar title: the printer name, with the printer type beside it (design 38). */
@Composable
internal fun PrinterTitle(name: String, model: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Heading(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = nameMaxLines(),
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (!model.isNullOrBlank()) {
            Text(
                text = model,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Dimens.Space.s),
            )
        }
    }
}
