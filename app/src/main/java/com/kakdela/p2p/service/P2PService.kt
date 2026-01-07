package com.kakdela.p2p.services

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.MainActivity
import com.kakdela.p2p.R
import com.kakdela.p2p.data.IdentityRepository

class P2PService : Service() {

    private lateinit var identityRepository: IdentityRepository

    override fun onCreate() {
        super.onCreate()
        identityRepository = IdentityRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        // ТУТ ДОЛЖЕН БЫТЬ ЗАПУСК ТВОЕГО P2P УЗЛА (например, запуск сервера или WebRTC)
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "p2p_node_channel"
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "P2P Node Active",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Мессенджер активен")
            .setContentText("Ваш телефон работает как узел P2P сети")
            .setSmallIcon(R.drawable.ic_p2p_node)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Чтобы пользователь не мог смахнуть
            .build()

        // ВАЖНО ДЛЯ ANDROID 14/15: Указываем тип сервиса
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

