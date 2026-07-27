package com.lunentous.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.lunentous.app.data.remote.buildPhotoUrl
import com.lunentous.app.data.repository.TimelineEventWithPhotos
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.components.ConfirmDialog
import com.lunentous.app.ui.icons.iconFor
import com.lunentous.app.ui.plant.TimelineEntryFormSheet
import com.lunentous.app.ui.theme.LunentousExtendedTheme
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private sealed interface EntrySheetTarget {
    data class Create(val initialDate: String?, val initialPhoto: File? = null) : EntrySheetTarget
    data class Edit(val event: TimelineEventWithPhotos) : EntrySheetTarget
}

/** Mirrors web/src/pages/Calendar.tsx, adapted to mobile: day-click shows a
 * detail panel (phases + entries) rather than an inline create form --
 * creation only ever happens via the top "New entry" button, per the
 * Android plan.
 *
 * sharedPhotoFile arrives from a share-to-app intent (see
 * ui/nav/DeepLinkTarget.ShareImage, imported to a durable File by
 * MainScaffold before this screen ever sees it) -- when present, opens the
 * new-entry sheet immediately with that photo pre-attached rather than
 * waiting for the user to tap "New entry" themselves. promptNewEntry is
 * the same idea without a photo -- set by the widget's "+" button and the
 * NewTimelineEntry app shortcut (see ui/nav/DeepLinkTarget), both of which
 * want to land directly on "pick a plant and write an entry" rather than
 * just the Calendar screen.
 */
