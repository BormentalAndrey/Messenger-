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
            // Инициализация криптографии для работы в контексте сервиса
            CryptoManager.init(applicationContext)
            
            // Инициализация репозитория (запуск UDP слушателя и Keep-Alive)
            identityRepository = IdentityRepository(applicationContext)
            
            Log.d(TAG, "P2P Node Service Created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize P2P components: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        // START_STICKY гарантирует перезапуск сервиса системой при нехватке памяти
        return START_STICKY
    }

    private fun promoteToForeground() {
        val channelId = "p2p_node_channel"
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Создание канала уведомлений для Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, 
                "P2P Node Connectivity",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Обеспечивает работу P2P узла в фоновом режиме"
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

        // Сборка уведомления Foreground сервиса
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("P2P Сеть активна")
            .setContentText("Ваше устройство работает как защищенный узел")
            .setSmallIcon(R.mipmap.ic_launcher) // Используем стандартную иконку, если ic_p2p_node нет
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        // Запуск в режиме Foreground с учетом ограничений Android 10+ и 14+
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1001, 
                    notification, 
                    // TYPE_CONNECTED_DEVICE наиболее подходит для P2P мессенджера
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Foreground start failed: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Исправлено: Вызов правильного метода очистки ресурсов
     */
    override fun onDestroy() {
        Log.d(TAG, "Stopping P2P Node Service...")
        
        // Вызываем onDestroy у репозитория для закрытия сокетов и отмены корутин
        // Раньше здесь был ошибочный stopP2PNode(), что вызывало Unresolved reference
        identityRepository?.onDestroy()
        
        identityRepository = null
        super.onDestroy()
    }
}
