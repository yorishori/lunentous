package com.lunentous.app.data.sync.pull

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lunentous.app.LunentousApplication
import kotlinx.coroutines.flow.first

/**
 * Periodic (~4h, see SyncScheduler) background refresh so data stays
 * reasonably fresh even without the user opening the app -- a no-op when
 * no server is connected. Also the foundation the plan's on-device
 * reminder poll builds on in phase 6: the server's `notified`/
 * `due_before_or_on` query params on GET /reminder-states exist
 * specifically for that, per ARCHITECTURE.md's Android section, but
 * firing a notification from here is out of scope until phase 6 wires up
 * the notification channel/permission.
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
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
