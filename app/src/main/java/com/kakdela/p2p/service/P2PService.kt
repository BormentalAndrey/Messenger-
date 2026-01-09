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
import com.kakdela.p2p.security.CryptoManager

class P2PService : Service() {

    private var identityRepository: IdentityRepository? = null

    override fun onCreate() {
        super.onCreate()
        // Гарантируем, что ключи доступны сервису
        CryptoManager.init(applicationContext)
        identityRepository = IdentityRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        return START_STICKY
    }

    private fun promoteToForeground() {
        val channelId = "p2p_node_channel"
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, 
                "P2P Node Connectivity",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Обеспечивает работу P2P узла в фоновом режиме"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(
                this, 
                0, 
                it, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("P2P Сеть активна")
            .setContentText("Ваше устройство работает как защищенный узел")
            .setSmallIcon(R.drawable.ic_p2p_node) // Убедитесь, что иконка существует
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1001, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(1001, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Очищаем ресурсы репозитория (закрываем сокеты и т.д.)
        identityRepository?.stopP2PNode()
        super.onDestroy()
    }
}
