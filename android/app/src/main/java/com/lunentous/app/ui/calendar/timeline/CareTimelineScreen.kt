package com.lunentous.app.ui.calendar.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.plant.TimelineEntryFormSheet
import com.lunentous.app.ui.theme.LunentousExtendedTheme
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

private val WEEK_WIDTH = 30.dp
private val LABEL_WIDTH = 96.dp
private val RANGE_STRIP_HEIGHT = 18.dp
private val POINT_STRIP_HEIGHT = 16.dp
private val PLANT_ROW_HEIGHT = RANGE_STRIP_HEIGHT + POINT_STRIP_HEIGHT + 4.dp
private val MONTH_HEADER_HEIGHT = 22.dp
private val WEEK_TICK_HEIGHT = 18.dp
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
                LegendRow(uiState.activities, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

                // No horizontal padding here -- this row is meant to use
                // the full screen width, unlike the text/legend above it.
                Row(modifier = Modifier.weight(1f)) {
                    LabelColumn(uiState.allPlants)
                    TimelineGrid(
                        uiState = uiState,
                        scrollState = scrollState,
                        activitiesById = activitiesById,
                        selectedWeek = viewModel.selectedWeek,
                        onSelectWeek = viewModel::selectWeek,
                        modifier = Modifier.weight(1f),
                    )
                }

                DetailPanel(uiState = uiState, selectedWeek = viewModel.selectedWeek, modifier = Modifier.fillMaxWidth().padding(16.dp))
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
    Column(modifier = Modifier.width(LABEL_WIDTH)) {
        Box(Modifier.fillMaxWidth().size(width = LABEL_WIDTH, height = MONTH_HEADER_HEIGHT))
        plants.forEach { plant ->
            Box(Modifier.fillMaxWidth().size(width = LABEL_WIDTH, height = PLANT_ROW_HEIGHT), contentAlignment = Alignment.CenterStart) {
                Text(
                    plant.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    Column(modifier = modifier.horizontalScroll(scrollState)) {
        MonthHeaderRow(uiState.weeks, selectedWeek, onSelectWeek)
        uiState.allPlants.forEach { plant ->
            PlantLane(plant, uiState, activitiesById, selectedWeek, onSelectWeek)
        }
        WeekTickRow(uiState.weeks, selectedWeek, today, onSelectWeek)
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
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                            Box(Modifier.padding(horizontal = 1.dp).size(6.dp).clip(CircleShape).background(color))
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

/** Shared selection-indicator modifier -- an inset border rather than a
 * background swap, since lane cells already carry activity color; also
 * exposes `selected` via semantics for screen reader/switch access. */
private fun Modifier.selectableCell(isSelected: Boolean): Modifier = this
    .semantics { selected = isSelected }
    .then(
        if (isSelected) {
            Modifier.background(Color.Gray.copy(alpha = 0.12f))
        } else {
            Modifier
        },
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailPanel(uiState: CareTimelineUiState, selectedWeek: Int, modifier: Modifier = Modifier) {
    val colors = LunentousExtendedTheme.colors
    val week = uiState.weeks.getOrNull(selectedWeek)
    val activitiesById = remember(uiState.activities) { uiState.activities.associateBy { it.id } }
    val activeRanges = remember(selectedWeek, uiState.ranges) { uiState.ranges.filter { selectedWeek in it.startWeek..it.endWeek } }
    val activeEvents = remember(selectedWeek, uiState.events) { uiState.events.filter { it.week == selectedWeek } }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    OutlinedCard(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            if (week != null) {
                Text(
                    "Week of ${week.startDate.format(formatter)} – ${week.startDate.plusDays(6).format(formatter)}",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (activeRanges.isEmpty() && activeEvents.isEmpty()) {
                Text(
                    "Routine care only — nothing scheduled this week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                FlowRow(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    activeRanges.forEach { range -> activitiesById[range.activityId]?.let { DetailChip(it, range.plantName) } }
                    activeEvents.forEach { event -> activitiesById[event.activityId]?.let { DetailChip(it, event.plantName) } }
                }
            }
        }
    }
}

@Composable
private fun DetailChip(activity: CareActivity, plantName: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(activity.color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(activity.icon, contentDescription = null, tint = activity.color, modifier = Modifier.size(14.dp))
        Text("${activity.label} · $plantName", style = MaterialTheme.typography.labelSmall, color = activity.color)
    }
}
