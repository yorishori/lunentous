package com.lunentous.app.ui.plant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.theme.LunentousExtendedTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/**
 * Shared create/edit sheet, mirroring web/src/components/PlantForm.tsx's
 * field set -- avatar photo capture is deferred to the phase-6 camera
 * work (Android plan's Build ordering), so it's not included here yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantFormSheet(
    container: AppContainer,
    existing: PlantEntity?,
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = LunentousExtendedTheme.colors

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var species by remember { mutableStateOf(existing?.species ?: "") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var acquiredDate by remember { mutableStateOf(existing?.acquiredDate ?: "") }
    var generalNotes by remember { mutableStateOf(existing?.generalNotes ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(if (existing != null) "Edit plant" else "Add plant", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(top = 12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
            OutlinedTextField(
                value = species,
                onValueChange = { species = it },
                label = { Text("Species") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
            OutlinedTextField(
                value = acquiredDate,
                onValueChange = {},
                readOnly = true,
                label = { Text("Acquired date") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick date")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
            OutlinedTextField(
                value = generalNotes,
                onValueChange = { generalNotes = it },
                label = { Text("General notes") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )

            error?.let { Text(it, color = colors.overdue, modifier = Modifier.padding(bottom = 10.dp)) }

            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "Name is required"
                        return@Button
                    }
                    saving = true
                    error = null
                    scope.launch {
                        val result = if (existing != null) {
                            container.plantRepository.updatePlant(
                                existing.localId,
                                name.trim(),
                                species.trim().ifBlank { null },
                                location.trim().ifBlank { null },
                                acquiredDate.ifBlank { null },
                                generalNotes.trim().ifBlank { null },
                            )
                        } else {
                            container.plantRepository.createPlant(
                                name.trim(),
                                species.trim().ifBlank { null },
                                location.trim().ifBlank { null },
                                acquiredDate.ifBlank { null },
                                generalNotes.trim().ifBlank { null },
                            )
                        }
                        saving = false
                        result.onSuccess { plant -> onSaved(plant.localId) }
                        result.onFailure { error = it.message ?: "Failed to save plant" }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (existing != null) "Save changes" else "Create plant")
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = acquiredDate.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        acquiredDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
