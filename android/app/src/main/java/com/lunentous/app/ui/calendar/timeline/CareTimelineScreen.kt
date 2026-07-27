package com.lunentous.app.ui.calendar.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.data.local.entity.ReminderTypeEntity
import com.lunentous.app.data.remote.photoDisplayModel
import com.lunentous.app.data.repository.TimelineEventWithPhotos
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.icons.iconFor
import com.lunentous.app.ui.plant.TimelineEntryFormSheet
import com.lunentous.app.ui.theme.LunentousExtendedTheme
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

private val WEEK_WIDTH = 36.dp
private val LABEL_WIDTH = 104.dp
private val RANGE_STRIP_HEIGHT = 20.dp
private val POINT_STRIP_HEIGHT = 18.dp
private val ROW_GAP = 6.dp
private val PLANT_ROW_HEIGHT = RANGE_STRIP_HEIGHT + POINT_STRIP_HEIGHT + 6.dp
private val MONTH_HEADER_HEIGHT = 26.dp
private val WEEK_TICK_HEIGHT = 22.dp
private const val SCROLL_PAGE_WEEKS = 4

private data class EntryTarget(val initialPhoto: File? = null)

/**
 * Multi-month plant care timeline: rows are plants, columns are weeks
 * (grouped by month). Each plant's row shows its own phase-window range
 * pills (top strip, up to 2 concurrent types stacked) and reminder point
 * dots (bottom strip) together -- the legend above decodes which color/
 * icon belongs to which activity type, since the row itself is now
 * labeled by plant rather than activity. A single Row/Column layout (no
 * orientation branching) with one shared horizontalScroll state keeps the
 * month header, every plant row, and the week-tick row moving in
 * lockstep; a sticky (non-scrolling) label column of plant names sits to
 * their left, and arrow buttons page the shared scroll a few weeks at a
 * time as an alternative to dragging.
 */
