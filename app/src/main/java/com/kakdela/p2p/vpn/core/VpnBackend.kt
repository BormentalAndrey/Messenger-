package com.kakdela.p2p.vpn.core

import android.content.Context
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.config.*
import com.wireguard.crypto.Key

class VpnBackend(private val context: Context) {
    private val backend by lazy { WgQuickBackend(context) }
    
    private val tunnel = object : Tunnel {
        override fun getName(): String = "CloudflareWARP"
        override fun onStateChange(newState: Tunnel.State) {}
    }

    fun buildWarpConfig(privateKey: String): Config {
        // Стандартные параметры Cloudflare WARP
        val interfaceBuilder = Interface.Builder()
            .addAddress(InetNetwork.parse("172.16.0.2/32"))
            .setPrivateKey(Key.fromBase64(privateKey))
            .addDnsServer(java.net.InetAddress.getByName("1.1.1.1"))
            .build()

        val peerBuilder = Peer.Builder()
            .setPublicKey(Key.fromBase64("bmXOC+F1FxEMY9dyU9S47Vp00nU8NAs4W8uNP0R2D1s=")) // Публичный ключ Cloudflare
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0")) // Весь трафик приложения
            .setEndpoint(InetEndpoint.parse("162.159.193.2:2408")) // Или 162.159.192.1:2408
            .setPersistentKeepalive(25)
            .build()

        return Config.Builder()
            .setInterface(interfaceBuilder)
            .addPeer(peerBuilder)
            .build()
    }

    fun up(config: Config) {
        try {
            backend.setState(tunnel, Tunnel.State.UP, config)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun down() {
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } catch (e: Exception) { e.printStackTrace() }
    }
}
