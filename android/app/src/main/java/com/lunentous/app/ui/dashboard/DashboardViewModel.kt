package com.lunentous.app.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.di.AppContainer
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReminderTask(
    val stateLocalId: Long,
    val plantLocalId: Long,
    val plantName: String,
    val reminderTypeLocalId: Long,
    val reminderTypeName: String,
    val reminderTypeIcon: String?,
    val reminderTypeColor: String?,
    val daysOverdue: Int,
)

/** One button in a plant card's quick-log bar -- every reminder type the
 * plant has a rule for, regardless of whether it currently has a computed
 * due date (a paused rule still gets a button; ReminderTask above only
 * exists for rules with one). */
data class QuickLogType(
    val reminderTypeLocalId: Long,
    val name: String,
    val icon: String?,
    val color: String?,
)

data class PlantCardData(val plant: PlantEntity, val quickLogTypes: List<QuickLogType>)

data class DashboardUiState(
    val loading: Boolean = true,
    val overdue: List<ReminderTask> = emptyList(),
    val upcoming: List<ReminderTask> = emptyList(),
    val plants: List<PlantCardData> = emptyList(),
)

/**
 * Reads are pure Room Flows (offline-safe, instant) combined client-side --
 * unlike the server's /reminder-states response, ReminderStateEntity doesn't
 * carry joined plant/type names, so this ViewModel does that join itself.
 * Mirrors web/src/pages/Dashboard.tsx's overdue/upcoming split; each plant
 * card's quick-log bar has no direct web equivalent (a mobile-only
 * shortcut for logging any of that plant's reminder types in one tap).
 */
class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    private val plantRepository = container.plantRepository
    private val reminderStateRepository = container.reminderStateRepository
    private val reminderTypeRepository = container.reminderTypeRepository
    private val reminderRuleRepository = container.reminderRuleRepository
    private val timelineRepository = container.timelineRepository

    val uiState: StateFlow<DashboardUiState> = combine(
        plantRepository.observeByArchived(false),
        plantRepository.observeAll(),
        reminderStateRepository.observeAll(),
        reminderTypeRepository.observeAll(),
        reminderRuleRepository.observeAll(),
    ) { activePlants, allPlants, states, types, rules ->
        val plantsById = allPlants.associateBy { it.localId }
        val typesById = types.associateBy { it.localId }
        val today = LocalDate.now().toEpochDay()

        val tasks = states.mapNotNull { state ->
            val dueDate = state.dueDate ?: return@mapNotNull null
            val plant = plantsById[state.plantLocalId] ?: return@mapNotNull null
            val type = typesById[state.reminderTypeLocalId] ?: return@mapNotNull null
            val daysOverdue = (today - LocalDate.parse(dueDate).toEpochDay()).toInt()
            ReminderTask(
                stateLocalId = state.localId,
                plantLocalId = plant.localId,
                plantName = plant.name,
                reminderTypeLocalId = type.localId,
                reminderTypeName = type.name,
                reminderTypeIcon = type.icon,
                reminderTypeColor = type.color,
                daysOverdue = daysOverdue,
            )
        }.sortedByDescending { it.daysOverdue }

        val rulesByPlant = rules.groupBy { it.rule.plantLocalId }
        val plantCards = activePlants.map { plant ->
            val quickLogTypes = rulesByPlant[plant.localId].orEmpty().mapNotNull { ruleWithPeriods ->
                val type = typesById[ruleWithPeriods.rule.reminderTypeLocalId] ?: return@mapNotNull null
                QuickLogType(type.localId, type.name, type.icon, type.color)
            }
            PlantCardData(plant, quickLogTypes)
        }

        DashboardUiState(
            loading = false,
            overdue = tasks.filter { it.daysOverdue >= 0 },
            upcoming = tasks.filter { it.daysOverdue < 0 }.take(8),
            plants = plantCards,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    var isRefreshing by mutableStateOf(false)
        private set

    var confirmingTask by mutableStateOf<ReminderTask?>(null)
        private set

    var isMarkingDone by mutableStateOf(false)
        private set

    var isSavingUntypedEntry by mutableStateOf(false)
        private set

    var untypedEntryError by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing = true
            plantRepository.pullSync()
            reminderTypeRepository.pullSync()
            reminderStateRepository.pullSyncAll()
            isRefreshing = false
        }
    }

    fun requestMarkDone(task: ReminderTask) {
        confirmingTask = task
    }

    /** Same confirm-and-log flow as an overdue/upcoming task row, just
     * triggered from a plant card's quick-log bar instead -- daysOverdue
     * and stateLocalId are placeholders since ConfirmDialog's message
     * never reads them, only plantName/reminderTypeName. */
    fun requestMarkDoneForType(plant: PlantEntity, type: QuickLogType) {
        confirmingTask = ReminderTask(
            stateLocalId = 0,
            plantLocalId = plant.localId,
            plantName = plant.name,
            reminderTypeLocalId = type.reminderTypeLocalId,
            reminderTypeName = type.name,
            reminderTypeIcon = type.icon,
            reminderTypeColor = type.color,
            daysOverdue = 0,
        )
    }

    fun dismissConfirm() {
        confirmingTask = null
    }

    fun confirmMarkDone() {
        val task = confirmingTask ?: return
        viewModelScope.launch {
            isMarkingDone = true
            timelineRepository.createEvent(
                plantLocalId = task.plantLocalId,
                eventDate = LocalDate.now().toString(),
                reminderTypeLocalId = task.reminderTypeLocalId,
                text = null,
            )
            reminderStateRepository.pullSyncForPlant(task.plantLocalId)
            container.refreshWidget()
            isMarkingDone = false
            confirmingTask = null
        }
    }

    /** The quick-log bar's extra "+" button -- a journal-note entry with
     * no reminder type attached, for this plant specifically. */
    fun logUntypedEntry(plantLocalId: Long, eventDate: String, text: String?, photoFiles: List<File>, onDone: () -> Unit) {
        viewModelScope.launch {
            isSavingUntypedEntry = true
            untypedEntryError = null
            timelineRepository.createEvent(plantLocalId, eventDate, reminderTypeLocalId = null, text = text, photoFiles = photoFiles)
                .onSuccess {
                    container.refreshWidget()
                    onDone()
                }
                .onFailure { untypedEntryError = it.message ?: "Failed to save entry" }
            isSavingUntypedEntry = false
        }
    }
}
