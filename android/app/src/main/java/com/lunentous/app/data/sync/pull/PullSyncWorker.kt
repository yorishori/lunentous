package com.lunentous.app.data.sync.pull

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lunentous.app.LunentousApplication
import com.lunentous.app.ui.widget.refreshLunentousWidget
import kotlinx.coroutines.flow.first

/**
 * Periodic (~4h, see SyncScheduler) background refresh so data stays
 * reasonably fresh even without the user opening the app -- a no-op when
 * no server is connected. Actually posting reminder notifications is
 * ReminderNotificationWorker's job, scheduled for the user's chosen time
 * of day (see data/notifications/NotificationScheduler.kt) rather than
 * whenever this happens to run.
 */
class PullSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LunentousApplication).container
        if (!container.sessionStore.hasSession()) return Result.success()

        return runCatching {
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
            refreshLunentousWidget(applicationContext)
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
