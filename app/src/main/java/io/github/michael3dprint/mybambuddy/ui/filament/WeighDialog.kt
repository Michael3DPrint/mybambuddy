package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.policy.SpoolWeighRules
import io.github.michael3dprint.mybambuddy.domain.policy.WeighInput
import io.github.michael3dprint.mybambuddy.ui.common.ConfirmDialog

/** Asks for the total weight on the scale. Update stays off until the number is valid; a high number only warns. */
@Composable
internal fun WeighDialog(spool: Spool, onUpdate: (Double) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val input = if (text.isBlank()) null else SpoolWeighRules.parse(text, spool.labelWeightGrams)
    ConfirmDialog(
        title = stringResource(R.string.weigh_title),
        confirmLabel = stringResource(R.string.weigh_update),
        confirmEnabled = input is WeighInput.Valid,
        onConfirm = { (input as? WeighInput.Valid)?.let { onUpdate(it.grams) } },
        onCancel = onCancel,
    ) {
        Column {
            Text(stringResource(R.string.weigh_explain))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                suffix = { Text("g") },
                isError = input is WeighInput.Invalid,
                supportingText = {
                    when (input) {
                        is WeighInput.Invalid -> Text(input.reason)
                        is WeighInput.Valid -> input.warning?.let { Text(it) }
                        null -> Unit
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Text(stringResource(R.string.weigh_limits))
        }
    }
}
