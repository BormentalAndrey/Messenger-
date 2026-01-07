package com.kakdela.p2p.services

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.MainActivity
import com.kakdela.p2p.R
import com.kakdela.p2p.data.IdentityRepository

class P2PService : Service() {

    private lateinit var identityRepository: IdentityRepository

    override fun onCreate() {
        super.onCreate()
        // Инициализируем репозиторий прямо в сервисе
        identityRepository = IdentityRepository(applicationContext)
        startForegroundService()
    }

    private fun startForegroundService() {
        val channelId = "p2p_node_channel"
        val notificationManager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            channelId, "P2P Node Active",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        val pendingIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Мессенджер активен")
            .setContentText("Ваш телефон работает как узел P2P сети")
            .setSmallIcon(R.drawable.ic_p2p_node) // Замени на свою иконку
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Перезапускать сервис, если система его закроет
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
