package com.lunentous.app.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunentous.app.data.local.entity.OneTimeReminderEntity
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.data.local.entity.ReminderStateEntity
import com.lunentous.app.data.local.entity.ReminderTypeEntity
import com.lunentous.app.data.repository.ReminderRuleWithPeriods
import com.lunentous.app.di.AppContainer
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A row in the overdue/upcoming lists -- either a regular typed reminder
 * (reminderTypeLocalId set, has an icon/color) or a one-time informational
 * reminder (reminderTypeLocalId null, no icon, reminderTypeName holds the
 * reminder's own free text instead of a type name). entityLocalId is
 * whichever local id the row's completion action needs: a
 * ReminderStateEntity's for a regular reminder, or a OneTimeReminderEntity's
 * for a one-time one. */
data class ReminderTask(
    val entityLocalId: Long,
    val plantLocalId: Long,
    val plantName: String,
    val reminderTypeLocalId: Long?,
    val reminderTypeName: String,
    val reminderTypeIcon: String?,
    val reminderTypeColor: String?,
    val daysOverdue: Int,
    val isOneTime: Boolean = false,
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

private data class BaseData(
    val activePlants: List<PlantEntity>,
    val allPlants: List<PlantEntity>,
    val states: List<ReminderStateEntity>,
    val types: List<ReminderTypeEntity>,
    val rules: List<ReminderRuleWithPeriods>,
)

/**
 * Reads are pure Room Flows (offline-safe, instant) combined client-side --
 * unlike the server's /reminder-states response, ReminderStateEntity doesn't
 * carry joined plant/type names, so this ViewModel does that join itself.
 * Mirrors web/src/pages/Dashboard.tsx's overdue/upcoming split (now also
 * mixing in one-time reminders, same as web); each plant card's quick-log
 * bar has no direct web equivalent (a mobile-only shortcut for logging any
 * of that plant's reminder types in one tap).
 */
class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    private val plantRepository = container.plantRepository
    private val reminderStateRepository = container.reminderStateRepository
    private val reminderTypeRepository = container.reminderTypeRepository
    private val reminderRuleRepository = container.reminderRuleRepository
    private val timelineRepository = container.timelineRepository
    private val oneTimeReminderRepository = container.oneTimeReminderRepository

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            plantRepository.observeByArchived(false),
            plantRepository.observeAll(),
            reminderStateRepository.observeAll(),
            reminderTypeRepository.observeAll(),
            reminderRuleRepository.observeAll(),
        ) { activePlants, allPlants, states, types, rules -> BaseData(activePlants, allPlants, states, types, rules) },
        oneTimeReminderRepository.observeAll(),
    ) { base, oneTimeReminders -> buildUiState(base, oneTimeReminders) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private fun buildUiState(base: BaseData, oneTimeReminders: List<OneTimeReminderEntity>): DashboardUiState {
        val plantsById = base.allPlants.associateBy { it.localId }
        val typesById = base.types.associateBy { it.localId }
        val today = LocalDate.now().toEpochDay()

        val reminderTasks = base.states.mapNotNull { state ->
            val dueDate = state.dueDate ?: return@mapNotNull null
            val plant = plantsById[state.plantLocalId] ?: return@mapNotNull null
            val type = typesById[state.reminderTypeLocalId] ?: return@mapNotNull null
            val daysOverdue = (today - LocalDate.parse(dueDate).toEpochDay()).toInt()
            ReminderTask(
                entityLocalId = state.localId,
                plantLocalId = plant.localId,
                plantName = plant.name,
                reminderTypeLocalId = type.localId,
                reminderTypeName = type.name,
                reminderTypeIcon = type.icon,
                reminderTypeColor = type.color,
                daysOverdue = daysOverdue,
            )
        }

        val oneTimeTasks = oneTimeReminders.filter { it.completedAt == null }.mapNotNull { reminder ->
            val plant = plantsById[reminder.plantLocalId] ?: return@mapNotNull null
            val daysOverdue = (today - LocalDate.parse(reminder.dueDate).toEpochDay()).toInt()
            ReminderTask(
                entityLocalId = reminder.localId,
                plantLocalId = plant.localId,
                plantName = plant.name,
                reminderTypeLocalId = null,
                reminderTypeName = reminder.text,
                reminderTypeIcon = null,
                reminderTypeColor = null,
                daysOverdue = daysOverdue,
                isOneTime = true,
            )
        }

        val tasks = (reminderTasks + oneTimeTasks).sortedByDescending { it.daysOverdue }

        val rulesByPlant = base.rules.groupBy { it.rule.plantLocalId }
        val plantCards = base.activePlants.map { plant ->
            val quickLogTypes = rulesByPlant[plant.localId].orEmpty().mapNotNull { ruleWithPeriods ->
                val type = typesById[ruleWithPeriods.rule.reminderTypeLocalId] ?: return@mapNotNull null
                QuickLogType(type.localId, type.name, type.icon, type.color)
            }
            PlantCardData(plant, quickLogTypes)
        }

        return DashboardUiState(
            loading = false,
            overdue = tasks.filter { it.daysOverdue >= 0 },
            upcoming = tasks.filter { it.daysOverdue < 0 }.take(8),
            plants = plantCards,
        )
    }

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
            val plants = plantRepository.observeByArchived(false).first()
            plants.forEach { plant -> oneTimeReminderRepository.pullSyncForPlant(plant.localId) }
            isRefreshing = false
        }
    }

    fun requestMarkDone(task: ReminderTask) {
        confirmingTask = task
    }

    /** Same confirm-and-log flow as an overdue/upcoming task row, just
     * triggered from a plant card's quick-log bar instead -- daysOverdue
     * and entityLocalId are placeholders since ConfirmDialog's message
     * never reads them, only plantName/reminderTypeName. */
    fun requestMarkDoneForType(plant: PlantEntity, type: QuickLogType) {
        confirmingTask = ReminderTask(
            entityLocalId = 0,
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
            if (task.isOneTime) {
                oneTimeReminderRepository.setCompleted(task.entityLocalId, true)
            } else {
                timelineRepository.createEvent(
                    plantLocalId = task.plantLocalId,
                    eventDate = LocalDate.now().toString(),
                    reminderTypeLocalId = task.reminderTypeLocalId,
                    text = null,
                )
                reminderStateRepository.pullSyncForPlant(task.plantLocalId)
            }
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
