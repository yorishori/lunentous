package com.lunentous.app.ui.types

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunentous.app.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhaseTypesViewModel(private val container: AppContainer) : ViewModel() {
    private val showArchivedFlow = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = showArchivedFlow

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<TypeRow>> = showArchivedFlow.flatMapLatest { archived ->
        combine(
            container.phaseTypeRepository.observeByArchived(archived),
            container.phaseWindowRepository.observeUsageCounts(),
        ) { types, counts ->
            val countByType = counts.associate { it.typeLocalId to it.count }
            types.map { t -> TypeRow(t.localId, t.name, icon = null, t.color, t.archived, countByType[t.localId] ?: 0) }
                .sortedBy { it.name.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var isSaving by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { container.phaseTypeRepository.pullSync() }
    }

    fun setShowArchived(value: Boolean) {
        showArchivedFlow.value = value
    }

    fun save(existingLocalId: Long?, name: String, color: String, onDone: () -> Unit) {
        viewModelScope.launch {
            isSaving = true
            error = null
            val result = if (existingLocalId != null) {
                container.phaseTypeRepository.update(existingLocalId, name, color)
            } else {
                container.phaseTypeRepository.create(name, color)
            }
            isSaving = false
            result.onSuccess { onDone() }
            result.onFailure { error = it.message ?: "Failed to save phase type" }
        }
    }

    fun toggleArchive(row: TypeRow) {
        viewModelScope.launch {
            container.phaseTypeRepository.setArchived(row.localId, !row.archived)
        }
    }
}
