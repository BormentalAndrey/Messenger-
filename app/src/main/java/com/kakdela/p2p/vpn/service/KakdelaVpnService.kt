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

    // Значение ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 0x40000000
    // Используем число, чтобы код компилировался на любых версиях SDK без ошибок импорта
    private val TYPE_SPECIAL_USE = 1073741824

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()

        // В Android 14 (API 34) обязательно указывать тип сервиса, иначе краш
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startForeground(1, notification, TYPE_SPECIAL_USE)
            } catch (e: Exception) {
                // Если вдруг упадет, пробуем старый метод (хотя на 14+ это вряд ли поможет)
                startForeground(1, notification)
            }
        } else {
            // Для Android 13 и ниже
            startForeground(1, notification)
        }

        startDnsVpn()
        return START_STICKY
    }

    private fun startDnsVpn() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
            builder.setSession("Kakdela DNS VPN")

            // Фиктивный адрес (стандартный для VPN туннелей)
            builder.addAddress("10.0.0.2", 32)

            // DNS (Cloudflare + Google)
            builder.addDnsServer("1.1.1.1")
            builder.addDnsServer("8.8.8.8")

            // Весь трафик через VPN
            builder.addRoute("0.0.0.0", 0)
            
            // Настройка MTU часто помогает стабильности
            builder.setMtu(1280) 

            // Попытка добавить IPv6 (иногда вызывает сбой, если сеть не поддерживает, но оставим как было у вас)
            try {
                // builder.allowFamily(android.system.OsConstants.AF_INET6) 
                // Закомментировал, так как часто вызывает проблемы на эмуляторах без полной поддержки IPv6
            } catch (e: Exception) {
                e.printStackTrace()
            }

            vpnInterface = builder.establish()
        } catch (e: Exception) {
            e.printStackTrace()
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

        // Используем R.mipmap.ic_launcher, так как R.drawable.ic_vpn может не существовать
        // Если у вас есть ic_vpn, замените обратно
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Kakdela VPN")
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

