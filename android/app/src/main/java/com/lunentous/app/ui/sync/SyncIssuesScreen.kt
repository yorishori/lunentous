package com.lunentous.app.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lunentous.app.data.local.entity.OutboxEntityType
import com.lunentous.app.data.local.entity.OutboxOpType
import com.lunentous.app.data.local.entity.OutboxOperationEntity
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.theme.LunentousExtendedTheme

/** Android-only -- the web has no offline write queue to surface issues
 * from. Lists every FAILED outbox op (non-retryable failures the queue
 * skipped past rather than blocking on) with a Retry/Discard action each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncIssuesScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: SyncIssuesViewModel = viewModel(factory = viewModelFactory { initializer { SyncIssuesViewModel(container) } })
    val failedOps by viewModel.failedOps.collectAsState(emptyList())
    val colors = LunentousExtendedTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync issues") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        if (failedOps.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nothing to fix.", color = colors.textMuted)
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(failedOps, key = { it.id }) { op ->
                    FailedOpCard(op = op, onRetry = { viewModel.retry(op.id) }, onDiscard = { viewModel.discard(op.id) })
                }
            }
        }
    }
}

@Composable
private fun FailedOpCard(op: OutboxOperationEntity, onRetry: () -> Unit, onDiscard: () -> Unit) {
    val colors = LunentousExtendedTheme.colors
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${op.opType.label()} ${op.entityType.label()}", style = MaterialTheme.typography.bodyMedium)
            op.lastError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.overdue, modifier = Modifier.padding(top = 4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                OutlinedButton(onClick = onRetry) { Text("Retry") }
                TextButton(onClick = onDiscard) { Text("Discard", color = colors.overdue) }
            }
        }
    }
}

private fun OutboxOpType.label(): String = when (this) {
    OutboxOpType.CREATE -> "Create"
    OutboxOpType.UPDATE -> "Update"
    OutboxOpType.DELETE -> "Delete"
    OutboxOpType.ARCHIVE -> "Archive"
    OutboxOpType.UNARCHIVE -> "Unarchive"
    OutboxOpType.APPEND_PHOTOS -> "Upload photos for"
}

private fun OutboxEntityType.label(): String = when (this) {
    OutboxEntityType.PLANT -> "plant"
    OutboxEntityType.REMINDER_TYPE -> "reminder type"
    OutboxEntityType.PHASE_TYPE -> "phase type"
    OutboxEntityType.REMINDER_RULE -> "reminder rule"
    OutboxEntityType.PHASE_WINDOW -> "phase window"
    OutboxEntityType.TIMELINE_EVENT -> "timeline entry"
    OutboxEntityType.ONE_TIME_REMINDER -> "one-time reminder"
}
