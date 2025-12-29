package com.kakdela.p2p.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.R

class KakdelaVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "ACTION_CONNECT"
        private const val CHANNEL_ID = "vpn_channel"
        private const val NOTIF_ID = 1
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CONNECT) {
            startVpn()
        }
        return Service.START_STICKY
    }

    private fun startVpn() {
        createNotification()

        val builder = Builder()
            .setSession("Kakdela VPN")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        vpnInterface = builder.establish()
    }

    private fun createNotification() {
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kakdela VPN")
            .setContentText("VPN активен")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
