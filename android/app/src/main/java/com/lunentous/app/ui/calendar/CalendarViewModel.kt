package com.lunentous.app.ui.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunentous.app.data.local.entity.PhaseTypeEntity
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.data.local.entity.PlantPhaseWindowEntity
import com.lunentous.app.data.local.entity.ReminderStateEntity
import com.lunentous.app.data.local.entity.ReminderTypeEntity
import com.lunentous.app.data.repository.ReminderRuleWithPeriods
import com.lunentous.app.data.repository.TimelineEventWithPhotos
import com.lunentous.app.data.sync.dates.DateMath
import com.lunentous.app.di.AppContainer
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MarkerKind { DUE, PROJECTED, LOGGED }

data class CalendarMarker(val key: String, val label: String, val kind: MarkerKind, val color: String?)

data class PhaseBand(val color: String?, val label: String)

sealed interface DayDetailItem {
    data class Reminder(
        val kind: MarkerKind,
        val plantName: String,
        val reminderTypeName: String,
        val color: String?,
        val icon: String?,
    ) : DayDetailItem

    data class Logged(
        val plantName: String,
        val event: TimelineEventWithPhotos,
        val typeName: String?,
        val color: String?,
    ) : DayDetailItem
}

data class CalendarUiState(
    val loading: Boolean = true,
    val allPlants: List<PlantEntity> = emptyList(),
    val reminderTypes: List<ReminderTypeEntity> = emptyList(),
    val markersByDate: Map<String, List<CalendarMarker>> = emptyMap(),
    val phaseBandsByDate: Map<String, List<PhaseBand>> = emptyMap(),
    val dayDetailsByDate: Map<String, List<DayDetailItem>> = emptyMap(),
)

/**
 * Mirrors web/src/pages/Calendar.tsx: due/projected reminder markers (via
 * DateMath's port of the server's interval resolution), phase-window
 * shading, and logged timeline entries, all filtered to the visible month
 * and an optional plant multiselect. Adapted to mobile conventions per the
 * Android plan -- day cells show compact markers, not text labels; tapping
 * a day surfaces a detail panel instead of the web's inline hover/click
 * text, and creation only happens via the top-level "New entry" button.
 *
 * The visible month and plant filter both live in StateFlows (not plain
 * Compose state) so that changing either actually retriggers uiState's
 * combine -- a bare `var` read by closure inside the combine lambda would
 * only refresh whenever some *other* input flow happened to re-emit.
 */
class CalendarViewModel(private val container: AppContainer) : ViewModel() {
    private val today = LocalDate.now()

    private val viewMonthFlow = MutableStateFlow(today.year to today.monthValue)
    private val selectedPlantIdsFlow = MutableStateFlow<Set<Long>>(emptySet())

    var viewYear by mutableStateOf(today.year)
        private set
    var viewMonth by mutableStateOf(today.monthValue)
        private set
    val selectedPlantLocalIds: StateFlow<Set<Long>> = selectedPlantIdsFlow

    var selectedDay by mutableStateOf<String?>(null)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var isSavingEntry by mutableStateOf(false)
        private set
    var entryError by mutableStateOf<String?>(null)
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    private val timelineInMonth = viewMonthFlow.flatMapLatest { (year, month) ->
        val (from, to) = monthBounds(year, month)
        container.timelineRepository.observeAllInRange(from, to)
    }

    private val basicData = combine(
        container.plantRepository.observeByArchived(false),
        container.reminderTypeRepository.observeByArchived(false),
        container.phaseTypeRepository.observeByArchived(false),
        container.reminderStateRepository.observeAll(),
        container.reminderRuleRepository.observeAll(),
    ) { plants, types, phaseTypes, states, rules -> BasicData(plants, types, phaseTypes, states, rules) }

    private val coreData = combine(basicData, container.phaseWindowRepository.observeAll()) { basic, windows ->
        CoreData(basic.plants, basic.reminderTypes, basic.phaseTypes, basic.reminderStates, basic.reminderRules, windows)
    }

