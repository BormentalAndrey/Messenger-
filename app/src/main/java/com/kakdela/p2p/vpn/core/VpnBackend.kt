package com.kakdela.p2p.vpn.core

import android.content.Context
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.config.InetEndpoint
import com.wireguard.crypto.Key
// ВАЖНО: В этой версии библиотеки InetNetwork находится здесь:
import com.wireguard.util.InetNetwork

class VpnBackend(private val context: Context) {
    private val backend by lazy { WgQuickBackend(context) }
    
    private val tunnel = object : Tunnel {
        override fun getName(): String = "P2PVpn"
        
        // Исправление ошибок p1, p2: явно указываем типы и имена
        override fun onStateChange(newState: Tunnel.State) {
            // Здесь можно добавить логику уведомления пользователя
        }
    }

    fun buildConfig(serverHost: String, serverPort: Int, serverPubKey: String, privateKey: String): Config {
        // Создаем интерфейс (клиентская часть)
        val iFace = Interface.Builder()
            .addAddress(InetNetwork.parse("10.0.0.2/32"))
            .setPrivateKey(Key.fromBase64(privateKey))
            .build()

        // Создаем пир (серверная часть)
        val peer = Peer.Builder()
            .setPublicKey(Key.fromBase64(serverPubKey))
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
            .setEndpoint(InetEndpoint.parse("$serverHost:$serverPort"))
            .build()

        return Config.Builder()
            .setInterface(iFace)
            .addPeer(peer)
            .build()
    }

    fun up(config: Config) {
        try {
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

