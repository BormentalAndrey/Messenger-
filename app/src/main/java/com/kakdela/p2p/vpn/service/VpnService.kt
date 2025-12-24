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

class MyVpnService : VpnService() {

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

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                notification("Подключение…"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_VPN
            )
        } else {
            startForeground(NOTIF_ID, notification("Подключение…"))
        }

        if (intent?.action == ACTION_DISCONNECT) {
            stopVpn()
            return START_NOT_STICKY
        }

        if (tunnel == null) connectVpn()

        return START_STICKY
    }

    private fun connectVpn() {
        scope.launch {
            try {
                val keyStore = WgKeyStore(this@MyVpnService)
                val registrar = WarpRegistrar()

                val resp = registrar.register(keyStore.getPublicKey())
                    ?: error("WARP error")

                val ip = resp.config!!.interfaceData!!.addresses!!.v4!!
                val peer = resp.config.peers!!.first()

                val config = Config.Builder()
                    .setInterface(
                        Interface.Builder()
                            .parsePrivateKey(keyStore.getPrivateKey())
                            .addAddress(InetNetwork.parse(ip))
                            .addDnsServer(InetAddress.getByName("1.1.1.1"))
                            .addDnsServer(InetAddress.getByName("8.8.8.8"))
                            .setMtu(1280)
                            .build()
                    )
                    .addPeer(
                        Peer.Builder()
                            .parsePublicKey(peer.public_key!!)
                            .setEndpoint(InetEndpoint.parse(peer.endpoint!!.host!!))
                            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
                            .addAllowedIp(InetNetwork.parse("::/0"))
                            .setPersistentKeepalive(25)
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
                update("Ошибка VPN")
                stopSelf()
            }
        }
    }

    private fun stopVpn() {
        scope.launch {
            try {
                tunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) }
            } catch (_: Exception) {}
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            tunnel = null
        }
    }

    private fun notification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Kakdela VPN")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun update(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, notification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "VPN",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
        }
    }
}
