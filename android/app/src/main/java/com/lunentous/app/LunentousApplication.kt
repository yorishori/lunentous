package com.lunentous.app

import android.app.Application
import com.lunentous.app.di.AppContainer

class LunentousApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
