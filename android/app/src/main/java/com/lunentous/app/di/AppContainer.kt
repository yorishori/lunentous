package com.lunentous.app.di

import android.content.Context
import androidx.room.Room
import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.LunentousDatabase
import com.lunentous.app.data.remote.NetworkModule
import com.lunentous.app.data.repository.PhaseTypeRepository
import com.lunentous.app.data.repository.PhaseWindowRepository
import com.lunentous.app.data.repository.PlantRepository
import com.lunentous.app.data.repository.ReminderRuleRepository
import com.lunentous.app.data.repository.ReminderStateRepository
import com.lunentous.app.data.repository.ReminderTypeRepository
import com.lunentous.app.data.repository.TimelineRepository

/**
 * Manual DI -- no Hilt/Dagger. The app is small enough that a hand-wired
 * container of app-scoped singletons is simpler than an annotation
 * processor and its build-time cost, per the Android plan's package
 * structure notes.
 */
class AppContainer(context: Context) {
    val sessionStore = SessionStore(context)

    private val database = Room.databaseBuilder(
        context.applicationContext,
        LunentousDatabase::class.java,
        "lunentous.db",
    ).build()

    private val api = NetworkModule.createApi(sessionStore)

    val plantRepository = PlantRepository(database.plantDao(), api, sessionStore)
    val reminderTypeRepository = ReminderTypeRepository(database.reminderTypeDao(), api, sessionStore)
    val phaseTypeRepository = PhaseTypeRepository(database.phaseTypeDao(), api, sessionStore)
    val reminderRuleRepository = ReminderRuleRepository(database.reminderRuleDao(), database.overridePeriodDao(), api, sessionStore)
    val reminderStateRepository = ReminderStateRepository(database.reminderStateDao(), api, sessionStore)
    val phaseWindowRepository = PhaseWindowRepository(database.phaseWindowDao(), api, sessionStore)
    val timelineRepository = TimelineRepository(database.timelineEventDao(), database.photoDao(), api, sessionStore)
}
