package com.kakdela.p2p.vpn.core

import android.content.Context
import com.kakdela.p2p.vpn.model.VpnServer
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.config.InetNetwork
import com.wireguard.config.Config

class VpnBackend(private val context: Context) {

    private val backend by lazy { WgQuickBackend(context) }
    private val keyStore = WgKeyStore(context)

    fun buildConfig(server: VpnServer): Config {
        val privateKey = keyStore.getPrivateKey()

        val iface = Interface.Builder()
            .addAddress(InetNetwork.parse("10.0.0.2/32")) // адрес клиента
            .setPrivateKey(privateKey)

        val peer = Peer.Builder()
            .setPublicKey(server.publicKey)
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
            .setEndpoint("${server.host}:${server.port}")

        return Config.Builder()
            .setInterface(iface.build())
            .addPeer(peer.build())
            .build()
    }

    fun up(name: String, config: Config) {
        backend.setState(name, config, com.wireguard.android.backend.Tunnel.State.UP)
    }

    fun down(name: String) {
        backend.setState(name, null, com.wireguard.android.backend.Tunnel.State.DOWN)
    }
}
