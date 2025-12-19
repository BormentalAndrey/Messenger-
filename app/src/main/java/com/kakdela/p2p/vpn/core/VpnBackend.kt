package com.kakdela.p2p.vpn.core

import android.content.Context
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.config.Config
import com.wireguard.config.InetAddress
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key

class VpnBackend(private val context: Context) {

    private val backend by lazy { WgQuickBackend(context) }

    private val tunnel = object : Tunnel {
        override fun getName() = "WARP"
        override fun onStateChange(state: Tunnel.State) {}
    }

    fun buildWarpConfig(privateKey: String): Config {
        val iface = Interface.Builder()
            .addAddress(InetNetwork.parse("172.16.0.2/32"))
            .addDnsServer(java.net.InetAddress.getByName("1.1.1.1"))
            .setPrivateKey(Key.fromBase64(privateKey))
            .build()

        val peer = Peer.Builder()
            .setPublicKey(Key.fromBase64("bmXOC+F1FxEMY9dyU9S47Vp00nU8NAs4W8uNP0R2D1s="))
            .setEndpoint(InetEndpoint.parse("162.159.193.2:2408"))
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
            .setPersistentKeepalive(25)
            .build()

        return Config.Builder()
            .setInterface(iface)
            .addPeer(peer)
            .build()
    }

    fun up(config: Config) = backend.setState(tunnel, Tunnel.State.UP, config)

    fun down() = backend.setState(tunnel, Tunnel.State.DOWN, null)
}
