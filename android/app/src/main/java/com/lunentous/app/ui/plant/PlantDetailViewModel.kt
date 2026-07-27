package com.lunentous.app.ui.plant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunentous.app.data.local.entity.OverridePeriodEntity
import com.lunentous.app.data.local.entity.PhaseTypeEntity
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.data.local.entity.PlantPhaseWindowEntity
import com.lunentous.app.data.local.entity.ReminderTypeEntity
import com.lunentous.app.data.repository.ReminderRuleWithPeriods
import com.lunentous.app.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlantDetailViewModel(private val container: AppContainer, private val plantLocalId: Long) : ViewModel() {
    val plant: StateFlow<PlantEntity?> = container.plantRepository.observeByLocalId(plantLocalId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Active types only, matching web's reminderTypesQuery({ archived: false })
     * used to populate the "add rule" type picker. */
    val reminderTypes: StateFlow<List<ReminderTypeEntity>> = container.reminderTypeRepository.observeByArchived(false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminderRules: StateFlow<List<ReminderRuleWithPeriods>> = container.reminderRuleRepository.observeByPlant(plantLocalId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val phaseTypes: StateFlow<List<PhaseTypeEntity>> = container.phaseTypeRepository.observeByArchived(false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val phaseWindows: StateFlow<List<PlantPhaseWindowEntity>> = container.phaseWindowRepository.observeByPlant(plantLocalId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var isArchiving by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var isSavingRule by mutableStateOf(false)
        private set

    var ruleError by mutableStateOf<String?>(null)
        private set

    var isSavingWindow by mutableStateOf(false)
        private set

    var windowError by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { container.plantRepository.pullSync() }
        viewModelScope.launch { container.reminderTypeRepository.pullSync() }
        viewModelScope.launch { container.reminderRuleRepository.pullSyncForPlant(plantLocalId) }
        viewModelScope.launch { container.phaseTypeRepository.pullSync() }
        viewModelScope.launch { container.phaseWindowRepository.pullSyncForPlant(plantLocalId) }
    }

    fun toggleArchive() {
        val current = plant.value ?: return
        viewModelScope.launch {
            isArchiving = true
            error = null
            container.plantRepository.setArchived(plantLocalId, !current.archived)
                .onFailure { error = it.message ?: "Failed to update plant" }
            isArchiving = false
        }
    }

    fun saveReminderRule(
        existingRuleLocalId: Long?,
        reminderTypeLocalId: Long,
        defaultIntervalDays: Int?,
        periods: List<OverridePeriodEntity>,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            isSavingRule = true
            ruleError = null
            val result = if (existingRuleLocalId != null) {
                container.reminderRuleRepository.update(existingRuleLocalId, defaultIntervalDays, periods)
            } else {
                container.reminderRuleRepository.create(plantLocalId, reminderTypeLocalId, defaultIntervalDays, periods)
            }
            isSavingRule = false
            result.onSuccess {
                container.reminderStateRepository.pullSyncForPlant(plantLocalId)
                onDone()
            }
            result.onFailure { ruleError = it.message ?: "Failed to save reminder rule" }
        }
    }

    fun deleteReminderRule(ruleLocalId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            isSavingRule = true
            ruleError = null
            val result = container.reminderRuleRepository.delete(ruleLocalId)
            isSavingRule = false
            result.onSuccess {
                container.reminderStateRepository.pullSyncForPlant(plantLocalId)
                onDone()
            }
            result.onFailure { ruleError = it.message ?: "Failed to delete reminder rule" }
        }
    }

    fun savePhaseWindow(
        existingWindowLocalId: Long?,
        phaseTypeLocalId: Long,
        startMonth: Int,
        startDay: Int,
        endMonth: Int,
        endDay: Int,
        notes: String?,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            isSavingWindow = true
            windowError = null
            val result = if (existingWindowLocalId != null) {
                container.phaseWindowRepository.update(existingWindowLocalId, phaseTypeLocalId, startMonth, startDay, endMonth, endDay, notes)
            } else {
                container.phaseWindowRepository.create(plantLocalId, phaseTypeLocalId, startMonth, startDay, endMonth, endDay, notes)
            }
            isSavingWindow = false
            result.onSuccess { onDone() }
            result.onFailure { windowError = it.message ?: "Failed to save phase window" }
        }
    }

    fun deletePhaseWindow(windowLocalId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            isSavingWindow = true
            windowError = null
            val result = container.phaseWindowRepository.delete(windowLocalId)
            isSavingWindow = false
            result.onSuccess { onDone() }
            result.onFailure { windowError = it.message ?: "Failed to delete phase window" }
        }
    }
}
