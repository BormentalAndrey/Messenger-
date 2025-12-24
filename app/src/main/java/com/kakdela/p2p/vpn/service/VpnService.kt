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
import com.wireguard.config.*
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
        // Для Android 14+ указываем тип сервиса
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        
        startForeground(1, createNotification("Подключение..."), type)

        if (intent?.action == ACTION_DISCONNECT) {
            disconnect()
            return START_NOT_STICKY
        }

        // Если уже работает, не перезапускаем
        if (tunnel != null) return START_STICKY

        connect()
        return START_STICKY
    }

    private fun connect() {
        scope.launch {
            try {
                // 1. Получаем свои ключи
                val keyStore = WgKeyStore(this@VpnService)
                val privateKey = keyStore.getPrivateKey()
                val publicKey = keyStore.getPublicKey()

                // 2. Регистрируемся в Cloudflare для получения IP и endpoint
                val registrar = WarpRegistrar()
                val response = registrar.register(publicKey)

                if (response?.config == null) {
                    updateNotification("Ошибка: Не удалось получить конфиг")
                    stopSelf()
                    return@launch
                }

                // 3. Достаем данные из ответа
                // V4 адрес клиента
                val clientIp = response.config.interfaceData?.addresses?.v4 ?: throw Exception("Нет IP")
                // Адрес сервера и его ключ
                val peerData = response.config.peers?.firstOrNull() ?: throw Exception("Нет пиров")
                val serverEndpoint = peerData.endpoint?.host ?: "engage.cloudflareclient.com:2408"
                val serverPubKey = peerData.public_key ?: ""

                // 4. Собираем конфиг WireGuard
                val config = Config.Builder()
                    .setInterface(Interface.Builder()
                        .parsePrivateKey(privateKey)
                        .addAddress(InetNetwork.parse(clientIp))
                        .addDnsServer(InetAddress.getByName("8.8.8.8")) // Google DNS для сайтов
                        .addDnsServer(InetAddress.getByName("1.1.1.1")) // Cloudflare DNS
                        .setMtu(1280) // Важно для мобильных сетей
                        .build())
                    .addPeer(Peer.Builder()
                        .parsePublicKey(serverPubKey)
                        .setEndpoint(InetEndpoint.parse(serverEndpoint))
                        .addAllowedIp(InetNetwork.parse("0.0.0.0/0")) // Весь трафик через VPN
                        .setPersistentKeepalive(25)
                        .build())
                    .build()

                // 5. Запускаем туннель
                tunnel = object : Tunnel { 
                    override fun getName() = "KakdelaVPN"
                    override fun onStateChange(state: Tunnel.State) {} 
                }
                
                backend.setState(tunnel!!, Tunnel.State.UP, config)
                updateNotification("VPN Активен • Интернет доступен")

            } catch (e: Exception) {
                e.printStackTrace()
                updateNotification("Ошибка VPN: ${e.message}")
                stopSelf()
            }
        }
    }

    private fun disconnect() {
        scope.launch {
            try { tunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) } } catch (_: Exception) {}
            stopSelf()
            tunnel = null
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(NotificationManager::class.java)
            val chan = NotificationChannel(CHANNEL_ID, "VPN Status", NotificationManager.IMPORTANCE_LOW)
            mgr.createNotificationChannel(chan)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Kakdela P2P")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(1, createNotification(text))
    }
    
    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }
}

