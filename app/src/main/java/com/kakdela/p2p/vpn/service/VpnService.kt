package com.kakdela.p2p.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.MainActivity
import com.kakdela.p2p.R
import com.kakdela.p2p.vpn.core.VpnBackend
import com.kakdela.p2p.vpn.core.WgKeyStore

class VpnService : VpnService() {

    private val backend by lazy { VpnBackend(this) }
    private val keyStore by lazy { WgKeyStore(this) }

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "warp_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ИСПРАВЛЕНО: Тип изменен на SPECIAL_USE для прохождения компиляции AAPT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                createNotification(), 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        // Запуск туннеля
        try {
            val config = backend.buildWarpConfig(keyStore.getPrivateKey())
            backend.up(config)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        backend.down()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        createChannel()

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WARP активен")
            .setContentText("Защищённое соединение через Cloudflare")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WARP VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Информирует о работе защищенного соединения"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}

