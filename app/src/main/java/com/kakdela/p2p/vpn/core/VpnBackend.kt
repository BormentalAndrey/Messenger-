package com.kakdela.p2p.vpn.core

import android.content.Context
import android.net.VpnService
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.config.*

class VpnBackend(private val context: Context) {
    private val backend by lazy { WgQuickBackend(context) }

    private val tunnel = object : Tunnel {
        override fun getName(): String = "WARP"
        override fun onStateChange(newState: Tunnel.State) {}
    }

    fun buildWarpConfig(pk: String): Config {
        val iface = Interface.Builder()
            .addAddress(InetNetwork.parse("172.16.0.2/32"))
            .setPrivateKey(com.wireguard.crypto.Key.fromBase64(pk))
            .addDnsServer(java.net.InetAddress.getByName("1.1.1.1"))
            .build()

        val peer = Peer.Builder()
            .setPublicKey(com.wireguard.crypto.Key.fromBase64("bmXOC+F1FxEMY9dyU9S47Vp00nU8NAs4W8uNP0R2D1s="))
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
            .setEndpoint(InetEndpoint.parse("162.159.193.2:2408"))
            .setPersistentKeepalive(25)
            .build()

        return Config.Builder()
            .setInterface(iface)
            .addPeer(peer)
            .build()
    }

    fun safeReconnect() {
        try {
            val pk = WgKeyStore(context).getPrivateKey()
            up(buildWarpConfig(pk))
        } catch (_: Throwable) {}
    }

    fun up(config: Config) {
        backend.setState(tunnel, Tunnel.State.UP, config)
    }

    fun down() {
        backend.setState(tunnel, Tunnel.State.DOWN, null)
    }
}
