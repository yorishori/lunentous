package com.lunentous.app.ui.types

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lunentous.app.ui.components.ColorPicker
import com.lunentous.app.ui.components.IconPicker
import com.lunentous.app.ui.theme.LunentousExtendedTheme

/** Shared create/edit sheet for reminder_types and phase_types, mirroring
 * web/src/components/TypeForm.tsx's parameterization by `hasIcon`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeFormSheet(
    noun: String,
    hasIcon: Boolean,
    existing: TypeRow?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String?, color: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = LunentousExtendedTheme.colors

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(existing?.icon) }
    var color by remember { mutableStateOf(existing?.color ?: "#cba6f7") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(if (existing != null) "Edit ${noun.lowercase()}" else "Add ${noun.lowercase()}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(top = 12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )

            if (hasIcon) {
                Text("Icon", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
                IconPicker(value = icon, onChange = { icon = it })
                Spacer(Modifier.padding(top = 12.dp))
            }

            Text("Color", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
            ColorPicker(value = color, onChange = { color = it })

            error?.let { Text(it, color = colors.overdue, modifier = Modifier.padding(top = 12.dp)) }

            Button(
                onClick = { onSave(name.trim(), icon, color) },
                enabled = !isSaving && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(if (existing != null) "Save changes" else "Add ${noun.lowercase()}")
            }
        }
    }
}
