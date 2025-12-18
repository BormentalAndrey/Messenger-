package com.kakdela.p2p.vpn.core

import android.content.Context
import com.kakdela.p2p.vpn.model.VpnServer
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import com.wireguard.util.InetNetwork

class VpnBackend(private val context: Context) {

    private val backend by lazy { WgQuickBackend(context) }
    
    // Для работы с официальным SDK нужно создать объект Tunnel
    private val tunnel = object : Tunnel {
        override fun getName(): String = "P2PVpnTunnel"
        override fun onStateChange(newState: Tunnel.State) {
            // Здесь можно добавить логику уведомления UI о смене статуса
        }
    }

    fun buildConfig(server: VpnServer): Config {
        // В официальном SDK ключи создаются через Key.fromBase64
        // Убедитесь, что ваш keyStore возвращает строку Base64
        val privateKeyString = "ВАШ_ПРИВАТНЫЙ_КЛЮЧ_BASE64" 
        val privateKey = Key.fromBase64(privateKeyString)

        val iface = Interface.Builder()
            .addAddress(InetNetwork.parse("10.0.0.2/32"))
            .setPrivateKey(privateKey)
            .build()

        val peer = Peer.Builder()
            .setPublicKey(Key.fromBase64(server.publicKey))
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
            .setEndpoint("${server.host}:${server.port}")
            .build()

        return Config.Builder()
            .setInterface(iface)
            .addPeer(peer)
            .build()
    }

    fun up(config: Config) {
        try {
            // Официальный метод требует объект Tunnel, состояние и конфиг
            backend.setState(tunnel, Tunnel.State.UP, config)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun down() {
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

