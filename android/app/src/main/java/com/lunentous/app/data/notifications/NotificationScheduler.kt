package com.lunentous.app.data.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val NOTIFICATION_WORK_NAME = "reminder_notification"

/**
 * Schedules ReminderNotificationWorker as a one-shot request with an
 * initial delay computed to land at `time` -- WorkManager has no native
 * "run daily at this clock time" API, so the worker re-calls this itself
 * after each run to chain the next occurrence (see
 * ReminderNotificationWorker). ExistingWorkPolicy.REPLACE means changing
 * the time in Settings cancels whatever was pending and requeues against
 * the new time immediately.
 *
 * No network constraint here, deliberately -- the worker's own notify
 * step reads local Room state, not the network, so it should still fire
 * on schedule even if the device happens to be offline right then (it
 * just won't have refreshed reminder_states first).
 */
object NotificationScheduler {
    fun scheduleNext(context: Context, time: LocalTime) {
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(LocalDate.now(), time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMillis = Duration.between(now, next).toMillis()

        val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOTIFICATION_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
