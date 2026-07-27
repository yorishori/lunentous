package com.lunentous.app.di

import android.content.Context
import androidx.room.Room
import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.LunentousDatabase
import com.lunentous.app.data.local.entity.OutboxEntityType
import com.lunentous.app.data.notifications.ReminderNotifier
import com.lunentous.app.data.remote.ConnectivityObserver
import com.lunentous.app.data.remote.NetworkModule
import com.lunentous.app.data.repository.AccountRepository
import com.lunentous.app.data.repository.PhaseTypeRepository
import com.lunentous.app.data.repository.PhaseWindowRepository
import com.lunentous.app.data.repository.PlantRepository
import com.lunentous.app.data.repository.ReminderRuleRepository
import com.lunentous.app.data.repository.ReminderStateRepository
import com.lunentous.app.data.repository.ReminderTypeRepository
import com.lunentous.app.data.repository.TimelineRepository
import com.lunentous.app.data.sync.dates.ProvisionalDueDateCalculator
import com.lunentous.app.data.sync.outbox.OutboxProcessor
import com.lunentous.app.data.sync.outbox.OutboxRepository
import com.lunentous.app.ui.widget.refreshLunentousWidget

/**
 * Manual DI -- no Hilt/Dagger. The app is small enough that a hand-wired
 * container of app-scoped singletons is simpler than an annotation
 * processor and its build-time cost, per the Android plan's package
 * structure notes.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val sessionStore = SessionStore(context)
    val connectivityObserver = ConnectivityObserver(context.applicationContext)

    private val database = Room.databaseBuilder(
        context.applicationContext,
        LunentousDatabase::class.java,
        "lunentous.db",
    )
        // Pre-release, no shipped installs yet -- fine to wipe local cache
        // data on schema bumps rather than writing real migrations.
        .fallbackToDestructiveMigration()
        .build()

    private val gson = NetworkModule.createGson()
    private val api = NetworkModule.createApi(sessionStore, gson)

    val outboxRepository = OutboxRepository(database.outboxDao(), gson, context.applicationContext)

    private val provisionalDueDateCalculator = ProvisionalDueDateCalculator(
        database.reminderRuleDao(),
        database.overridePeriodDao(),
        database.timelineEventDao(),
        database.reminderStateDao(),
    )

    val plantRepository = PlantRepository(database.plantDao(), api, sessionStore, outboxRepository, gson)
    val reminderTypeRepository = ReminderTypeRepository(database.reminderTypeDao(), api, sessionStore, outboxRepository, gson)
    val phaseTypeRepository = PhaseTypeRepository(database.phaseTypeDao(), api, sessionStore, outboxRepository, gson)
    val reminderRuleRepository = ReminderRuleRepository(
        database.reminderRuleDao(),
        database.overridePeriodDao(),
        database.plantDao(),
        database.reminderTypeDao(),
        api,
        sessionStore,
        outboxRepository,
        gson,
        provisionalDueDateCalculator,
    )
    val reminderStateRepository = ReminderStateRepository(
        database.reminderStateDao(),
        database.plantDao(),
        database.reminderTypeDao(),
        api,
        sessionStore,
    )
    val phaseWindowRepository = PhaseWindowRepository(
        database.phaseWindowDao(),
        database.plantDao(),
        database.phaseTypeDao(),
        api,
        sessionStore,
        outboxRepository,
        gson,
    )
    val timelineRepository = TimelineRepository(
        database.timelineEventDao(),
        database.photoDao(),
        database.plantDao(),
        database.reminderTypeDao(),
        api,
        sessionStore,
        outboxRepository,
        gson,
        provisionalDueDateCalculator,
    )
    val accountRepository = AccountRepository(api, sessionStore)

    val reminderNotifier = ReminderNotifier(
        context.applicationContext,
        database.plantDao(),
        database.reminderTypeDao(),
        reminderStateRepository,
    )

    val outboxProcessor = OutboxProcessor(
        outboxRepository,
        mapOf(
            OutboxEntityType.PLANT to plantRepository,
            OutboxEntityType.REMINDER_TYPE to reminderTypeRepository,
            OutboxEntityType.PHASE_TYPE to phaseTypeRepository,
            OutboxEntityType.REMINDER_RULE to reminderRuleRepository,
            OutboxEntityType.PHASE_WINDOW to phaseWindowRepository,
            OutboxEntityType.TIMELINE_EVENT to timelineRepository,
        ),
        sessionStore,
    )

    /** Called after any in-app mark-done/mutation that could change what
     * the home screen widget shows (see ui/widget/LunentousWidget.kt) --
     * PullSyncWorker refreshes it too, but that's up to ~4h stale otherwise. */
    suspend fun refreshWidget() = refreshLunentousWidget(appContext)
}
