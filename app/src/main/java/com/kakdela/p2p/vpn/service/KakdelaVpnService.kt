package com.kakdela.p2p.vpn.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService as AndroidVpnService
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

class KakdelaVpnService : AndroidVpnService() {

    companion object {
        const val ACTION_CONNECT = "vpn_connect"
        const val ACTION_DISCONNECT = "vpn_disconnect"
        const val CHANNEL_ID = "vpn_channel"
        const val NOTIF_ID = 1001
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var backend: GoBackend
    private var tunnel: Tunnel? = null

    override fun onCreate() {
        super.onCreate()
        backend = GoBackend(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            stopVpn()
            return START_NOT_STICKY
        }

        val notification = notification("Подключение…")
        
        // Безопасный запуск Foreground Service в зависимости от версии Android
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 - 13
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_VPN)
            } else {
                // Ниже Android 10
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            // Фолбэк для предотвращения крэша при старте
            startForeground(NOTIF_ID, notification)
        }

        if (tunnel == null) connectVpn()
        return START_STICKY
    }

    private fun connectVpn() {
        scope.launch {
            try {
                val keyStore = WgKeyStore(this@KakdelaVpnService)
                val registrar = WarpRegistrar()
                val resp = registrar.register(keyStore.getPublicKey()) ?: error("WARP error")
                val configData = resp.config ?: error("Config is null")
                
                val v4Address = configData.interfaceData?.addresses?.v4 ?: "172.16.0.2/32"
                val peerPubKey = configData.peers?.firstOrNull()?.public_key ?: ""
                val endpoint = configData.peers?.firstOrNull()?.endpoint?.host ?: "engage.cloudflareclient.com:2408"

                val config = Config.Builder()
                    .setInterface(
                        Interface.Builder()
                            .parsePrivateKey(keyStore.getPrivateKey())
                            .addAddress(InetNetwork.parse(v4Address))
                            .addDnsServer(InetAddress.getByName("1.1.1.1"))
                            .setMtu(1280)
                            .build()
                    )
                    .addPeer(
                        Peer.Builder()
                            .parsePublicKey(peerPubKey)
                            .setEndpoint(InetEndpoint.parse(endpoint))
                            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
                            .build()
                    )
                    .build()

                tunnel = object : Tunnel {
                    override fun getName() = "KakdelaVPN"
                    override fun onStateChange(state: Tunnel.State) {}
                }

                backend.setState(tunnel!!, Tunnel.State.UP, config)
                update("VPN активен")
            } catch (e: Exception) {
                update("Ошибка подключения")
                stopSelf()
            }
        }
    }

    private fun stopVpn() {
        scope.launch {
            try { tunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) } } catch (e: Exception) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            stopSelf()
            tunnel = null
        }
    }

    private fun notification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Kakdela VPN")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun update(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_ID, notification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(NotificationChannel(CHANNEL_ID, "VPN Service", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

