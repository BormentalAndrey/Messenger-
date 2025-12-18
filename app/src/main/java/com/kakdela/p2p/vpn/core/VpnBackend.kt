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
    // Используем WgQuickBackend для работы через стандартные конфиги WireGuard
    private val backend by lazy { WgQuickBackend(context) }
    
    // Реализация интерфейса Tunnel с корректными именами параметров для Kotlin
    private val tunnel = object : Tunnel {
        override fun getName(): String = "P2PVpn"
        
        // Именно здесь возникала ошибка p1/p2. Теперь параметры указаны верно.
        override fun onStateChange(newState: Tunnel.State) {
            // Можно добавить логирование состояния для отладки
        }
    }

    /**
     * Создание конфигурации туннеля.
     * Все ключи должны быть в формате Base64 String.
     */
    fun buildConfig(serverHost: String, serverPort: Int, serverPubKey: String, privateKey: String): Config {
        // Создаем интерфейс клиента (наш телефон)
        val interfaceBuilder = Interface.Builder()
            .addAddress(InetNetwork.parse("10.0.0.2/32"))
            // Метод setPrivateKey требует объект Key, а не String!
            .setPrivateKey(Key.fromBase64(privateKey)) 
            .build()

        // Настройка пира (удаленного сервера)
        val peerBuilder = Peer.Builder()
            .setPublicKey(Key.fromBase64(serverPubKey))
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0")) // Весь трафик через VPN
            .setEndpoint(InetEndpoint.parse("$serverHost:$serverPort"))
            .build()

        return Config.Builder()
            .setInterface(interfaceBuilder)
            .addPeer(peerBuilder)
            .build()
    }

    fun up(config: Config) {
        try {
            // Включаем VPN туннель
            backend.setState(tunnel, Tunnel.State.UP, config)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun down() {
        try {
            // Выключаем VPN туннель (config передаем null)
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

