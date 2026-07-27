package com.lunentous.app.ui.calendar.timeline

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunentous.app.data.local.entity.PhaseTypeEntity
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.data.local.entity.PlantPhaseWindowEntity
import com.lunentous.app.data.local.entity.ReminderStateEntity
import com.lunentous.app.data.local.entity.ReminderTypeEntity
import com.lunentous.app.data.repository.ReminderRuleWithPeriods
import com.lunentous.app.data.sync.dates.DateMath
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.icons.iconFor
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ActivityKind { RANGE, POINT }

/** id is namespaced by kind+localId (e.g. "phase-3") so a phase type and a
 * reminder type can never collide even though both are Longs. */
data class CareActivity(
    val id: String,
    val label: String,
    val kind: ActivityKind,
    val color: Color,
    val icon: ImageVector,
)

data class SeasonRange(val activityId: String, val plantLocalId: Long, val plantName: String, val startWeek: Int, val endWeek: Int)

data class CareEvent(val activityId: String, val plantLocalId: Long, val plantName: String, val week: Int, val date: LocalDate)

data class WeekInfo(val index: Int, val startDate: LocalDate, val monthLabel: String?)

data class CareTimelineUiState(
    val loading: Boolean = true,
    val weeks: List<WeekInfo> = emptyList(),
    val activities: List<CareActivity> = emptyList(),
    val ranges: List<SeasonRange> = emptyList(),
    val events: List<CareEvent> = emptyList(),
    val allPlants: List<PlantEntity> = emptyList(),
    val allReminderTypes: List<ReminderTypeEntity> = emptyList(),
)

private data class RawData(
    val plants: List<PlantEntity>,
    val reminderTypes: List<ReminderTypeEntity>,
    val phaseTypes: List<PhaseTypeEntity>,
    val phaseWindows: List<PlantPhaseWindowEntity>,
    val reminderRules: List<ReminderRuleWithPeriods>,
    val reminderStates: List<ReminderStateEntity>,
)

private const val WINDOW_MONTHS = 4L

/**
 * Builds the multi-month care timeline (range activities = phase windows,
 * point activities = reminder occurrences) per the provided spec --
 * ranges/events are derived fresh from raw Room state on every emission
 * rather than pre-expanded into a week x activity matrix and stored, since
 * that's cheap at this scale (a handful of plants/types, ~17 weeks) and
 * keeps the raw rows as the single source of truth.
 *
 * Colors come from each reminder/phase type's own user-chosen color
 * (already how every other screen in this app renders them) rather than
 * the spec's suggested 3-role cap -- that cap exists to avoid inventing a
 * palette, but this app already has one per type, which is the more
 * faithful "use what already exists" choice here.
 */
class CareTimelineViewModel(private val container: AppContainer) : ViewModel() {
    private val windowStart: LocalDate = LocalDate.now().withDayOfMonth(1)
    private val windowEnd: LocalDate = windowStart.plusMonths(WINDOW_MONTHS).minusDays(1)
    private val weeks: List<WeekInfo> = buildWeeks(windowStart, windowEnd)

    var selectedWeek by mutableIntStateOf(weekIndexFor(LocalDate.now(), windowStart, weeks))
        private set

    var isRefreshing by mutableStateOf(false)
        private set
    var isSavingEntry by mutableStateOf(false)
        private set
    var entryError by mutableStateOf<String?>(null)
        private set

    private val rawData = combine(
        container.plantRepository.observeByArchived(false),
        container.reminderTypeRepository.observeByArchived(false),
        container.phaseTypeRepository.observeByArchived(false),
        container.phaseWindowRepository.observeAll(),
        container.reminderRuleRepository.observeAll(),
    ) { plants, reminderTypes, phaseTypes, phaseWindows, reminderRules ->
        RawFirst(plants, reminderTypes, phaseTypes, phaseWindows, reminderRules)
    }

    val uiState: StateFlow<CareTimelineUiState> = combine(rawData, container.reminderStateRepository.observeAll()) { first, states ->
        buildUiState(
            RawData(first.plants, first.reminderTypes, first.phaseTypes, first.phaseWindows, first.reminderRules, states),
            weeks,
            windowStart,
            windowEnd,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CareTimelineUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing = true
            container.plantRepository.pullSync()
            container.reminderTypeRepository.pullSync()
            container.phaseTypeRepository.pullSync()
            container.reminderStateRepository.pullSyncAll()
            val plants = container.plantRepository.observeByArchived(false).first()
            plants.forEach { plant ->
                container.reminderRuleRepository.pullSyncForPlant(plant.localId)
                container.phaseWindowRepository.pullSyncForPlant(plant.localId)
            }
            isRefreshing = false
        }
    }

    fun selectWeek(index: Int) {
        selectedWeek = index
    }

