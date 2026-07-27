package com.lunentous.app.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.di.AppContainer
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

data class PlantCardData(val plant: PlantEntity, val nextReminder: ReminderTask?)

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
 * Mirrors web/src/pages/Dashboard.tsx's overdue/upcoming split and
 * PlantCard.tsx's per-plant "next reminder" pick.
 */
class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    private val plantRepository = container.plantRepository
    private val reminderStateRepository = container.reminderStateRepository
    private val reminderTypeRepository = container.reminderTypeRepository
    private val timelineRepository = container.timelineRepository

    val uiState: StateFlow<DashboardUiState> = combine(
        plantRepository.observeByArchived(false),
        plantRepository.observeAll(),
        reminderStateRepository.observeAll(),
        reminderTypeRepository.observeAll(),
    ) { activePlants, allPlants, states, types ->
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

        val plantCards = activePlants.map { plant ->
            PlantCardData(plant, tasks.firstOrNull { it.plantLocalId == plant.localId })
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
            isMarkingDone = false
            confirmingTask = null
        }
    }
}
