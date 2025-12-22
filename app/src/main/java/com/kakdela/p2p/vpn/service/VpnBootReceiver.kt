package com.kakdela.p2p.vpn.service

import android.content.*
import android.net.VpnService

class VpnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            if (VpnService.prepare(context) == null) {
                context.startForegroundService(
                    Intent(context, VpnService::class.java)
                )
            }
        }
    }
}
