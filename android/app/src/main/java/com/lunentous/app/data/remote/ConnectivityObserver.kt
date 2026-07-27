package com.lunentous.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.lunentous.app.data.sync.outbox.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for "is there a usable network right now" --
 * backs the sync status chip's Offline state and is also where the
 * outbox's regained-connectivity sync trigger lives (see the Android
 * plan's three sync triggers), so the two don't drift out of sync with
 * each other.
 */
class ConnectivityObserver(private val context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val isOnlineFlow = MutableStateFlow(currentlyOnline())
    val isOnline: StateFlow<Boolean> = isOnlineFlow

    init {
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        connectivityManager?.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    isOnlineFlow.value = true
                    SyncScheduler.triggerOutboxSync(context)
                }

                override fun onLost(network: Network) {
                    isOnlineFlow.value = currentlyOnline()
                }
            },
        )
    }

    private fun currentlyOnline(): Boolean {
        val capabilities = connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
