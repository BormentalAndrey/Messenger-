package com.kakdela.p2p.vpn.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.kakdela.p2p.vpn.core.VpnBackend
import com.kakdela.p2p.vpn.model.VpnServer

class VpnService : Service() {
    private val vpnBackend by lazy { VpnBackend(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    fun startVpn(server: VpnServer, myPrivateKey: String) {
        // Убедитесь, что server.port — это Int, а остальные — String
        val config = vpnBackend.buildConfig(
            serverHost = server.host,
            serverPort = server.port, 
            serverPubKey = server.publicKey,
            privateKey = myPrivateKey
        )
        vpnBackend.up(config)
    }

    fun stopVpn() {
        vpnBackend.down() // Теперь метод down() определен в VpnBackend
    }
}

