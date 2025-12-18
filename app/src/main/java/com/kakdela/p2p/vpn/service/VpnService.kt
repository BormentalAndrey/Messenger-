// Файл: app/src/main/java/com/kakdela/p2p/vpn/service/VpnService.kt
package com.kakdela.p2p.vpn.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.kakdela.p2p.vpn.core.VpnBackend
import com.kakdela.p2p.vpn.model.VpnServer

class VpnService : Service() {
    // Используем lazy, чтобы контекст 'this' был доступен только после создания сервиса
    private val vpnBackend by lazy { VpnBackend(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    fun startVpn(server: VpnServer, privateKey: String) {
        val config = vpnBackend.buildConfig(server, privateKey)
        vpnBackend.up(config)
    }

    fun stopVpn() {
        vpnBackend.down()
    }
}