@Composable
fun CareTimelineScreen(
    container: AppContainer,
    sharedPhotoFile: File? = null,
    onSharedPhotoConsumed: () -> Unit = {},
    promptNewEntry: Boolean = false,
    onNewEntryPromptConsumed: () -> Unit = {},
) {
    val viewModel: CareTimelineViewModel = viewModel(factory = viewModelFactory { initializer { CareTimelineViewModel(container) } })
    val uiState by viewModel.uiState.collectAsState()
    val colors = LunentousExtendedTheme.colors
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var entryTarget by remember { mutableStateOf<EntryTarget?>(null) }
    val activitiesById = remember(uiState.activities) { uiState.activities.associateBy { it.id } }

    LaunchedEffect(sharedPhotoFile) {
        sharedPhotoFile?.let { file ->
            entryTarget = EntryTarget(initialPhoto = file)
            onSharedPhotoConsumed()
        }
    }
    LaunchedEffect(promptNewEntry) {
        if (promptNewEntry) {
            entryTarget = EntryTarget()
            onNewEntryPromptConsumed()
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { entryTarget = EntryTarget() }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New entry") })
        },
    ) { padding ->
        if (uiState.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Care timeline", style = MaterialTheme.typography.headlineSmall)
                    Row {
                        val pagePx = with(density) { (WEEK_WIDTH * SCROLL_PAGE_WEEKS).roundToPx() }
                        IconButton(onClick = { scope.launch { scrollState.animateScrollTo((scrollState.value - pagePx).coerceAtLeast(0)) } }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Scroll to earlier weeks")
                        }
                        IconButton(onClick = { scope.launch { scrollState.animateScrollTo((scrollState.value + pagePx).coerceAtMost(scrollState.maxValue)) } }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Scroll to later weeks")
                        }
                    }
                }
                LegendRow(uiState.activities, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                // No horizontal padding here -- this row is meant to use
                // the full screen width, unlike the text/legend above it.
                // fillMaxWidth() (not just weight(1f)) on the outer Row
                // makes sure it actually claims the full remaining width
                // rather than only as much as its content needs.
                Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp)) {
                    LabelColumn(uiState.allPlants)
                    // The scrollable content lives in its own Box rather
                    // than putting weight() and horizontalScroll() on the
                    // same node -- combining them directly on one
                    // Column/Row is a known source of Compose layout bugs
                    // where the scroll viewport ends up sized by its own
                    // unconstrained content instead of the weight-assigned
                    // available width, breaking both "fill remaining
                    // width" and scroll-by-button (nothing to animate to
                    // if the viewport already claims its full content
                    // width). The Box gets the width constraint; the
                    // Column inside just scrolls within it.
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        TimelineGrid(
                            uiState = uiState,
                            scrollState = scrollState,
                            activitiesById = activitiesById,
                            selectedWeek = viewModel.selectedWeek,
                            onSelectWeek = viewModel::selectWeek,
                        )
                    }
                }

                DetailPanel(
                    uiState = uiState,
                    selectedWeek = viewModel.selectedWeek,
                    baseUrl = container.sessionStore.getBaseUrl(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }

    entryTarget?.let { target ->
        TimelineEntryFormSheet(
            reminderTypes = uiState.allReminderTypes,
            existing = null,
            plants = uiState.allPlants,
            initialPhotos = listOfNotNull(target.initialPhoto),
            baseUrl = container.sessionStore.getBaseUrl(),
            isSaving = viewModel.isSavingEntry,
            error = viewModel.entryError,
            onDismiss = { entryTarget = null },
            onSave = { plantLocalId, eventDate, reminderTypeLocalId, text, photoFiles ->
                viewModel.saveEntry(plantLocalId, eventDate, reminderTypeLocalId, text, photoFiles) { entryTarget = null }
            },
            onDelete = null,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendRow(activities: List<CareActivity>, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        activities.forEach { activity ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(activity.color))
                Text(activity.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LabelColumn(plants: List<PlantEntity>) {
    val colors = LunentousExtendedTheme.colors
    // Same vertical arrangement as TimelineGrid's Column, and the same
    // number of children in the same order (header spacer, one per plant,
    // tick spacer) -- that's what keeps each plant's label lined up with
    // its row in the scrollable grid.
    Column(modifier = Modifier.width(LABEL_WIDTH), verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        Box(Modifier.fillMaxWidth().size(width = LABEL_WIDTH, height = MONTH_HEADER_HEIGHT))
        plants.forEach { plant ->
            Box(Modifier.fillMaxWidth().size(width = LABEL_WIDTH, height = PLANT_ROW_HEIGHT), contentAlignment = Alignment.CenterStart) {
                Text(
                    plant.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().size(width = LABEL_WIDTH, height = WEEK_TICK_HEIGHT))
    }
}

@Composable
private fun TimelineGrid(
    uiState: CareTimelineUiState,
    scrollState: ScrollState,
    activitiesById: Map<String, CareActivity>,
    selectedWeek: Int,
    onSelectWeek: (Int) -> Unit,
) {
    val colors = LunentousExtendedTheme.colors
    val today = remember { LocalDate.now() }
    val plantCount = uiState.allPlants.size
    // Exactly mirrors the row sequence below (header, one per plant, tick)
    // so the overlay's height lines up with the real content precisely --
    // computed instead of measured, since every row height here is
    // already a fixed constant.
    val totalHeight = MONTH_HEADER_HEIGHT + WEEK_TICK_HEIGHT +
        PLANT_ROW_HEIGHT * plantCount +
        ROW_GAP * (plantCount + 1)

    Box(modifier = Modifier.horizontalScroll(scrollState)) {
        // One continuous rounded highlight for the whole selected column
        // (month header through the week-tick row), instead of each row
        // drawing its own separate selected box -- drawn first so the
        // actual row content (pills/dots/text) paints on top of it.
        if (uiState.weeks.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .offset(x = WEEK_WIDTH * selectedWeek)
                    .width(WEEK_WIDTH)
                    .height(totalHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.accentSoft)
                    .border(1.dp, colors.accent, RoundedCornerShape(percent = 50)),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            MonthHeaderRow(uiState.weeks, selectedWeek, onSelectWeek)
            uiState.allPlants.forEach { plant ->
                PlantLane(plant, uiState, activitiesById, selectedWeek, onSelectWeek)
            }
            WeekTickRow(uiState.weeks, selectedWeek, today, onSelectWeek)
        }
    }
}

@Composable
private fun MonthHeaderRow(weeks: List<WeekInfo>, selectedWeek: Int, onSelectWeek: (Int) -> Unit) {
    Row {
        weeks.forEach { week ->
            Box(
                modifier = Modifier
                    .size(width = WEEK_WIDTH, height = MONTH_HEADER_HEIGHT)
                    .selectableCell(week.index == selectedWeek)
                    .clickable(onClickLabel = "Select week of ${week.startDate}") { onSelectWeek(week.index) },
            ) {
                week.monthLabel?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true))
                }
            }
        }
    }
}

/** One row per plant, combining that plant's own range activities (top
 * strip, phase windows) and point activities (bottom strip, reminder
 * occurrences) rather than one row per activity type -- the legend above
 * the grid is what decodes each strip segment/dot's color back to an
 * activity name, since the row label is now the plant. Capped at 2
 * concurrent range types per week (stacked as two thinner segments) --
 * a single plant having 3+ overlapping phase windows at once is not a
 * case worth the extra layout complexity to handle exactly. */
@Composable
private fun PlantLane(
    plant: PlantEntity,
    uiState: CareTimelineUiState,
    activitiesById: Map<String, CareActivity>,
    selectedWeek: Int,
    onSelectWeek: (Int) -> Unit,
) {
    val plantRanges = remember(plant.localId, uiState.ranges) { uiState.ranges.filter { it.plantLocalId == plant.localId } }
    val plantEvents = remember(plant.localId, uiState.events) { uiState.events.filter { it.plantLocalId == plant.localId } }
    val rangeActivityIds = remember(plantRanges) { plantRanges.map { it.activityId }.distinct() }

    Row {
        uiState.weeks.forEach { week ->
            val activeRangeIds = rangeActivityIds.filter { id -> plantRanges.any { it.activityId == id && week.index in it.startWeek..it.endWeek } }
            val activeEventIds = plantEvents.filter { it.week == week.index }.map { it.activityId }.distinct()
            val description = "${plant.name}, week of ${week.startDate}" +
                if (activeRangeIds.isEmpty() && activeEventIds.isEmpty()) ", nothing scheduled" else ""

            Box(
                modifier = Modifier
                    .size(width = WEEK_WIDTH, height = PLANT_ROW_HEIGHT)
                    .selectableCell(week.index == selectedWeek)
                    .clickable(onClickLabel = description) { onSelectWeek(week.index) },
            ) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.fillMaxWidth().height(RANGE_STRIP_HEIGHT)) {
                        val shown = activeRangeIds.take(2)
                        val subHeight = if (shown.size > 1) RANGE_STRIP_HEIGHT / 2 else RANGE_STRIP_HEIGHT
                        shown.forEach { id ->
                            val color = activitiesById[id]?.color ?: Color.Gray
                            val ranges = plantRanges.filter { it.activityId == id }
                            val roundStart = ranges.none { (week.index - 1) in it.startWeek..it.endWeek }
                            val roundEnd = ranges.none { (week.index + 1) in it.startWeek..it.endWeek }
                            val shape = RoundedCornerShape(
                                topStart = if (roundStart) 6.dp else 0.dp,
                                bottomStart = if (roundStart) 6.dp else 0.dp,
                                topEnd = if (roundEnd) 6.dp else 0.dp,
                                bottomEnd = if (roundEnd) 6.dp else 0.dp,
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(subHeight)
                                    .padding(horizontal = if (roundStart || roundEnd) 1.dp else 0.dp)
                                    .clip(shape)
                                    .background(color.copy(alpha = 0.75f)),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(POINT_STRIP_HEIGHT), horizontalArrangement = Arrangement.Center) {
                        activeEventIds.take(3).forEach { id ->
                            val color = activitiesById[id]?.color ?: Color.Gray
                            Box(Modifier.padding(horizontal = 1.5.dp).size(8.dp).clip(CircleShape).background(color))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekTickRow(weeks: List<WeekInfo>, selectedWeek: Int, today: LocalDate, onSelectWeek: (Int) -> Unit) {
    val colors = LunentousExtendedTheme.colors
    Row {
        weeks.forEach { week ->
            val isToday = !today.isBefore(week.startDate) && today.isBefore(week.startDate.plusDays(7))
            Box(
                modifier = Modifier
                    .size(width = WEEK_WIDTH, height = WEEK_TICK_HEIGHT)
                    .selectableCell(week.index == selectedWeek)
                    .clickable(onClickLabel = "Select week of ${week.startDate}") { onSelectWeek(week.index) },
                contentAlignment = Alignment.Center,
            ) {
                if (isToday) {
                    Box(Modifier.size(width = 2.dp, height = WEEK_TICK_HEIGHT).background(colors.accent))
                } else if (week.index % 4 == 0) {
                    Text("${week.index + 1}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
            }
        }
    }
}

/** No visual styling here anymore -- the whole selected column is drawn
 * once as a single overlay in TimelineGrid (see totalHeight/offset
 * there), not per-cell, so this only exposes `selected` via semantics for
 * screen reader/switch access. */
private fun Modifier.selectableCell(isSelected: Boolean): Modifier = this
    .semantics { selected = isSelected }

/** Shows every timeline entry actually logged during the selected week
 * (not the phase-window/reminder-occurrence summary the grid itself
 * already shows visually) -- capped and internally scrollable so a
 * photo-heavy week doesn't push the grid above off-screen. */
@Composable
private fun DetailPanel(uiState: CareTimelineUiState, selectedWeek: Int, baseUrl: String?, modifier: Modifier = Modifier) {
    val colors = LunentousExtendedTheme.colors
    val week = uiState.weeks.getOrNull(selectedWeek)
    val plantsById = remember(uiState.allPlants) { uiState.allPlants.associateBy { it.localId } }
    val reminderTypesById = remember(uiState.allReminderTypes) { uiState.allReminderTypes.associateBy { it.localId } }
    val weekEntries = remember(week, uiState.timelineEntries) {
        week?.let { w ->
            val weekEnd = w.startDate.plusDays(6)
            uiState.timelineEntries.filter { entry ->
                val date = LocalDate.parse(entry.event.eventDate)
                !date.isBefore(w.startDate) && !date.isAfter(weekEnd)
            }
        }.orEmpty()
    }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (week != null) {
                Text(
                    "Week of ${week.startDate.format(formatter)} – ${week.startDate.plusDays(6).format(formatter)}",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (weekEntries.isEmpty()) {
                Text(
                    "No entries logged this week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    weekEntries.forEach { entry ->
                        val plant = plantsById[entry.event.plantLocalId]
                        val type = entry.event.reminderTypeLocalId?.let { reminderTypesById[it] }
                        EntryCard(entry = entry, plantName = plant?.name ?: "Unknown plant", reminderType = type, baseUrl = baseUrl)
                    }
                }
            }
        }
    }
}

/** One card per logged entry -- untyped (journal-only) entries show no
 * icon at all, since there's no reminder type to represent. */
@Composable
private fun EntryCard(entry: TimelineEventWithPhotos, plantName: String, reminderType: ReminderTypeEntity?, baseUrl: String?) {
    val colors = LunentousExtendedTheme.colors
    val typeColor = reminderType?.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: colors.accent

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reminderType != null) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(typeColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(iconFor(reminderType.icon), contentDescription = null, tint = typeColor, modifier = Modifier.size(14.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(plantName, style = MaterialTheme.typography.bodyMedium)
                    reminderType?.let { Text(it.name, style = MaterialTheme.typography.labelSmall, color = typeColor) }
                }
                Text(entry.event.eventDate, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
            entry.event.text?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.padding(top = 6.dp))
            }
            if (entry.photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    items(entry.photos) { photo ->
                        AsyncImage(
                            model = photoDisplayModel(baseUrl, photo),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
        }
    }
}
