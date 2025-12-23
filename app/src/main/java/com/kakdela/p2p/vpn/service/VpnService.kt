package com.kakdela.p2p.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kakdela.p2p.MainActivity
import com.kakdela.p2p.R
import com.kakdela.p2p.vpn.core.WgKeyStore
import com.kakdela.p2p.vpn.data.ServerRepository
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
    
    // Дескриптор интерфейса (нужен, чтобы система знала, что VPN жив)
    private var pfd: ParcelFileDescriptor? = null

    companion object {
        const val ACTION_CONNECT = "com.kakdela.p2p.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.kakdela.p2p.vpn.DISCONNECT"
        const val CHANNEL_ID = "vpn_channel_id"
    }

    override fun onCreate() {
        super.onCreate()
        backend = GoBackend(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Запуск Foreground Service (обязательно для VPN)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, createNotification())
        }

        when (intent?.action) {
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> disconnect()
        }
        return START_STICKY
    }

    private fun connect() {
        scope.launch {
            try {
                Log.d("VpnService", "Подготовка подключения...")
                
                // 1. Загружаем сервер и ключи
                val servers = ServerRepository(this@VpnService).load()
                if (servers.isEmpty()) {
                    Log.e("VpnService", "Нет серверов в json!")
                    stopSelf()
                    return@launch
                }
                
                // Берем первый сервер (или реализуйте логику выбора)
                val server = servers[0] 
                val keyStore = WgKeyStore(this@VpnService)
                val privateKey = keyStore.getPrivateKey()

                Log.d("VpnService", "Подключение к: ${server.host}")

                // 2. Создаем конфигурацию WireGuard
                val config = buildConfig(privateKey, server.publicKey, server.host, server.port)

                // 3. Создаем туннель через GoBackend
                // ВНИМАНИЕ: GoBackend сам управляет tun интерфейсом, нам не нужно вызывать builder.establish() вручную,
                // НО GoBackend требует реализации интерфейса Tunnel.
                
                val newTunnel = object : Tunnel {
                    override fun getName() = "KakdelaVPN"
                    override fun onStateChange(state: Tunnel.State) {
                        Log.d("VpnService", "State change: $state")
                    }
                }
                tunnel = newTunnel

                // Поднимаем туннель
                backend.setState(newTunnel, Tunnel.State.UP, config)
                Log.d("VpnService", "Туннель поднят!")

            } catch (e: Exception) {
                Log.e("VpnService", "Ошибка подключения", e)
                stopSelf()
            }
        }
    }

    private fun buildConfig(privateKey: String, serverPublicKey: String, host: String, port: Int): Config {
        // Конфигурация интерфейса (Ваш телефон)
        val iface = Interface.Builder()
            .parsePrivateKey(privateKey)
            // ВАЖНО: Большинство бесплатных конфигов требуют динамический IP, 
            // но WireGuard нужен статический. Мы пробуем 10.2.0.2. 
            // Если сервер ждет другой IP, трафик не пойдет.
            .addAddress(InetNetwork.parse("10.2.0.2/32")) 
            .addDnsServer(InetAddress.getByName("8.8.8.8")) // DNS Google
            .addDnsServer(InetAddress.getByName("1.1.1.1")) // DNS Cloudflare
            .build()

        // Конфигурация Пира (Сервер)
        val peer = Peer.Builder()
            .parsePublicKey(serverPublicKey)
            .setEndpoint(InetEndpoint.parse("$host:$port"))
            // ЭТА СТРОКА ЗАВОРАЧИВАЕТ ВЕСЬ ТРАФИК В VPN
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0")) 
            .addAllowedIp(InetNetwork.parse("::/0"))
            .setPersistentKeepalive(25)
            .build()

        return Config.Builder()
            .setInterface(iface)
            .addPeer(peer)
            .build()
    }

    private fun disconnect() {
        scope.launch {
            try {
                tunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // --- Уведомления ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VPN Активен")
            .setContentText("Весь трафик защищен")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
        scope.cancel()
    }
}

