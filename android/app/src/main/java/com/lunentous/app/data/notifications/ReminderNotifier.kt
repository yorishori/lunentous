package com.lunentous.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lunentous.app.MainActivity
import com.lunentous.app.R
import com.lunentous.app.data.local.dao.PlantDao
import com.lunentous.app.data.local.dao.ReminderTypeDao
import com.lunentous.app.data.repository.ReminderStateRepository
import com.lunentous.app.ui.nav.EXTRA_PLANT_LOCAL_ID
import java.time.LocalDate

private const val CHANNEL_ID = "reminders"

/**
 * The on-device half of the plan's reminder poll: PullSyncWorker refreshes
 * reminder_states from the server on its own periodic schedule, then calls
 * checkAndNotify() here to surface anything due/overdue and not yet
 * notified. Reuses that same pull rather than issuing its own
 * due_before_or_on/notified-filtered request, since the data's already
 * fresh in Room by the time this runs.
 */
class ReminderNotifier(
    private val context: Context,
    private val plantDao: PlantDao,
    private val reminderTypeDao: ReminderTypeDao,
    private val reminderStateRepository: ReminderStateRepository,
) {
    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_reminders_description)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    suspend fun checkAndNotify() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return // Nothing to do until the user grants it -- states stay unnotified for the next check to pick up.
        }

        val due = reminderStateRepository.getDueUnnotified(LocalDate.now().toString())
        if (due.isEmpty()) return

        val plantsById = plantDao.getAllOnce().associateBy { it.localId }
        val typesById = reminderTypeDao.getAllOnce().associateBy { it.localId }
        val notificationManager = NotificationManagerCompat.from(context)

        due.forEach { state ->
            val plant = plantsById[state.plantLocalId] ?: return@forEach
            val type = typesById[state.reminderTypeLocalId] ?: return@forEach
            val overdue = state.dueDate != null && state.dueDate < LocalDate.now().toString()

            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_PLANT_LOCAL_ID, plant.localId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                state.localId.toInt(),
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (overdue) "${type.name} overdue" else "${type.name} due today")
                .setContentText(plant.name)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            runCatching { notificationManager.notify(state.localId.toInt(), notification) }
            reminderStateRepository.markNotified(state)
        }
    }
}
