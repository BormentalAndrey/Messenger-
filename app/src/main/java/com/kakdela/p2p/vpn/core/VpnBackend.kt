package com.kakdela.p2p.vpn.core

import android.content.Context
import com.wireguard.android.backend.GoBackend  // Рекомендую GoBackend (быстрее и стабильнее WgQuickBackend)
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import java.net.InetAddress

class VpnBackend(private val context: Context) {

    private val backend by lazy { GoBackend(context) }

    private val tunnel = object : Tunnel {
        override fun getName() = "WARP"

        override fun onStateChange(state: Tunnel.State) {
            // Можно добавить логирование состояния для отладки
        }
    }

    fun buildWarpConfig(privateKey: String): Config {
        val iface = Interface.Builder()
            .addAddress(InetNetwork.parse("172.16.0.2/32"))
            .addDnsServer(InetAddress.getByName("1.1.1.1"))
            .parsePrivateKey(privateKey)  // Правильный метод для приватного ключа
            .build()

        val peer = Peer.Builder()
            .parsePublicKey("bmXOC+F1FxEMY9dyU9S47Vp00nU8NAs4W8uNP0R2D1s=")  // Правильный метод для публичного ключа
            .endpoint(InetEndpoint.parse("162.159.193.2:2408"))
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
            .addAllowedIp(InetNetwork.parse("::/0"))  // Для поддержки IPv6 (опционально, но рекомендуется)
            .persistentKeepalive(25)
            .build()

        return Config.Builder()
            .setInterface(iface)
            .addPeer(peer)
            .build()
    }

    fun up(config: Config) = backend.setState(tunnel, Tunnel.State.UP, config)

    fun down() = backend.setState(tunnel, Tunnel.State.DOWN, null)
}
