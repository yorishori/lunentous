package com.lunentous.app.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lunentous.app.LunentousApplication

/**
 * Fires once at the user's chosen notification time (see
 * NotificationScheduler), refreshes just enough state to know what's due,
 * and posts notifications for it -- then reschedules itself for the same
 * time tomorrow. Deliberately separate from PullSyncWorker's own ~4h
 * periodic refresh, since that runs on its own schedule and would
 * otherwise notify whenever it happened to fire rather than at the time
 * the user actually asked for.
 */
class ReminderNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LunentousApplication).container
        if (!container.sessionStore.hasSession()) {
            NotificationScheduler.scheduleNext(applicationContext, container.notificationScheduleStore.time.value)
            return Result.success()
        }

        // Pull failure (e.g. no connectivity right now) must not skip
        // notifying -- checkAndNotify reads local Room state, which may
        // already know about something due even without a fresh pull.
        runCatching {
            container.plantRepository.pullSync()
            container.reminderTypeRepository.pullSync()
            container.reminderStateRepository.pullSyncAll()
        }
        container.reminderNotifier.checkAndNotify()

        // Reschedule regardless of success/failure -- a transient failure
        // here shouldn't silently end the daily notification schedule;
        // tomorrow's attempt just tries again, rather than relying on
        // WorkManager's own retry/backoff for a once-a-day job.
        NotificationScheduler.scheduleNext(applicationContext, container.notificationScheduleStore.time.value)

        return Result.success()
    }
}
