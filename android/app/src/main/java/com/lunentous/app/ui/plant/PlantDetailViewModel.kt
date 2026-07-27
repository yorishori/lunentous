package com.lunentous.app.ui.plant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlantDetailViewModel(private val container: AppContainer, private val plantLocalId: Long) : ViewModel() {
    val plant: StateFlow<PlantEntity?> = container.plantRepository.observeByLocalId(plantLocalId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var isArchiving by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { container.plantRepository.pullSync() }
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
}
