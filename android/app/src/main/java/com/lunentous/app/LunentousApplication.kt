package com.lunentous.app

import android.app.Application
import com.lunentous.app.data.sync.outbox.SyncScheduler
import com.lunentous.app.di.AppContainer

class LunentousApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // AppContainer eagerly constructs ConnectivityObserver, whose init
        // registers the regained-connectivity sync trigger -- nothing else
        // to wire up here.
        container = AppContainer(this)
        container.reminderNotifier.ensureChannel()
        SyncScheduler.schedulePeriodicPullSync(this)
    }
}
