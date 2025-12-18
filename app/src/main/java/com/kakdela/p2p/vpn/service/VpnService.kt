package com.kakdela.p2p.vpn.core

import android.content.Context
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.config.InetEndpoint
import com.wireguard.crypto.Key
import com.wireguard.config.InetNetwork

class VpnBackend(private val context: Context) {
    private val backend by lazy { WgQuickBackend(context) }
    
    // Исправлено: интерфейс Tunnel требует реализации getName и onStateChange
    private val tunnel = object : Tunnel {
        override fun getName(): String = "P2PVpn"
        
        // В новых версий SDK параметры называются newState
        override fun onStateChange(newState: Tunnel.State) {
            // Оставляем пустым или логируем
        }
    }

    fun buildConfig(serverHost: String, serverPort: Int, serverPubKey: String, privateKey: String): Config {
        // Интерфейс клиента
        val interfaceBuilder = Interface.Builder()
            .addAddress(InetNetwork.parse("10.0.0.2/32"))
            // Исправлено: используем Key.fromBase64
            .setPrivateKey(Key.fromBase64(privateKey))
            .build()

        // Настройка пира (сервера)
        val peerBuilder = Peer.Builder()
            .setPublicKey(Key.fromBase64(serverPubKey))
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
            .setEndpoint(InetEndpoint.parse("$serverHost:$serverPort"))
            .build()

        return Config.Builder()
            .setInterface(interfaceBuilder)
            .addPeer(peerBuilder)
            .build()
    }

    fun up(config: Config) {
        try {
            // backend.setState(Tunnel, State, Config)
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

