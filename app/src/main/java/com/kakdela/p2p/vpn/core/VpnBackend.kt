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
import com.wireguard.util.InetNetwork

class VpnBackend(private val context: Context) {

    private val backend by lazy { WgQuickBackend(context) }
    
    // Объект туннеля для управления состоянием
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

    // Здесь должен быть ТОЛЬКО ОДИН аргумент
    fun up(config: Config) {
        try {
            backend.setState(tunnel, Tunnel.State.UP, config)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Здесь аргументов быть НЕ ДОЛЖНО
    fun down() {
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

