package com.lunentous.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.data.remote.dto.ApiKeyDto
import com.lunentous.app.di.AppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** API keys have no local (Room) representation -- see AccountRepository --
 * so this is a plain load-on-demand list rather than a Flow off Room. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    var isConnecting by mutableStateOf(false)
        private set

    var connectError by mutableStateOf<String?>(null)
        private set

    /** Non-empty only right after an initial-connect merge found
     * case-insensitive name collisions -- see the Android plan's
     * "Server connection is optional" / initial-connect merge design.
     * Purely in-memory: this is a one-time post-connect prompt, not
     * something that needs to survive a process death. */
    var duplicatePlantGroups by mutableStateOf<List<List<PlantEntity>>>(emptyList())
        private set

    /** Connects to a server, then runs the initial-connect merge if this
     * device already had local-only data: the push half is free (every
     * write already enqueues an outbox op regardless of connection state,
     * so local-only entities already have pending CREATE ops sitting in
     * the queue) -- this just drains that backlog synchronously (rather
     * than going through WorkManager's async trigger) so the sequencing
     * the plan describes actually holds: push, then a normal pull, then
     * the duplicate-plant scan. */
    fun connect(serverUrl: String, apiKey: String, onConnected: () -> Unit) {
        viewModelScope.launch {
            isConnecting = true
            connectError = null

            val hadLocalOnlyPlants = runCatching { container.plantRepository.hasLocalOnlyPlants() }.getOrDefault(false)
            container.sessionStore.saveSession(serverUrl, apiKey)

            val result = runCatching {
                container.outboxProcessor.processQueue()
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
            }

            if (hadLocalOnlyPlants) {
                duplicatePlantGroups = runCatching { container.plantRepository.findDuplicateNameGroups() }.getOrDefault(emptyList())
            }

            result.onFailure { connectError = it.message ?: "Connected, but the initial sync failed -- it'll retry automatically" }
            isConnecting = false
            onConnected()
        }
    }

    fun dismissDuplicates() {
        duplicatePlantGroups = emptyList()
    }

    /** "Archive" is this flow's version of the plan's "archive/delete one"
     * cleanup action -- plants have no server DELETE endpoint, only
     * archive, so that's the only real choice here. Reuses the existing
     * archive repository method rather than a new merge primitive. */
    fun archiveDuplicate(plant: PlantEntity) {
        viewModelScope.launch {
            container.plantRepository.setArchived(plant.localId, true)
            duplicatePlantGroups = duplicatePlantGroups
                .map { group -> group.filterNot { it.localId == plant.localId } }
                .filter { it.size > 1 }
        }
    }

    var apiKeys by mutableStateOf<List<ApiKeyDto>>(emptyList())
        private set

    var isLoadingKeys by mutableStateOf(false)
        private set

    var isSavingKey by mutableStateOf(false)
        private set

    var keysError by mutableStateOf<String?>(null)
        private set

    var createdToken by mutableStateOf<String?>(null)
        private set

    fun loadApiKeys() {
        viewModelScope.launch {
            isLoadingKeys = true
            container.accountRepository.getApiKeys()
                .onSuccess { apiKeys = it }
                .onFailure { keysError = it.message ?: "Failed to load API keys" }
            isLoadingKeys = false
        }
    }

    fun createApiKey(label: String) {
        viewModelScope.launch {
            isSavingKey = true
            keysError = null
            container.accountRepository.createApiKey(label)
                .onSuccess { created ->
                    createdToken = created.token
                    loadApiKeys()
                }
                .onFailure { keysError = it.message ?: "Failed to create API key" }
            isSavingKey = false
        }
    }

    fun dismissCreatedToken() {
        createdToken = null
    }

    fun revokeApiKey(id: Long) {
        viewModelScope.launch {
            keysError = null
            container.accountRepository.revokeApiKey(id)
                .onSuccess { loadApiKeys() }
                .onFailure { keysError = it.message ?: "Failed to revoke API key" }
        }
    }
}
