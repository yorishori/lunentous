package com.lunentous.app.data.sync.outbox

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lunentous.app.data.sync.pull.PullSyncWorker
import java.util.concurrent.TimeUnit

private const val OUTBOX_WORK_NAME = "outbox_sync"
private const val PULL_SYNC_WORK_NAME = "periodic_pull_sync"
private const val IMMEDIATE_PULL_SYNC_WORK_NAME = "immediate_pull_sync"
private const val PULL_SYNC_INTERVAL_HOURS = 4L

/**
 * Triggers OutboxSyncWorker from every place the plan calls for: on
 * enqueue (each repository write), on regained connectivity
 * (LunentousApplication's NetworkCallback), and on app foreground
 * (MainActivity.onResume). ExistingWorkPolicy.KEEP means a burst of
 * enqueues collapses into the one already-scheduled run, since
 * OutboxProcessor drains the whole queue in a single pass anyway.
 *
 * Also schedules the periodic PullSyncWorker, started once from
 * LunentousApplication.onCreate().
 */
object SyncScheduler {
    fun triggerOutboxSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(OUTBOX_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** One-off pull, distinct from the periodic job below -- used by the
     * widget's manual refresh button. A different unique work name than
     * the periodic job's so this doesn't cancel/replace that schedule. */
    fun triggerImmediatePullSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<PullSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_PULL_SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun schedulePeriodicPullSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<PullSyncWorker>(PULL_SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // KEEP -- re-registering the exact same periodic schedule on every
        // app launch would otherwise reset its next-run countdown each time.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PULL_SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
