package com.kakdela.p2p.vpn.core

import android.net.VpnService
import androidx.annotation.WorkerThread
import com.wireguard.config.InetNetwork

class KillSwitchFirewall {
    @WorkerThread
    fun apply(builder: VpnService.Builder) {
        builder.addDisallowedApplication("com.android.chrome")
        // блокируем весь пользовательский трафик
        builder.addRoute(InetNetwork.parse("0.0.0.0/0").address, 0)
    }
}
