package com.kakdela.vpn.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.MainActivity
import com.kakdela.p2p.R
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class VpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(createNotificationId(), createNotification())
        startTunnel()
    }

    override fun onDestroy() {
        vpnJob?.cancel()
        vpnInterface?.close()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    private fun startTunnel() {
        vpnJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val builder = Builder()

                // Указываем виртуальный IP адрес VPN-интерфейса
                builder.addAddress("10.0.0.2", 24)

                // Редирект всего трафика
                builder.addRoute("0.0.0.0", 0)

                // DNS через Cloudflare
                builder.addDnsServer("1.1.1.1")
                builder.addDnsServer("8.8.8.8")

                // Настройки MTU
                builder.setMtu(1500)

                // Блокируем приложения? (false = все пойдут через VPN)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setBlockUntrustedApps(false)
                }

                vpnInterface = builder.establish()

                if (vpnInterface == null) {
                    stopSelf()
                    return@launch
                }

                val input = FileInputStream(vpnInterface!!.fileDescriptor)
                val output = FileOutputStream(vpnInterface!!.fileDescriptor)
                val packet = ByteBuffer.allocate(32767)

                // Простейшее "эхо" — пакеты читаются и возвращаются
                while (isActive) {
                    val len = input.read(packet.array())
                    if (len > 0) {
                        output.write(packet.array(), 0, len)
                    }
                }
            } catch (e: Exception) {
                stopSelf()
            }
        }
    }

    private fun createNotification(): Notification {
        val activityIntent = Intent(this, MainActivity::class.java)
        val pIntent = PendingIntent.getActivity(
            this, 1, activityIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, createNotificationChannel())
            .setContentTitle("VPN активен")
            .setContentText("Трафик проходит через защищённый туннель")
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(pIntent)
            .build()
    }

    private fun createNotificationId() = 1001

    private fun createNotificationChannel(): String {
        val channelId = "vpn_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "VPN",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
        return channelId
    }
}
