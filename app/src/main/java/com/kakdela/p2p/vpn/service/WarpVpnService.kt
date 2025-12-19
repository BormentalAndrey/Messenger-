package com.kakdela.p2p.vpn.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.kakdela.p2p.vpn.core.VpnBackend
import com.kakdela.p2p.vpn.core.WarpRegistrar
import com.kakdela.p2p.vpn.notification.VpnNotification
import kotlinx.coroutines.*

class WarpVpnService : Service() {

    private val backend by lazy { VpnBackend(this) }
    private val reg by lazy { WarpRegistrar(this) }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        startForeground(1, VpnNotification.create(this))

        CoroutineScope(Dispatchers.IO).launch {
            reg.load { cfg ->
                val config = backend.build(cfg)
                backend.up(config)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        backend.down()
        super.onDestroy()
    }
}
