package com.kakdela.p2p.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class VpnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            if (VpnService.prepare(context) == null) {
                val i = Intent(context, MyVpnService::class.java)
                    .setAction(MyVpnService.ACTION_CONNECT)
                context.startForegroundService(i)
            }
        }
    }
}
