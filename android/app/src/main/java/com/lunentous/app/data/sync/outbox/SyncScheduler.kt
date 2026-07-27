package com.lunentous.app.data.sync.outbox

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

private const val UNIQUE_WORK_NAME = "outbox_sync"

/**
 * Triggers OutboxSyncWorker from every place the plan calls for: on
 * enqueue (each repository write), on regained connectivity
 * (LunentousApplication's NetworkCallback), and on app foreground
 * (MainActivity.onResume). ExistingWorkPolicy.KEEP means a burst of
 * enqueues collapses into the one already-scheduled run, since
 * OutboxProcessor drains the whole queue in a single pass anyway.
 */
object SyncScheduler {
    fun triggerOutboxSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
