package com.kakdela.p2p.services

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.MainActivity
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.security.CryptoManager

class P2PService : Service() {

    private val TAG = "P2PService"
    private var identityRepository: IdentityRepository? = null

    override fun onCreate() {
        super.onCreate()
        try {
            CryptoManager.init(applicationContext)

            // Получаем глобальный инстанс репозитория
            val app = applicationContext as com.kakdela.p2p.MyApplication
            identityRepository = app.identityRepository

            identityRepository?.startNetwork()
            Log.d(TAG, "P2P Service Started")
        } catch (e: Exception) {
            Log.e(TAG, "Service Init Error: ${e.message}", e)
        }
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
                channelId, "P2P Сеть", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Работа узла связи" }
            notificationManager?.createNotificationChannel(channel)
        }

        val pendingIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("KakDela P2P")
            .setContentText("Узел связи активен")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Foreground Error: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        identityRepository?.stopNetwork()
        identityRepository = null
        super.onDestroy()
    }
}
