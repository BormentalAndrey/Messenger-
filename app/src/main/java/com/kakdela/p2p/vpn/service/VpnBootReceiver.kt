package com.kakdela.p2p.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VpnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val i = Intent(context, KakdelaVpnService::class.java)
            i.action = KakdelaVpnService.ACTION_CONNECT
            context.startForegroundService(i)
        }
    }
}
