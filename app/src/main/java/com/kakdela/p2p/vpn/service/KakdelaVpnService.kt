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
        val notification = notification("Подключение…")
        
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_VPN)
            }
            else -> startForeground(NOTIF_ID, notification)
        }

        if (intent?.action == ACTION_DISCONNECT) {
            stopVpn()
        } else {
            if (tunnel == null) connectVpn()
        }

        return START_STICKY
    }

    private fun connectVpn() {
        scope.launch {
            try {
                val keyStore = WgKeyStore(this@KakdelaVpnService)
                val registrar = WarpRegistrar()
                val resp = registrar.register(keyStore.getPublicKey()) ?: error("WARP error")
                val configData = resp.config ?: error("Config is null")
                
                val config = Config.Builder()
                    .setInterface(
                        Interface.Builder()
                            .parsePrivateKey(keyStore.getPrivateKey())
                            .addAddress(InetNetwork.parse(configData.interfaceData?.addresses?.v4!!))
                            .addDnsServer(InetAddress.getByName("1.1.1.1"))
                            .setMtu(1280)
                            .build()
                    )
                    .addPeer(
                        Peer.Builder()
                            .parsePublicKey(configData.peers?.first()?.public_key!!)
                            .setEndpoint(InetEndpoint.parse(configData.peers?.first()?.endpoint?.host!!))
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
            stopForeground(true)
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

