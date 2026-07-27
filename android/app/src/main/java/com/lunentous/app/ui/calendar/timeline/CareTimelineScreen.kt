package com.lunentous.app.ui.calendar.timeline

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.plant.TimelineEntryFormSheet
import com.lunentous.app.ui.theme.LunentousExtendedTheme
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val WEEK_WIDTH = 30.dp
private val LABEL_WIDTH = 96.dp
private val LANE_HEIGHT = 32.dp
private val MONTH_HEADER_HEIGHT = 22.dp
private val WEEK_TICK_HEIGHT = 18.dp

private data class EntryTarget(val initialPhoto: File? = null)

/**
 * Multi-month plant care timeline: phase windows render as range pills (one
 * lane per phase type, merged across plants), reminder occurrences render
 * as point dots (one lane per reminder type). A single Row/Column layout
 * (no orientation branching) with one shared horizontalScroll state keeps
 * the month header, every lane, and the week-tick row moving in lockstep;
 * a sticky (non-scrolling) label column sits to their left.
 *
 * Deliberate simplification vs. the original spec: overlapping ranges from
 * different plants on the same activity lane are shown as one merged pill
 * (the union of active weeks) rather than stacked sub-bars -- the detail
 * panel below still enumerates every contributing plant for the selected
 * week, so nothing is actually lost, it just isn't drawn as parallel bars.
 * That tradeoff was chosen to keep row heights (and thus the sticky label
 * column's sync with the scrollable grid) trivially uniform.
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
    var entryTarget by remember { mutableStateOf<EntryTarget?>(null) }

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
                Text("Care timeline", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp))
                LegendRow(uiState.activities, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

                Row(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    LabelColumn(uiState.activities)
                    TimelineGrid(
                        uiState = uiState,
                        scrollState = scrollState,
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
private fun LabelColumn(activities: List<CareActivity>) {
    val colors = LunentousExtendedTheme.colors
    Column(modifier = Modifier.width(LABEL_WIDTH)) {
        Box(Modifier.fillMaxWidth().size(width = LABEL_WIDTH, height = MONTH_HEADER_HEIGHT))
        activities.forEach { activity ->
            Box(Modifier.fillMaxWidth().size(width = LABEL_WIDTH, height = LANE_HEIGHT), contentAlignment = Alignment.CenterStart) {
                Text(
                    activity.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.text,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.fillMaxWidth().size(width = LABEL_WIDTH, height = WEEK_TICK_HEIGHT))
    }
}

@Composable
private fun TimelineGrid(
    uiState: CareTimelineUiState,
    scrollState: androidx.compose.foundation.ScrollState,
    selectedWeek: Int,
    onSelectWeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    Column(modifier = modifier.horizontalScroll(scrollState)) {
        MonthHeaderRow(uiState.weeks, selectedWeek, onSelectWeek)
        uiState.activities.forEach { activity ->
            ActivityLane(activity, uiState, selectedWeek, onSelectWeek)
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

@Composable
private fun ActivityLane(activity: CareActivity, uiState: CareTimelineUiState, selectedWeek: Int, onSelectWeek: (Int) -> Unit) {
    val activeWeeks = remember(activity.id, uiState.ranges) {
        uiState.ranges.filter { it.activityId == activity.id }.flatMap { it.startWeek..it.endWeek }.toSet()
    }
    val eventWeeks = remember(activity.id, uiState.events) {
        uiState.events.filter { it.activityId == activity.id }.map { it.week }.toSet()
    }

    Row {
        uiState.weeks.forEach { week ->
            val description = "${activity.label}, week of ${week.startDate}, " +
                if (activity.kind == ActivityKind.RANGE) {
                    if (week.index in activeWeeks) "active" else "not active"
                } else {
                    if (week.index in eventWeeks) "occurs this week" else "no occurrence"
                }
            Box(
                modifier = Modifier
                    .size(width = WEEK_WIDTH, height = LANE_HEIGHT)
                    .selectableCell(week.index == selectedWeek)
                    .clickable(onClickLabel = description) { onSelectWeek(week.index) },
                contentAlignment = Alignment.Center,
            ) {
                when (activity.kind) {
                    ActivityKind.RANGE -> if (week.index in activeWeeks) {
                        val roundStart = (week.index - 1) !in activeWeeks
                        val roundEnd = (week.index + 1) !in activeWeeks
                        val shape = RoundedCornerShape(
                            topStart = if (roundStart) 8.dp else 0.dp,
                            bottomStart = if (roundStart) 8.dp else 0.dp,
                            topEnd = if (roundEnd) 8.dp else 0.dp,
                            bottomEnd = if (roundEnd) 8.dp else 0.dp,
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (roundStart || roundEnd) 1.dp else 0.dp)
                                .size(height = 16.dp, width = WEEK_WIDTH)
                                .clip(shape)
                                .background(activity.color.copy(alpha = 0.75f)),
                        )
                    }
                    ActivityKind.POINT -> if (week.index in eventWeeks) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(activity.color))
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
