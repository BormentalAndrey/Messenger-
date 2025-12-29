package com.kakdela.p2p.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService as AndroidVpnService
import android.os.Build

class VpnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            if (AndroidVpnService.prepare(context) == null) {
                val i = Intent(context, KakdelaVpnService::class.java)
                    .setAction(KakdelaVpnService.ACTION_CONNECT)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            }
        }
    }
}