    /** Plant-picker entry creation, same underlying write TimelineRepository
     * always uses -- per-event edit/delete still lives on Plant Detail's
     * own timeline section; this screen only ever creates. */
    fun saveEntry(plantLocalId: Long, eventDate: String, reminderTypeLocalId: Long?, text: String?, photoFiles: List<File>, onDone: () -> Unit) {
        viewModelScope.launch {
            isSavingEntry = true
            entryError = null
            container.timelineRepository.createEvent(plantLocalId, eventDate, reminderTypeLocalId, text, photoFiles)
                .onSuccess {
                    if (reminderTypeLocalId != null) container.reminderStateRepository.pullSyncForPlant(plantLocalId)
                    onDone()
                }
                .onFailure { entryError = it.message ?: "Failed to save timeline entry" }
            isSavingEntry = false
        }
    }
}

private data class RawFirst(
    val plants: List<PlantEntity>,
    val reminderTypes: List<ReminderTypeEntity>,
    val phaseTypes: List<PhaseTypeEntity>,
    val phaseWindows: List<PlantPhaseWindowEntity>,
    val reminderRules: List<ReminderRuleWithPeriods>,
)

private fun buildWeeks(start: LocalDate, endInclusive: LocalDate): List<WeekInfo> {
    val weeks = mutableListOf<WeekInfo>()
    var cursor = start
    var index = 0
    var lastMonth = -1
    while (!cursor.isAfter(endInclusive)) {
        val monthLabel = if (cursor.monthValue != lastMonth) cursor.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) else null
        weeks.add(WeekInfo(index, cursor, monthLabel))
        lastMonth = cursor.monthValue
        cursor = cursor.plusDays(7)
        index++
    }
    return weeks
}

private fun weekIndexFor(date: LocalDate, windowStart: LocalDate, weeks: List<WeekInfo>): Int {
    val index = ((date.toEpochDay() - windowStart.toEpochDay()) / 7).toInt()
    return index.coerceIn(0, (weeks.size - 1).coerceAtLeast(0))
}

private fun parseColor(hex: String?, fallback: Color): Color =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: fallback

private fun buildUiState(raw: RawData, weeks: List<WeekInfo>, windowStart: LocalDate, windowEnd: LocalDate): CareTimelineUiState {
    val plantsById = raw.plants.associateBy { it.localId }
    val phaseTypesById = raw.phaseTypes.associateBy { it.localId }
    val reminderTypesById = raw.reminderTypes.associateBy { it.localId }
    val fallbackColor = Color(0xFF8839EF)

    val ranges = mutableListOf<SeasonRange>()
    for (window in raw.phaseWindows) {
        val plant = plantsById[window.plantLocalId] ?: continue
        val type = phaseTypesById[window.phaseTypeLocalId] ?: continue
        val activityId = "phase-${type.localId}"
        var segmentStart: Int? = null
        for (week in weeks) {
            val active = DateMath.dateInRange(week.startDate.toString(), window.startMonth, window.startDay, window.endMonth, window.endDay)
            if (active && segmentStart == null) segmentStart = week.index
            if (!active && segmentStart != null) {
                ranges.add(SeasonRange(activityId, plant.localId, plant.name, segmentStart, week.index - 1))
                segmentStart = null
            }
        }
        if (segmentStart != null) ranges.add(SeasonRange(activityId, plant.localId, plant.name, segmentStart, weeks.last().index))
    }

    val rulesByPlantAndType = raw.reminderRules.associateBy { it.rule.plantLocalId to it.rule.reminderTypeLocalId }
    val events = mutableListOf<CareEvent>()
    val windowStartStr = windowStart.toString()
    val windowEndStr = windowEnd.toString()
    for (state in raw.reminderStates) {
        val dueDate = state.dueDate ?: continue
        val plant = plantsById[state.plantLocalId] ?: continue
        val type = reminderTypesById[state.reminderTypeLocalId] ?: continue
        val activityId = "reminder-${type.localId}"
        val rule = rulesByPlantAndType[state.plantLocalId to state.reminderTypeLocalId]
        val occurrences = if (rule != null) {
            DateMath.projectOccurrencesInRange(dueDate, rule.rule.defaultIntervalDays, rule.overridePeriods, windowStartStr, windowEndStr)
        } else if (dueDate in windowStartStr..windowEndStr) {
            listOf(dueDate)
        } else {
            emptyList()
        }
        for (dateStr in occurrences) {
            val date = LocalDate.parse(dateStr)
            events.add(CareEvent(activityId, plant.localId, plant.name, weekIndexFor(date, windowStart, weeks), date))
        }
    }

    val rangeActivities = raw.phaseTypes
        .map { type -> CareActivity("phase-${type.localId}", type.name, ActivityKind.RANGE, parseColor(type.color, fallbackColor), Icons.Filled.DateRange) }
        .filter { activity -> ranges.any { it.activityId == activity.id } }

    val pointActivities = raw.reminderTypes
        .map { type -> CareActivity("reminder-${type.localId}", type.name, ActivityKind.POINT, parseColor(type.color, fallbackColor), iconFor(type.icon)) }
        .filter { activity -> events.any { it.activityId == activity.id } }

    return CareTimelineUiState(
        loading = false,
        weeks = weeks,
        activities = rangeActivities + pointActivities,
        ranges = ranges,
        events = events,
        allPlants = raw.plants,
        allReminderTypes = raw.reminderTypes,
    )
}
