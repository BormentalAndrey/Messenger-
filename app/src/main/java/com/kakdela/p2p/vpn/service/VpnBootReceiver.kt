package com.kakdela.p2p.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class VpnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Проверка: дано ли разрешение на VPN
            val prepare = VpnService.prepare(context)
            if (prepare == null) {
                // Если prepare == null, значит разрешение ЕСТЬ -> запускаем сервис
                val vpnIntent = Intent(context, com.kakdela.p2p.vpn.service.VpnService::class.java)
                vpnIntent.action = com.kakdela.p2p.vpn.service.VpnService.ACTION_CONNECT
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(vpnIntent)
                } else {
                    context.startService(vpnIntent)
                }
            }
        }
    }
}
