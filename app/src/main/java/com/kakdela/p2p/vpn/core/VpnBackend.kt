package com.kakdela.p2p.vpn.core

import android.content.Context
import com.kakdela.p2p.vpn.model.VpnServer
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.*
import java.net.InetAddress

class VpnBackend(context: Context) {

    private val backend = GoBackend(context)

    private val tunnel = object : Tunnel {
        override fun getName() = "KakdelaVPN"
        override fun onStateChange(state: Tunnel.State) {}
    }

    fun buildConfig(privateKey: String, server: VpnServer): Config {

        val iface = Interface.Builder()
            .parsePrivateKey(privateKey)
            .addAddress(InetNetwork.parse("172.16.0.2/32"))
            .addDnsServer(InetAddress.getByName("1.1.1.1"))
            .addDnsServer(InetAddress.getByName("8.8.8.8"))
            .build()

        val peer = Peer.Builder()
            .parsePublicKey(server.publicKey)
            .setEndpoint(InetEndpoint.parse("${server.host}:${server.port}"))
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
            .addAllowedIp(InetNetwork.parse("::/0"))
            .setPersistentKeepalive(25)
            .build()

        return Config.Builder()
            .setInterface(iface)
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