    val uiState: StateFlow<CalendarUiState> = combine(coreData, timelineInMonth, viewMonthFlow, selectedPlantIdsFlow) { core, events, (year, month), selectedIds ->
        buildUiState(core, events, year, month, selectedIds)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

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
                container.timelineRepository.pullSyncForPlant(plant.localId)
            }
            isRefreshing = false
        }
    }

    fun setSelectedPlantIds(ids: Set<Long>) {
        selectedPlantIdsFlow.value = ids
    }

    fun selectDay(iso: String) {
        selectedDay = if (selectedDay == iso) null else iso
    }

    fun prevMonth() {
        if (viewMonth == 1) {
            viewYear -= 1
            viewMonth = 12
        } else {
            viewMonth -= 1
        }
        selectedDay = null
        viewMonthFlow.value = viewYear to viewMonth
    }

    fun nextMonth() {
        if (viewMonth == 12) {
            viewYear += 1
            viewMonth = 1
        } else {
            viewMonth += 1
        }
        selectedDay = null
        viewMonthFlow.value = viewYear to viewMonth
    }

    fun saveEntry(
        plantLocalId: Long,
        existingEventLocalId: Long?,
        eventDate: String,
        reminderTypeLocalId: Long?,
        text: String?,
        photoFiles: List<File> = emptyList(),
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            isSavingEntry = true
            entryError = null
            val result = if (existingEventLocalId != null) {
                container.timelineRepository.updateEvent(existingEventLocalId, eventDate, reminderTypeLocalId, text)
            } else {
                container.timelineRepository.createEvent(plantLocalId, eventDate, reminderTypeLocalId, text, photoFiles)
            }
            isSavingEntry = false
            result.onSuccess {
                if (reminderTypeLocalId != null) container.reminderStateRepository.pullSyncForPlant(plantLocalId)
                onDone()
            }
            result.onFailure { entryError = it.message ?: "Failed to save timeline entry" }
        }
    }

    fun appendPhotos(eventLocalId: Long, photoFiles: List<File>) {
        viewModelScope.launch {
            container.timelineRepository.appendPhotos(eventLocalId, photoFiles)
                .onFailure { entryError = it.message ?: "Failed to add photo" }
        }
    }

    fun deleteEntry(plantLocalId: Long, eventLocalId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            isSavingEntry = true
            entryError = null
            val result = container.timelineRepository.deleteEvent(eventLocalId)
            isSavingEntry = false
            result.onSuccess {
                container.reminderStateRepository.pullSyncForPlant(plantLocalId)
                onDone()
            }
            result.onFailure { entryError = it.message ?: "Failed to delete timeline entry" }
        }
    }
}

private data class BasicData(
    val plants: List<PlantEntity>,
    val reminderTypes: List<ReminderTypeEntity>,
    val phaseTypes: List<PhaseTypeEntity>,
    val reminderStates: List<ReminderStateEntity>,
    val reminderRules: List<ReminderRuleWithPeriods>,
)

private data class CoreData(
    val plants: List<PlantEntity>,
    val reminderTypes: List<ReminderTypeEntity>,
    val phaseTypes: List<PhaseTypeEntity>,
    val reminderStates: List<ReminderStateEntity>,
    val reminderRules: List<ReminderRuleWithPeriods>,
    val phaseWindows: List<PlantPhaseWindowEntity>,
)

private fun monthBounds(year: Int, month: Int): Pair<String, String> {
    val yearMonth = YearMonth.of(year, month)
    return yearMonth.atDay(1).toString() to yearMonth.atEndOfMonth().toString()
}

