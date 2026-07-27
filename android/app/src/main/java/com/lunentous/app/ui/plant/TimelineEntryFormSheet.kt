package com.lunentous.app.ui.plant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.data.local.entity.ReminderTypeEntity
import com.lunentous.app.data.remote.photoDisplayModel
import com.lunentous.app.data.repository.TimelineEventWithPhotos
import com.lunentous.app.ui.camera.rememberCameraCaptureLauncher
import com.lunentous.app.ui.theme.LunentousExtendedTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Plant Detail passes `fixedPlantLocalId` and no `plants` list, so no
 * selector shows (mirrors TimelineEntryForm.tsx's `plantId` prop). Calendar
 * passes `plants` so the user picks one up front (mirrors its `plants`
 * prop) -- hidden when editing an existing entry either way, since the
 * plant can't change after the fact.
 *
 * Photos: capturing on a new (unsaved) entry queues the file into `onSave`;
 * capturing on an existing entry calls `onAppendPhotos` immediately, since
 * that's how TimelineRepository.appendPhotos already works independently
 * of the rest of the entry's fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineEntryFormSheet(
    reminderTypes: List<ReminderTypeEntity>,
    existing: TimelineEventWithPhotos?,
    plants: List<PlantEntity> = emptyList(),
    fixedPlantLocalId: Long? = null,
    initialDate: String? = null,
    initialPhotos: List<File> = emptyList(),
    baseUrl: String? = null,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (plantLocalId: Long, eventDate: String, reminderTypeLocalId: Long?, text: String?, photoFiles: List<File>) -> Unit,
    onDelete: (() -> Unit)?,
    onAppendPhotos: ((eventLocalId: Long, photoFiles: List<File>) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = LunentousExtendedTheme.colors

    var eventDate by remember { mutableStateOf(existing?.event?.eventDate ?: initialDate ?: LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var plantExpanded by remember { mutableStateOf(false) }
    var selectedTypeLocalId by remember { mutableStateOf(existing?.event?.reminderTypeLocalId) }
    var selectedPlantLocalId by remember {
        mutableStateOf(existing?.event?.plantLocalId ?: fixedPlantLocalId ?: plants.firstOrNull()?.localId ?: 0L)
    }
    var text by remember { mutableStateOf(existing?.event?.text ?: "") }
    var pendingPhotos by remember { mutableStateOf(initialPhotos) }

    val takePhoto = rememberCameraCaptureLauncher { file ->
        if (existing != null && onAppendPhotos != null) {
            onAppendPhotos(existing.event.localId, listOf(file))
        } else {
            pendingPhotos = pendingPhotos + file
        }
    }

    val selectedType = reminderTypes.find { it.localId == selectedTypeLocalId }
    val selectedPlant = plants.find { it.localId == selectedPlantLocalId }
    val showPlantPicker = plants.isNotEmpty() && existing == null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(if (existing != null) "Edit timeline entry" else "Log timeline entry", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(top = 12.dp))

            if (showPlantPicker) {
                ExposedDropdownMenuBox(expanded = plantExpanded, onExpandedChange = { plantExpanded = it }) {
                    OutlinedTextField(
                        value = selectedPlant?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Plant") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = plantExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).padding(bottom = 10.dp),
                    )
                    ExposedDropdownMenu(expanded = plantExpanded, onDismissRequest = { plantExpanded = false }) {
                        plants.forEach { plant ->
                            DropdownMenuItem(text = { Text(plant.name) }, onClick = { selectedPlantLocalId = plant.localId; plantExpanded = false })
                        }
                    }
                }
            }

            OutlinedTextField(
                value = eventDate,
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick date")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )

            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                OutlinedTextField(
                    value = selectedType?.name ?: "Journal note only",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Reminder type (optional)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    DropdownMenuItem(text = { Text("Journal note only") }, onClick = { selectedTypeLocalId = null; typeExpanded = false })
                    reminderTypes.forEach { type ->
                        DropdownMenuItem(text = { Text(type.name) }, onClick = { selectedTypeLocalId = type.localId; typeExpanded = false })
                    }
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )

            Text("Photos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                existing?.photos?.let { photos ->
                    items(photos, key = { "existing-${it.localId}" }) { photo ->
                        AsyncImage(
                            model = photoDisplayModel(baseUrl, photo),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
                items(pendingPhotos, key = { it.absolutePath }) { file ->
                    Box {
                        AsyncImage(
                            model = file,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                        )
                        IconButton(
                            onClick = { pendingPhotos = pendingPhotos - file },
                            modifier = Modifier.size(20.dp).align(Alignment.TopEnd),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = colors.overdue)
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = takePhoto, modifier = Modifier.size(64.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = "Take photo")
                    }
                }
            }

            error?.let { Text(it, color = colors.overdue, modifier = Modifier.padding(top = 12.dp)) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete, enabled = !isSaving, colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.overdue)) {
                        Text("Delete")
                    }
                }
                Button(
                    onClick = { onSave(selectedPlantLocalId, eventDate, selectedTypeLocalId, text.trim().ifBlank { null }, pendingPhotos) },
                    enabled = !isSaving && selectedPlantLocalId != 0L,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (existing != null) "Save changes" else "Add entry")
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = runCatching { LocalDate.parse(eventDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        eventDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
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
