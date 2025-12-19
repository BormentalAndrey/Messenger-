package com.kakdela.p2p.vpn.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kakdela.p2p.vpn.service.AutoRestartWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            AutoRestartWorker.schedule(context)
        }
    }
}
