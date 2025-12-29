package com.kakdela.p2p.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kakdela.p2p.vpn.VpnActions

class VpnBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {

            val vpnIntent = Intent(context, KakdelaVpnService::class.java).apply {
                action = VpnActions.ACTION_CONNECT
            }

            context.startForegroundService(vpnIntent)
        }
    }
}
