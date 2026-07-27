package com.lunentous.app.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunentous.app.di.AppContainer
import kotlinx.coroutines.launch

class SyncIssuesViewModel(private val container: AppContainer) : ViewModel() {
    val failedOps = container.outboxRepository.observeFailed()

    fun retry(opId: Long) {
        viewModelScope.launch { container.outboxRepository.retry(opId) }
    }

    fun discard(opId: Long) {
        viewModelScope.launch { container.outboxRepository.discard(opId) }
    }
}
