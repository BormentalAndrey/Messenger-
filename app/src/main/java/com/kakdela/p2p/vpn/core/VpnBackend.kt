// Файл: app/src/main/java/com/kakdela/p2p/vpn/core/VpnBackend.kt
package com.kakdela.p2p.vpn.core

import android.content.Context
import com.kakdela.p2p.vpn.model.VpnServer
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.config.InetEndpoint
import com.wireguard.crypto.Key
import com.wireguard.util.InetNetwork // ВАЖНО: правильный пакет для InetNetwork

class VpnBackend(private val context: Context) {
    private val backend by lazy { WgQuickBackend(context) }
    
    private val tunnel = object : Tunnel {
        override fun getName(): String = "P2PVpn"
        override fun onStateChange(newState: Tunnel.State) {}
    }

    fun buildConfig(server: VpnServer, privateKeyBase64: String): Config {
        val iFace = Interface.Builder()
            .addAddress(InetNetwork.parse("10.0.0.2/32"))
            .setPrivateKey(Key.fromBase64(privateKeyBase64))
            .build()

        val peer = Peer.Builder()
            .setPublicKey(Key.fromBase64(server.publicKey))
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
            .setEndpoint(InetEndpoint.parse("${server.host}:${server.port}"))
            .build()

        return Config.Builder()
            .setInterface(iFace)
            .addPeer(peer)
            .build()
    }

    fun up(config: Config) {
        backend.setState(tunnel, Tunnel.State.UP, config)
    }

    fun down() {
        backend.setState(tunnel, Tunnel.State.DOWN, null)
    }
}

