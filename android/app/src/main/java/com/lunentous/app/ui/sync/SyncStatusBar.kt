package com.lunentous.app.ui.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.theme.LunentousExtendedTheme

/**
 * Always-visible sync affordance, mirroring the web's LoadingBar
 * convention -- collapses to nothing when there's genuinely nothing to
 * report (Idle), matching the plan's Idle/Syncing/Offline·N/Sync issues·N
 * states. Shown once in MainScaffold rather than per-screen.
 */
@Composable
fun SyncStatusBar(container: AppContainer, onOpenSyncIssues: () -> Unit, onOpenSettings: () -> Unit) {
    val pendingCount by container.outboxRepository.observePendingCount().collectAsState(0)
    val failedCount by container.outboxRepository.observeFailedCount().collectAsState(0)
    val isOnline by container.connectivityObserver.isOnline.collectAsState()
    val reauthRequired by container.sessionStore.reauthRequired.collectAsState()

    if (!reauthRequired && failedCount == 0 && pendingCount == 0) return

    val colors = LunentousExtendedTheme.colors
    val clickAction = when {
        reauthRequired -> onOpenSettings
        failedCount > 0 -> onOpenSyncIssues
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (clickAction != null) it.clickable(onClick = clickAction) else it }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            reauthRequired -> Text("Reconnect needed — tap to open Settings", style = MaterialTheme.typography.labelSmall, color = colors.overdue)
            failedCount > 0 -> Text("Sync issues · $failedCount", style = MaterialTheme.typography.labelSmall, color = colors.overdue)
            !isOnline -> Text("Offline · $pendingCount pending", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            pendingCount > 0 -> {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = colors.accent)
                Text(" Syncing · $pendingCount", style = MaterialTheme.typography.labelSmall, color = colors.textMuted, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
