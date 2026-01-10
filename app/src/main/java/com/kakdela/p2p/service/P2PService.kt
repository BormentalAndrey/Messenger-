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
            // Инициализация криптографии
            CryptoManager.init(applicationContext)
            
            // Инициализация репозитория
            identityRepository = IdentityRepository(applicationContext)
            
            // Запуск сетевых возможностей (UDP слушатель, NSD и т.д.)
            identityRepository?.startNetwork()
            
            Log.d(TAG, "P2P Node Service Created and Network Started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize P2P components: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Важно: в Android 14+ startForeground должен вызываться максимально быстро после onStartCommand
        promoteToForeground()
        
        // Возвращаем START_STICKY, чтобы система пересоздала сервис при нехватке памяти
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
            .setSmallIcon(android.R.drawable.ic_menu_share) // Стандартная иконка для надежности
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Используем SPECIAL_USE для кастомных P2P протоколов в Android 14+, 
                // либо CONNECTED_DEVICE (требует разрешения в манифесте)
                startForeground(
                    1001, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска Foreground: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Ключевое исправление: вызываем stopNetwork(), так как именно этот метод 
     * отвечает за закрытие сокетов и остановку корутин в IdentityRepository.
     */
    override fun onDestroy() {
        Log.d(TAG, "Остановка P2P узла...")
        try {
            identityRepository?.stopNetwork()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при закрытии сети: ${e.message}")
        }
        identityRepository = null
        super.onDestroy()
    }
}
