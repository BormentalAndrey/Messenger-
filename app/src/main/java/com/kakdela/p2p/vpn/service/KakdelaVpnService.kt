package com.kakdela.p2p.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.R

class KakdelaVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ❗ ИСПРАВЛЕНИЕ ОШИБКИ КРАША ❗
        // Для Android 14 (SDK 34) и выше ОБЯЗАТЕЛЬНО передавать тип сервиса
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                1, 
                createNotification(), 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_VPN
            )
        } else {
            // Для старых версий (Android 13 и ниже)
            startForeground(1, createNotification())
        }

        startDnsVpn()
        return START_STICKY
    }

    private fun startDnsVpn() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
            builder.setSession("Kakdela DNS VPN")

            // Фиктивный адрес локальной сети VPN
            builder.addAddress("10.0.0.2", 32)

            // DNS серверы (Cloudflare + Google)
            builder.addDnsServer("1.1.1.1")
            builder.addDnsServer("8.8.8.8")

            // Маршрутизация всего трафика через VPN
            builder.addRoute("0.0.0.0", 0)

            // IPv4 обязателен. IPv6 в try-catch, так как на некоторых эмуляторах/устройствах он вызывает сбой
            try {
                builder.allowFamily(android.system.OsConstants.AF_INET)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // IPv6 (опционально, лучше обернуть в try/catch)
            try {
                builder.allowFamily(android.system.OsConstants.AF_INET6)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            vpnInterface = builder.establish()
        } catch (e: Exception) {
            e.printStackTrace()
            // Если не удалось создать интерфейс, останавливаем сервис, чтобы не висело уведомление
            stopSelf()
        }
    }

    private fun createNotification(): Notification {
        val channelId = "vpn_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Kakdela VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }

        // Используем mipmap иконку, так как она точно есть (R.drawable.ic_vpn может отсутствовать)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Как дела? VPN")
            .setContentText("DNS защита активна")
            .setSmallIcon(R.mipmap.ic_launcher) 
            .setOngoing(true) // Уведомление нельзя смахнуть
            .build()
    }

    override fun onDestroy() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vpnInterface = null
        super.onDestroy()
    }
}

