package com.lunentous.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunentous.app.data.remote.dto.ApiKeyDto
import com.lunentous.app.di.AppContainer
import kotlinx.coroutines.launch

/** API keys have no local (Room) representation -- see AccountRepository --
 * so this is a plain load-on-demand list rather than a Flow off Room. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {
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
