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
 * no server is connected. Also doubles as the on-device reminder poll:
 * once reminder_states is freshly pulled, ReminderNotifier checks it for
 * anything due/overdue and not yet notified and posts local notifications
 * for it -- the server's `notified` column and this worker's schedule are
 * exactly what ARCHITECTURE.md's Android section describes that flow
 * needing.
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
            container.reminderNotifier.checkAndNotify()
            refreshLunentousWidget(applicationContext)
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
