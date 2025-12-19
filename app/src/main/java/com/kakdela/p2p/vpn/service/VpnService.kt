package com.kakdela.p2p.vpn.service

import android.net.VpnService
import android.os.IBinder
import com.kakdela.p2p.vpn.core.*

class VpnService : VpnService() {
    private val backend by lazy { VpnBackend(this) }
    private val keyStore by lazy { WgKeyStore(this) }
    private val firewall by lazy { KillSwitchFirewall() }

    override fun onCreate() {
        super.onCreate()
        NetworkMonitor(this).register()
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
        firewall.apply(builder)
        builder.establish()

        backend.safeReconnect()
        AutoRestartWorker.schedule(this)

        return START_STICKY
    }

    override fun onBind(intent: android.content.Intent?): IBinder? = null
}
