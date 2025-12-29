package com.kakdela.p2p.vpn.service

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.R
import kotlinx.coroutines.*
import java.net.InetSocketAddress

class KakdelaVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
        startVpn()
    }

    private fun startVpn() {
        scope.launch {
            try {
                val builder = Builder()
                builder.setSession("Kakdela VPN")
                builder.addAddress("10.0.0.2", 32)
                builder.addDnsServer("1.1.1.1")
                builder.addRoute("0.0.0.0", 0)

                vpnInterface = builder.establish()
            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "vpn_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Как дела? VPN")
            .setContentText("VPN активен")
            .setSmallIcon(R.drawable.ic_vpn)
            .build()
    }

    override fun onDestroy() {
        vpnInterface?.close()
        scope.cancel()
        super.onDestroy()
    }
}