@Composable
fun CalendarScreen(
    container: AppContainer,
    sharedPhotoFile: File? = null,
    onSharedPhotoConsumed: () -> Unit = {},
    promptNewEntry: Boolean = false,
    onNewEntryPromptConsumed: () -> Unit = {},
) {
    val viewModel: CalendarViewModel = viewModel(factory = viewModelFactory { initializer { CalendarViewModel(container) } })
    val uiState by viewModel.uiState.collectAsState()
    val selectedPlantIds by viewModel.selectedPlantLocalIds.collectAsState()
    val colors = LunentousExtendedTheme.colors
    val baseUrl = container.sessionStore.getBaseUrl()

    var entryTarget by remember { mutableStateOf<EntrySheetTarget?>(null) }
    var deletingEvent by remember { mutableStateOf<TimelineEventWithPhotos?>(null) }

    LaunchedEffect(sharedPhotoFile) {
        sharedPhotoFile?.let { file ->
            entryTarget = EntrySheetTarget.Create(initialDate = null, initialPhoto = file)
            onSharedPhotoConsumed()
        }
    }

    LaunchedEffect(promptNewEntry) {
        if (promptNewEntry) {
            entryTarget = EntrySheetTarget.Create(initialDate = null)
            onNewEntryPromptConsumed()
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { entryTarget = EntrySheetTarget.Create(viewModel.selectedDay) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New entry") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Calendar", style = MaterialTheme.typography.headlineSmall)
                PlantMultiSelect(plants = uiState.allPlants, selected = selectedPlantIds, onChange = viewModel::setSelectedPlantIds)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::prevMonth) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month") }
                Text(
                    monthYearLabel(viewModel.viewYear, viewModel.viewMonth),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                IconButton(onClick = viewModel::nextMonth) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next month") }
            }

            Text(
                "Solid dots are due dates, ringed dots are projected occurrences, and filled rings are logged entries. Tap a day for details.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            CalendarGrid(
                year = viewModel.viewYear,
                month = viewModel.viewMonth,
                markersByDate = uiState.markersByDate,
                phaseBandsByDate = uiState.phaseBandsByDate,
                selectedDay = viewModel.selectedDay,
                onDayClick = viewModel::selectDay,
            )

            viewModel.selectedDay?.let { day ->
                DayDetailPanel(
                    day = day,
                    phases = uiState.phaseBandsByDate[day].orEmpty(),
                    entries = uiState.dayDetailsByDate[day].orEmpty(),
                    baseUrl = baseUrl,
                    onEditEntry = { event -> entryTarget = EntrySheetTarget.Edit(event) },
                    onDeleteEntry = { event -> deletingEvent = event },
                )
            }
        }
    }

    entryTarget?.let { target ->
        val existing = (target as? EntrySheetTarget.Edit)?.event
        TimelineEntryFormSheet(
            reminderTypes = uiState.reminderTypes,
            existing = existing,
            plants = if (existing == null) uiState.allPlants else emptyList(),
            initialDate = (target as? EntrySheetTarget.Create)?.initialDate,
            initialPhotos = listOfNotNull((target as? EntrySheetTarget.Create)?.initialPhoto),
            baseUrl = baseUrl,
            isSaving = viewModel.isSavingEntry,
            error = viewModel.entryError,
            onDismiss = { entryTarget = null },
            onSave = { plantLocalId, eventDate, reminderTypeLocalId, text, photoFiles ->
                viewModel.saveEntry(plantLocalId, existing?.event?.localId, eventDate, reminderTypeLocalId, text, photoFiles) {
                    entryTarget = null
                }
            },
            onDelete = existing?.let { e ->
                { viewModel.deleteEntry(e.event.plantLocalId, e.event.localId) { entryTarget = null } }
            },
            onAppendPhotos = { eventLocalId, files -> viewModel.appendPhotos(eventLocalId, files) },
        )
    }

    ConfirmDialog(
        open = deletingEvent != null,
        title = "Delete timeline entry?",
        message = "This permanently removes this entry and its photos, and recalculates the reminder if it was tagged.",
        confirmLabel = "Delete",
        pending = viewModel.isSavingEntry,
        onConfirm = {
            deletingEvent?.let { e -> viewModel.deleteEntry(e.event.plantLocalId, e.event.localId) { deletingEvent = null } }
        },
        onDismiss = { deletingEvent = null },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayDetailPanel(
    day: String,
    phases: List<PhaseBand>,
    entries: List<DayDetailItem>,
    baseUrl: String?,
    onEditEntry: (TimelineEventWithPhotos) -> Unit,
    onDeleteEntry: (TimelineEventWithPhotos) -> Unit,
) {
    val colors = LunentousExtendedTheme.colors

    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(formatLongDate(day), style = MaterialTheme.typography.titleMedium)

            if (phases.isNotEmpty()) {
                Text("Active phases", style = MaterialTheme.typography.labelSmall, color = colors.textMuted, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    phases.forEach { band -> PhaseChip(band) }
                }
            }

            Text("Entries", style = MaterialTheme.typography.labelSmall, color = colors.textMuted, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            if (entries.isEmpty()) {
                Text("Nothing for this day.", color = colors.textMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entries.forEach { item ->
                        when (item) {
                            is DayDetailItem.Logged -> LoggedEntryCard(item, baseUrl, onEditEntry, onDeleteEntry)
                            is DayDetailItem.Reminder -> ReminderDetailRow(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseChip(band: PhaseBand) {
    val colors = LunentousExtendedTheme.colors
    val color = band.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: colors.accent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(band.label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ReminderDetailRow(item: DayDetailItem.Reminder) {
    val colors = LunentousExtendedTheme.colors
    val typeColor = item.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: colors.accent

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(typeColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconFor(item.icon), contentDescription = null, tint = typeColor, modifier = Modifier.size(16.dp))
        }
        Column {
            Text("${item.plantName} — ${item.reminderTypeName}", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (item.kind == MarkerKind.DUE) "Scheduled" else "Projected (assumes on-time completion)",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun LoggedEntryCard(
    item: DayDetailItem.Logged,
    baseUrl: String?,
    onEdit: (TimelineEventWithPhotos) -> Unit,
    onDelete: (TimelineEventWithPhotos) -> Unit,
) {
    val colors = LunentousExtendedTheme.colors
    val typeColor = item.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: colors.accent

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.plantName, style = MaterialTheme.typography.bodyMedium)
                    item.typeName?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = typeColor)
                    }
                }
                Row {
                    IconButton(onClick = { onEdit(item.event) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit entry", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { onDelete(item.event) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete entry", tint = colors.overdue, modifier = Modifier.size(16.dp))
                    }
                }
            }
            item.event.event.text?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.padding(top = 6.dp))
            }
            if (item.event.photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    items(item.event.photos) { photo ->
                        AsyncImage(
                            model = buildPhotoUrl(baseUrl, photo.remoteFilePath),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
        }
    }
}

private fun monthYearLabel(year: Int, month: Int): String =
    LocalDate.of(year, month, 1).format(DateTimeFormatter.ofPattern("MMMM yyyy"))

private fun formatLongDate(iso: String): String =
    LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
