package com.kakdela.p2p.vpn.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.MainActivity
import com.kakdela.p2p.R
import com.kakdela.p2p.vpn.core.WarpRegistrar
import com.kakdela.p2p.vpn.core.WgKeyStore
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import kotlinx.coroutines.*
import java.net.InetAddress

class VpnService : VpnService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var backend: GoBackend
    private var tunnel: Tunnel? = null

    companion object {
        const val ACTION_CONNECT = "com.kakdela.p2p.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.kakdela.p2p.vpn.DISCONNECT"
        const val CHANNEL_ID = "vpn_channel"
    }

    override fun onCreate() {
        super.onCreate()
        backend = GoBackend(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        startForeground(1, createNotification("Подключение..."), type)

        when (intent?.action) {
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> disconnect()
        }
        return START_STICKY
    }

    private fun connect() {
        scope.launch {
            try {
                // 1. Генерируем ключи
                val keyStore = WgKeyStore(this@VpnService)
                val privateKey = keyStore.getPrivateKey()
                val publicKey = keyStore.getPublicKey()

                // 2. РЕГИСТРАЦИЯ (Это решит проблему "Server rejects connection")
                val registrar = WarpRegistrar(this@VpnService)
                // Пробуем получить конфиг (в реальном приложении лучше сохранять конфиг, чтобы не регистрироваться каждый раз)
                val account = registrar.register(publicKey)

                if (account == null) {
                    updateNotification("Ошибка регистрации WARP")
                    stopSelf()
                    return@launch
                }

                // 3. Создаем конфиг из полученных данных
                // Cloudflare обычно выдает V4 адрес в interface -> addresses -> v4
                val clientIp = account.config.interfaceData.addresses.v4 
                val serverEndpoint = account.config.peers[0].endpoint.host
                val serverPubKey = account.config.peers[0].publicKey

                val config = Config.Builder()
                    .setInterface(
                        Interface.Builder()
                            .parsePrivateKey(privateKey)
                            .addAddress(InetNetwork.parse(clientIp)) // Динамический IP от Cloudflare!
                            .addDnsServer(InetAddress.getByName("1.1.1.1"))
                            .build()
                    )
                    .addPeer(
                        Peer.Builder()
                            .parsePublicKey(serverPubKey)
                            .setEndpoint(InetEndpoint.parse(serverEndpoint))
                            .addAllowedIp(InetNetwork.parse("0.0.0.0/0")) // Весь трафик в VPN
                            .setPersistentKeepalive(25)
                            .build()
                    )
                    .build()

                // 4. Запуск
                tunnel = object : Tunnel {
                    override fun getName() = "KakdelaVPN"
                    override fun onStateChange(state: Tunnel.State) {}
                }
                
                backend.setState(tunnel!!, Tunnel.State.UP, config)
                updateNotification("VPN Активен • Защищено")

            } catch (e: Exception) {
                e.printStackTrace()
                updateNotification("Ошибка: ${e.message}")
                stopSelf()
            }
        }
    }

    private fun disconnect() {
        scope.launch {
            try {
                tunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) }
                stopSelf()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun createNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Kakdela VPN")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, createNotification(text))
    }
}

