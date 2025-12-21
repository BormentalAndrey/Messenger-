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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.kakdela.p2p.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.kakdela.p2p.vpn.DISCONNECT"
    }

    private val backend by lazy { VpnBackend(this) }
    private val keyStore by lazy { WgKeyStore(this) }
    private val scope = CoroutineScope(Dispatchers.IO)

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "warp_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Запуск Foreground (обязательно для VPN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                createNotification(), 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        if (intent?.action == ACTION_DISCONNECT) {
            stopVpn()
            return START_NOT_STICKY
        }

        // Запуск подключения в фоновом потоке
        scope.launch {
            try {
                // Генерируем конфиг и поднимаем туннель
                val config = backend.buildWarpConfig(keyStore.getPrivateKey())
                backend.up(config)
            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf() // Остановить сервис, если ошибка
            }
        }

        return START_STICKY
    }

    private fun stopVpn() {
        try {
            backend.down()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        // Вызывается, если пользователь отключил VPN через настройки системы
        stopVpn()
        super.onRevoke()
    }

    private fun createNotification(): Notification {
        createChannel()
        
        // Кнопка "Отключить" в уведомлении (опционально)
        val disconnectIntent = Intent(this, VpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WARP Protection")
            .setContentText("Соединение зашифровано")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отключить", disconnectPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WARP VPN Status",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}

