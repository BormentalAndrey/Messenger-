package com.kakdela.p2p.services

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.MainActivity
import com.kakdela.p2p.MyApplication
import com.kakdela.p2p.R
import com.kakdela.p2p.data.IdentityRepository

class P2PService : Service() {

    private lateinit var identityRepository: IdentityRepository

    override fun onCreate() {
        super.onCreate()

        // ✅ Получаем единый репозиторий из Application
        identityRepository =
            (application as MyApplication).identityRepository

        // 🔐 Гарантируем, что идентичность готова
        identityRepository.ensureIdentity()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()

        // 🚀 ЗАПУСК P2P-УЗЛА
        identityRepository.startP2PNode()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        // 🧹 Корректно останавливаем сеть
        identityRepository.stopP2PNode()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------- FOREGROUND --------------------

    private fun startAsForeground() {
        val channelId = "p2p_node_channel"
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "P2P Node",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("P2P мессенджер активен")
            .setContentText("Устройство работает как узел сети")
            .setSmallIcon(R.drawable.ic_p2p_node)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1, notification)
        }
    }
}
