package com.kakdela.p2p.vpn.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.MainActivity
import com.kakdela.p2p.R
import com.kakdela.p2p.vpn.core.VpnBackend
import com.kakdela.p2p.vpn.core.WgKeyStore
import com.kakdela.p2p.vpn.data.ServerRepository
import kotlinx.coroutines.*

class VpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "vpn.CONNECT"
        const val ACTION_DISCONNECT = "vpn.DISCONNECT"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var backend: VpnBackend

    override fun onCreate() {
        super.onCreate()
        backend = VpnBackend(this)

        // 🔥 НАСТОЯЩИЙ KILL SWITCH
        setUnderlyingNetworks(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        startForegroundCompat()

        if (intent?.action == ACTION_DISCONNECT) {
            backend.down()
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch {
            val server = ServerRepository(this@VpnService).load().first()
            val privKey = WgKeyStore(this@VpnService).getPrivateKey()
            val config = backend.buildConfig(privKey, server)
            backend.up(config)
        }

        return START_STICKY
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification())
        }
    }

    private fun notification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "vpn")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("VPN активен")
            .setContentText("Весь трафик защищён")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
