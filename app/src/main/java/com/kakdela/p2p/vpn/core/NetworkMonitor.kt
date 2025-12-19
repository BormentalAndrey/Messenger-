package com.kakdela.p2p.vpn.core

import android.net.*
import android.content.Context
import com.kakdela.p2p.vpn.service.AutoRestartWorker

class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    fun register() {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                AutoRestartWorker.schedule(cm.context)
            }

            override fun onLost(network: Network) {
                AutoRestartWorker.schedule(cm.context)
            }
        })
    }
}
