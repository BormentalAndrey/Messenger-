package com.kakdela.p2p.vpn.service

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.R

class KakdelaVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        startDnsVpn()
        return START_STICKY
    }

    private fun startDnsVpn() {
        if (vpnInterface != null) return

        val builder = Builder()
        builder.setSession("Kakdela DNS VPN")

        // фиктивный адрес
        builder.addAddress("10.0.0.2", 32)

        // DNS (Cloudflare + Google)
        builder.addDnsServer("1.1.1.1")
        builder.addDnsServer("8.8.8.8")

        // весь трафик через VPN
        builder.addRoute("0.0.0.0", 0)

        // важно!
        builder.allowFamily(android.system.OsConstants.AF_INET)
        builder.allowFamily(android.system.OsConstants.AF_INET6)

        vpnInterface = builder.establish()
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
                .createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Как дела? VPN")
            .setContentText("DNS защита активна")
            .setSmallIcon(R.drawable.ic_vpn)
            .build()
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
