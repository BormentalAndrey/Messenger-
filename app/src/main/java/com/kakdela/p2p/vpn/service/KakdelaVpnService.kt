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
    
    // Константа для FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    // Мы используем её, так как в Манифесте указали specialUse
    private val TYPE_SPECIAL_USE = 1073741824

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()

        // Для Android 14 (SDK 34+) обязательно указывать тип при запуске
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                // Передаем тип SPECIAL_USE, чтобы он совпал с Манифестом
                startForeground(1, notification, TYPE_SPECIAL_USE)
            } catch (e: Exception) {
                e.printStackTrace()
                // Фолбэк на случай ошибки (хотя с правильным манифестом её не будет)
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
            builder.addAddress("10.0.0.2", 32)
            builder.addDnsServer("1.1.1.1")
            builder.addDnsServer("8.8.8.8")
            builder.addRoute("0.0.0.0", 0)
            
            builder.setMtu(1280)

            try {
                builder.allowFamily(android.system.OsConstants.AF_INET)
            } catch (e: Exception) { e.printStackTrace() }

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

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Как дела? VPN")
            .setContentText("DNS защита активна")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
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

