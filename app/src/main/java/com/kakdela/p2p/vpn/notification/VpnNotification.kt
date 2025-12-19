package com.kakdela.p2p.vpn.notification

import android.app.*
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.R

object VpnNotification {
    const val CHANNEL_ID = "vpn_service"

    fun create(context: Context): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "VPN service",
                NotificationManager.IMPORTANCE_LOW
            )
            val mgr = context.getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(ch)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("VPN активен")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
}
