package com.kakdela.p2p.services

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.MainActivity
import com.kakdela.p2p.R
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.security.CryptoManager

class P2PService : Service() {

    private val TAG = "P2PService"
    private var identityRepository: IdentityRepository? = null

    override fun onCreate() {
        super.onCreate()
        try {
            // Инициализация криптографии (безопасно вызывать повторно)
            CryptoManager.init(applicationContext)

            // ✅ ИСПРАВЛЕНИЕ:
            // Берем ГЛОБАЛЬНЫЙ репозиторий из Application,
            // а не создаем новый (устраняем конфликт портов)
            val app = applicationContext as com.kakdela.p2p.MyApplication
            identityRepository = app.identityRepository

            // Запускаем сеть (внутри есть защита от повторного запуска)
            identityRepository?.startNetwork()

            Log.d(TAG, "P2P Service attached to Global Repository")
        } catch (e: Exception) {
            Log.e(TAG, "Service Init Error: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // В Android 14+ startForeground должен вызываться максимально быстро
        promoteToForeground()

        // Перезапуск при убийстве системой
        return START_STICKY
    }

    private fun promoteToForeground() {
        val channelId = "p2p_node_channel"
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "P2P Сеть",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Обеспечивает работу защищенного узла связи"
            }
            notificationManager?.createNotificationChannel(channel)
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
            .setContentTitle("KakDela P2P Активен")
            .setContentText("Ваше устройство работает как узел связи")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            )
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1001,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска Foreground: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Ключевое: закрываем сеть через stopNetwork(),
     * так как именно там закрываются сокеты и корутины
     */
    override fun onDestroy() {
        Log.d(TAG, "Остановка P2P узла...")
        try {
            identityRepository?.stopNetwork()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при закрытии сети: ${e.message}", e)
        }
        identityRepository = null
        super.onDestroy()
    }
}
