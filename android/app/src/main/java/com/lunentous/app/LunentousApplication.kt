package com.lunentous.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.lunentous.app.data.sync.outbox.SyncScheduler
import com.lunentous.app.di.AppContainer

class LunentousApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        registerConnectivityTrigger()
    }

    /** One of the outbox's three sync triggers (enqueue and app-foreground
     * are the other two, see SyncScheduler) -- fires whenever the device
     * regains a usable network, so a queue that stalled offline drains as
     * soon as connectivity comes back instead of waiting for the next
     * periodic run. */
    private fun registerConnectivityTrigger() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    SyncScheduler.triggerOutboxSync(this@LunentousApplication)
                }
            },
        )
    }
}