private fun buildUiState(
    core: CoreData,
    timelineEvents: List<TimelineEventWithPhotos>,
    viewYear: Int,
    viewMonth: Int,
    selectedPlantLocalIds: Set<Long>,
): CalendarUiState {
    val effectivePlants = if (selectedPlantLocalIds.isEmpty()) core.plants else core.plants.filter { it.localId in selectedPlantLocalIds }
    val effectivePlantIds = effectivePlants.map { it.localId }.toSet()
    val plantsById = core.plants.associateBy { it.localId }
    val typesById = core.reminderTypes.associateBy { it.localId }
    val phaseTypesById = core.phaseTypes.associateBy { it.localId }
    val (monthStart, monthEnd) = monthBounds(viewYear, viewMonth)
    val daysInMonth = YearMonth.of(viewYear, viewMonth).lengthOfMonth()

    val rulesByPlantAndType = core.reminderRules.associateBy { it.rule.plantLocalId to it.rule.reminderTypeLocalId }

    val markersByDate = mutableMapOf<String, MutableList<CalendarMarker>>()
    val phaseBandsByDate = mutableMapOf<String, MutableList<PhaseBand>>()
    val dayDetailsByDate = mutableMapOf<String, MutableList<DayDetailItem>>()

    fun addMarker(date: String, marker: CalendarMarker) = markersByDate.getOrPut(date) { mutableListOf() }.add(marker)
    fun addBand(date: String, band: PhaseBand) = phaseBandsByDate.getOrPut(date) { mutableListOf() }.add(band)
    fun addDetail(date: String, item: DayDetailItem) = dayDetailsByDate.getOrPut(date) { mutableListOf() }.add(item)

    // Reminder due dates + projected future occurrences.
    for (state in core.reminderStates) {
        val dueDate = state.dueDate ?: continue
        if (state.plantLocalId !in effectivePlantIds) continue
        val plant = plantsById[state.plantLocalId] ?: continue
        val type = typesById[state.reminderTypeLocalId] ?: continue
        val rule = rulesByPlantAndType[state.plantLocalId to state.reminderTypeLocalId]
        val occurrences = if (rule != null) {
            DateMath.projectOccurrencesInRange(dueDate, rule.rule.defaultIntervalDays, rule.overridePeriods, monthStart, monthEnd)
        } else if (dueDate in monthStart..monthEnd) {
            listOf(dueDate)
        } else {
            emptyList()
        }
        for (date in occurrences) {
            val kind = if (date == dueDate) MarkerKind.DUE else MarkerKind.PROJECTED
            addMarker(date, CalendarMarker(key = "state-${state.localId}-$date", label = "${plant.name}: ${type.name}", kind = kind, color = type.color))
            addDetail(date, DayDetailItem.Reminder(kind, plant.name, type.name, type.color, type.icon))
        }
    }

    // Phase windows, shaded across their active date range.
    for (window in core.phaseWindows) {
        if (window.plantLocalId !in effectivePlantIds) continue
        val plant = plantsById[window.plantLocalId] ?: continue
        val phaseType = phaseTypesById[window.phaseTypeLocalId]
        for (day in 1..daysInMonth) {
            val iso = LocalDate.of(viewYear, viewMonth, day).toString()
            if (DateMath.dateInRange(iso, window.startMonth, window.startDay, window.endMonth, window.endDay)) {
                addBand(iso, PhaseBand(color = phaseType?.color, label = "${plant.name}: ${phaseType?.name ?: "phase"}"))
            }
        }
    }

    // Logged timeline entries -- every entry, not just reminder completions.
    for (eventWithPhotos in timelineEvents) {
        val event = eventWithPhotos.event
        if (event.plantLocalId !in effectivePlantIds) continue
        val plant = plantsById[event.plantLocalId] ?: continue
        val type = event.reminderTypeLocalId?.let { typesById[it] }
        addMarker(event.eventDate, CalendarMarker(key = "event-${event.localId}", label = "${plant.name}: ${type?.name ?: "Note"}", kind = MarkerKind.LOGGED, color = type?.color))
        addDetail(event.eventDate, DayDetailItem.Logged(plant.name, eventWithPhotos, type?.name, type?.color))
    }

    return CalendarUiState(
        loading = false,
        allPlants = core.plants,
        reminderTypes = core.reminderTypes,
        markersByDate = markersByDate,
        phaseBandsByDate = phaseBandsByDate,
        dayDetailsByDate = dayDetailsByDate,
    )
}
