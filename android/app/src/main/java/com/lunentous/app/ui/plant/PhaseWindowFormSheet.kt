package com.lunentous.app.ui.plant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lunentous.app.data.local.entity.PhaseTypeEntity
import com.lunentous.app.data.local.entity.PlantPhaseWindowEntity
import com.lunentous.app.ui.theme.LunentousExtendedTheme

/** Shared add/edit sheet for phase windows, mirroring
 * web/src/components/PhaseWindowForm.tsx's field set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhaseWindowFormSheet(
    phaseTypes: List<PhaseTypeEntity>,
    existing: PlantPhaseWindowEntity?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (phaseTypeLocalId: Long, startMonth: Int, startDay: Int, endMonth: Int, endDay: Int, notes: String?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = LunentousExtendedTheme.colors

    var typeExpanded by remember { mutableStateOf(false) }
    var selectedTypeLocalId by remember { mutableStateOf(existing?.phaseTypeLocalId ?: phaseTypes.firstOrNull()?.localId ?: 0L) }
    var startMonth by remember { mutableStateOf(existing?.startMonth ?: 1) }
    var startDay by remember { mutableStateOf(existing?.startDay ?: 1) }
    var endMonth by remember { mutableStateOf(existing?.endMonth ?: 1) }
    var endDay by remember { mutableStateOf(existing?.endDay ?: 1) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    val selectedType = phaseTypes.find { it.localId == selectedTypeLocalId }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(if (existing != null) "Edit phase window" else "Add phase window", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(top = 12.dp))

            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                OutlinedTextField(
                    value = selectedType?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Phase type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    phaseTypes.forEach { type ->
                        DropdownMenuItem(text = { Text(type.name) }, onClick = { selectedTypeLocalId = type.localId; typeExpanded = false })
                    }
                }
            }

            Text("Window", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(startMonth, { startMonth = it }, "Start mo", 66.dp)
                Spacer(Modifier.padding(start = 4.dp))
                NumberField(startDay, { startDay = it }, "Day", 56.dp)
                Text(" – ", modifier = Modifier.padding(horizontal = 4.dp))
                NumberField(endMonth, { endMonth = it }, "End mo", 66.dp)
                Spacer(Modifier.padding(start = 4.dp))
                NumberField(endDay, { endDay = it }, "Day", 56.dp)
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            error?.let { Text(it, color = colors.overdue, modifier = Modifier.padding(top = 12.dp)) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete, enabled = !isSaving, colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.overdue)) {
                        Text("Delete")
                    }
                }
                Button(
                    onClick = { onSave(selectedTypeLocalId, startMonth, startDay, endMonth, endDay, notes.trim().ifBlank { null }) },
                    enabled = !isSaving && selectedTypeLocalId != 0L,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (existing != null) "Save changes" else "Add phase window")
                }
            }
        }
    }
}

@Composable
private fun NumberField(value: Int, onChange: (Int) -> Unit, label: String, width: Dp) {
    OutlinedTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { text -> onChange(text.filter { it.isDigit() }.toIntOrNull() ?: 0) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(width),
    )
}
