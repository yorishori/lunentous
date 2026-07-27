package com.lunentous.app.data.settings

import android.content.Context
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Plain (unencrypted) SharedPreferences, same reasoning as
 * AppearanceStore -- nothing here is sensitive. */
class NotificationScheduleStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lunentous_notification_schedule", Context.MODE_PRIVATE)

    private val timeFlow = MutableStateFlow(loadTime())
    val time: StateFlow<LocalTime> = timeFlow

    fun setTime(newTime: LocalTime) {
        prefs.edit().putInt(KEY_HOUR, newTime.hour).putInt(KEY_MINUTE, newTime.minute).apply()
        timeFlow.value = newTime
    }

    private fun loadTime(): LocalTime {
        val hour = prefs.getInt(KEY_HOUR, DEFAULT_HOUR)
        val minute = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)
        return LocalTime.of(hour, minute)
    }

    companion object {
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
        private const val DEFAULT_HOUR = 9
        private const val DEFAULT_MINUTE = 0
    }
}
