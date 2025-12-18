package com.kakdela.p2p.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.R
import com.kakdela.p2p.vpn.core.VpnBackend
import com.kakdela.p2p.vpn.data.ServerRepository

class VpnService : Service() {

    private lateinit var backend: VpnBackend

    override fun onCreate() {
        super.onCreate()
        backend = VpnBackend(this)
        showNotification()
        startVpn()
    }

    private fun showNotification() {
        val channelId = "vpn_channel"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("VPN активен")
            .setContentText("Трафик проходит через защищённый туннель")
            .setSmallIcon(R.drawable.ic_vpn)
            .build()

        startForeground(1, notification)
    }

    private fun startVpn() {
        val server = ServerRepository(this).load().first()
        val config = backend.buildConfig(server)
        backend.up("kakdela", config)
    }

    override fun onDestroy() {
        backend.down("kakdela")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
