package com.kakdela.p2p.vpn.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.kakdela.p2p.vpn.core.VpnBackend
import com.kakdela.p2p.vpn.model.VpnServer

class VpnService : Service() {
    private val vpnBackend by lazy { VpnBackend(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    // Эта функция вызывается при старте VPN
    fun startVpn(server: VpnServer, myPrivateKey: String) {
        // Исправлено: передаем поля объекта VpnServer по отдельности
        val config = vpnBackend.buildConfig(
            serverHost = server.host,
            serverPort = server.port,
            serverPubKey = server.publicKey,
            privateKey = myPrivateKey
        )
        vpnBackend.up(config)
    }

    fun stopVpn() {
        vpnBackend.down()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Здесь логика обработки команд старта/стопа через Intent
        return START_STICKY
    }
}

