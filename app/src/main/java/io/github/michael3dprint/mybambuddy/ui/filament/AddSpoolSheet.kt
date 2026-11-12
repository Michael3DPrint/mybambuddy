package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.CatalogFilament
import io.github.michael3dprint.mybambuddy.ui.common.Heading
import io.github.michael3dprint.mybambuddy.ui.common.nameMaxLines
import io.github.michael3dprint.mybambuddy.ui.theme.Dimens

/** The Add spool form: catalogue search, the fields of the draft, the first problem with it, and the Save button. */
@Composable
internal fun AddSpoolSheet(state: AddSpoolState, vm: FilamentViewModel, onScan: () -> Unit, scanning: Boolean) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Dimens.Space.l, vertical = Dimens.Space.s),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space.m),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item { Heading(stringResource(R.string.filament_add_spool), style = MaterialTheme.typography.titleLarge) }
        item {
            OutlinedButton(onClick = onScan, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
                Text(if (scanning) "Scanning…" else "Scan barcode / QR on the box or spool")
            }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChanged,
                label = { Text(stringResource(R.string.filament_search_catalog_e_g_bambu)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.searching) item { CircularProgressIndicator(Modifier.size(24.dp)) }
        state.searchMessage?.let { msg ->
            item { Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.results, key = { it.id }) { CatalogRow(it) { vm.pick(it) } }

        item { Heading(stringResource(R.string.filament_details), style = MaterialTheme.typography.titleMedium) }
        item { Field("Brand", state.draft.brand) { v -> vm.updateDraft { it.copy(brand = v) } } }
        item { Field("Material (e.g. PLA) *", state.draft.material) { v -> vm.updateDraft { it.copy(material = v) } } }
        item { Field("Type (e.g. Basic, Matte)", state.draft.subtype) { v -> vm.updateDraft { it.copy(subtype = v) } } }
        item { Field("Colour name", state.draft.colorName) { v -> vm.updateDraft { it.copy(colorName = v) } } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space.m)) {
                Box(Modifier.weight(1f)) {
                    Field("Colour hex (RRGGBB)", state.draft.colorHex) { v ->
                        vm.updateDraft { it.copy(colorHex = v.filter { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' }.take(8)) }
                    }
                }
                ColorSwatch(state.draft.colorHex, Modifier.size(40.dp))
            }
        }
        item {
            Field("Net weight (g)", state.draft.labelWeightGrams.toString()) { v ->
                vm.updateDraft { it.copy(labelWeightGrams = v.filter { c -> c.isDigit() }.take(6).toIntOrNull() ?: 0) }
            }
        }
        state.problem?.let { hint ->
            item { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
        state.saveError?.let { err ->
            item { Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = vm::closeAdd) { Text(stringResource(R.string.detail_cancel)) }
                Spacer(Modifier.size(8.dp))
                Button(onClick = vm::save, enabled = state.canSave) { Text(if (state.saving) "Saving…" else "Save") }
            }
        }
        item { Spacer(Modifier.height(Dimens.Space.xl)) }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = {
        Text(label)
    }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun CatalogRow(item: CatalogFilament, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorSwatch(item.colorHex, Modifier.size(28.dp))
        Spacer(Modifier.size(12.dp))
        Column {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = nameMaxLines(), overflow = TextOverflow.Ellipsis)
            Text("${item.weightGrams} g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
