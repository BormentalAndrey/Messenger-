package com.kakdela.p2p.vpn.core

import android.content.Context
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.config.*
import com.wireguard.crypto.Key

class VpnBackend(private val context: Context) {

    private val backend by lazy { WgQuickBackend(context) }

    private val tunnel = object : Tunnel {
        override fun getName() = "CloudflareWARP"
        override fun onStateChange(newState: Tunnel.State) {}
    }

    fun build(config: WarpRegistrar.WarpConfig): Config {
        val iface = Interface.Builder()
            .addAddress(InetNetwork.parse(config.address))
            .setPrivateKey(Key.fromBase64(config.privateKey))
            .addDnsServer(java.net.InetAddress.getByName("1.1.1.1"))
            .build()

        val peer = Peer.Builder()
            .setPublicKey(Key.fromBase64(config.publicKey))
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
            .setEndpoint(InetEndpoint.parse(config.endpoint))
            .setPersistentKeepalive(25)
            .build()

        return Config.Builder()
            .setInterface(iface)
            .addPeer(peer)
            .build()
    }

    fun up(cfg: Config) = backend.setState(tunnel, Tunnel.State.UP, cfg)
    fun down() = backend.setState(tunnel, Tunnel.State.DOWN, null)
}
