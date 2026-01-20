package com.kakdela.p2p.data

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.api.WebViewApiClient
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class IdentityRepository(private val context: Context) {

    private val TAG = "IdentityRepository"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private val api = WebViewApiClient
    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    val messageRepository by lazy {
        MessageRepository(context, db.messageDao(), this)
    }

    private val peerSyncRepository by lazy {
        PeerSyncRepository(this, db)
    }

    private val listeners = CopyOnWriteArrayList<(String, String, String, String) -> Unit>()

    val wifiPeers = ConcurrentHashMap<String, String>()
    val swarmPeers = ConcurrentHashMap<String, String>()

    private companion object {
        const val SERVICE_TYPE = "_kakdela_p2p._udp."
        const val PORT = 8888
        const val SYNC_INTERVAL = 300_000L
        const val PEPPER = "7fb8a1d2c3e4f5a6b7c8d9e0f1a2b3c4"
    }

    @Volatile
    private var isRunning = false

    private var udpSocket: DatagramSocket? = null
    private var networkScope: CoroutineScope? = null

    /* ============================================================
       LIFECYCLE
       ============================================================ */

    fun startNetwork() {
        if (isRunning) return
        isRunning = true

        networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        networkScope?.launch {
            try {
                CryptoManager.init(context)
                val myId = getMyId()

                Log.i(TAG, "Network started for ${myId.take(8)}")

                launch { startUdpListener() }
                launch { registerInWifi() }
                launch { discoverInWifi() }
                launch { pingKnownNodes() }

                peerSyncRepository.start()

                if (myId.isNotEmpty()) {
                    performServerSync(myId)
                    startSyncLoop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "startNetwork fatal error", e)
                stopNetwork()
            }
        }
    }

    fun stopNetwork() {
        isRunning = false

        peerSyncRepository.stop()
        networkScope?.cancel()
        networkScope = null

        udpSocket?.close()
        udpSocket = null

        try {
            nsdManager.unregisterService(registrationListener)
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (_: Exception) {}

        wifiPeers.clear()
        swarmPeers.clear()
    }

    /* ============================================================
       UDP
       ============================================================ */

    private fun startUdpListener() = networkScope?.launch {
        try {
            udpSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
            }

            val buffer = ByteArray(65507)

            while (isRunning) {
                val packet = DatagramPacket(buffer, buffer.size)
                udpSocket?.receive(packet)

                val fromIp = packet.address?.hostAddress ?: continue
                val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)

                processIncomingPacket(raw, fromIp)
            }
        } catch (e: Exception) {
            if (isRunning) Log.e(TAG, "UDP error", e)
        }
    }

    private fun processIncomingPacket(raw: String, fromIp: String) {
        networkScope?.launch(Dispatchers.Default) {
            try {
                val json = JSONObject(raw)

                val type = json.getString("type")
                val fromHash = json.getString("from")
                val pubKey = json.getString("pubkey")
                val timestamp = json.getLong("timestamp")
                val data = json.getString("data")

                val signature =
                    Base64.decode(json.getString("signature"), Base64.NO_WRAP)

                if (!CryptoManager.verify(
                        signature,
                        (data + timestamp).toByteArray(),
                        pubKey
                    )
                ) return@launch

                withContext(Dispatchers.IO) {
                    nodeDao.updateNetworkInfo(
                        fromHash,
                        fromIp,
                        PORT,
                        pubKey,
                        System.currentTimeMillis()
                    )
                }

                swarmPeers[fromHash] = fromIp

                when {
                    type == "PING" ->
                        sendUdp(fromIp, "PONG", "alive")

                    type == "PEER_SYNC" ->
                        peerSyncRepository.handleIncoming(data, fromHash)

                    type.startsWith("CHAT") ->
                        messageRepository.handleIncoming(type, data, fromHash)

                    else ->
                        listeners.forEach { it(type, data, fromIp, fromHash) }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Malformed packet", e)
            }
        }
    }

    suspend fun sendUdp(ip: String, type: String, data: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val signature =
                    CryptoManager.sign((data + timestamp).toByteArray())

                val json = JSONObject().apply {
                    put("type", type)
                    put("data", data)
                    put("from", getMyId())
                    put("pubkey", CryptoManager.getMyPublicKeyStr())
                    put("timestamp", timestamp)
                    put(
                        "signature",
                        Base64.encodeToString(signature, Base64.NO_WRAP)
                    )
                }

                val bytes = json.toString().toByteArray(Charsets.UTF_8)
                val addr = InetAddress.getByName(ip)

                DatagramSocket().use {
                    it.send(DatagramPacket(bytes, bytes.size, addr, PORT))
                }

                true
            } catch (_: Exception) {
                false
            }
        }

    /* ============================================================
       MESSAGE SENDING  ✅ FIXED
       ============================================================ */

    suspend fun sendMessageSmart(
        targetHash: String,
        phone: String?,
        message: String
    ): Boolean = withContext(Dispatchers.IO) {

        val ip =
            wifiPeers[targetHash]
                ?: swarmPeers[targetHash]
                ?: getCachedNode(targetHash)?.ip

        var delivered = false

        if (!ip.isNullOrBlank() && ip != "0.0.0.0") {
            delivered = sendUdp(ip, "CHAT_MSG", message)
        }

        if (!delivered && !phone.isNullOrBlank()) {
            sendAsSms(phone, message)
            delivered = true
        }

        return@withContext delivered
    }

    private fun sendAsSms(phone: String, message: String) {
        try {
            val sms =
                if (android.os.Build.VERSION.SDK_INT >= 31)
                    context.getSystemService(SmsManager::class.java)
                else
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()

            sms?.sendTextMessage(phone, null, "[P2P] $message", null, null)
        } catch (_: Exception) {}
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    fun getMyId(): String {
        var id = prefs.getString("my_security_hash", "") ?: ""

        if (id.isEmpty()) {
            id = CryptoManager.getMyIdentityHash()
            if (id.isNotEmpty()) {
                prefs.edit().putString("my_security_hash", id).apply()
            }
        }
        return id
    }

    suspend fun getCachedNode(hash: String): NodeEntity? =
        withContext(Dispatchers.IO) {
            nodeDao.getNodeByHash(hash)
        }

    fun generatePhoneDiscoveryHash(phone: String): String {
        val digits = phone.replace(Regex("[^0-9]"), "")
        val normalized = when {
            digits.length == 10 && digits.startsWith("9") -> "7$digits"
            digits.length == 11 && digits.startsWith("8") ->
                "7${digits.substring(1)}"
            else -> digits
        }

        return MessageDigest.getInstance("SHA-256")
            .digest((normalized + PEPPER).toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun getLocalIpAddress(): String? = try {
        java.net.NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
            ?.hostAddress
    } catch (_: Exception) {
        null
    }

    fun addListener(l: (String, String, String, String) -> Unit) =
        listeners.add(l)

    fun removeListener(l: (String, String, String, String) -> Unit) =
        listeners.remove(l)

    /* ============================================================
       NSD
       ============================================================ */

    private val registrationListener =
        object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) {}
            override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) {}
            override fun onServiceUnregistered(s: NsdServiceInfo) {}
            override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) {}
        }

    private val discoveryListener =
        object : NsdManager.DiscoveryListener {
            override fun onServiceFound(s: NsdServiceInfo) {
                if (s.serviceType != SERVICE_TYPE) return

                nsdManager.resolveService(
                    s,
                    object : NsdManager.ResolveListener {
                        override fun onServiceResolved(r: NsdServiceInfo) {
                            val host = r.host?.hostAddress ?: return
                            val peerHash =
                                r.serviceName.removePrefix("KakDela-")

                            if (peerHash != getMyId()) {
                                wifiPeers[peerHash] = host
                                networkScope?.launch {
                                    sendUdp(host, "PING", "discovery")
                                }
                            }
                        }

                        override fun onResolveFailed(
                            s: NsdServiceInfo,
                            e: Int
                        ) {}
                    }
                )
            }

            override fun onServiceLost(s: NsdServiceInfo) {
                wifiPeers.remove(s.serviceName.removePrefix("KakDela-"))
            }

            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        }

    private fun registerInWifi() {
        val id = getMyId()
        if (id.isEmpty()) return

        val info = NsdServiceInfo().apply {
            serviceName = "KakDela-$id"
            serviceType = SERVICE_TYPE
            port = PORT
        }

        try {
            nsdManager.registerService(
                info,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )
        } catch (_: Exception) {}
    }

    private fun discoverInWifi() {
        try {
            nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (_: Exception) {}
    }

    private fun startSyncLoop() = networkScope?.launch {
        while (isRunning) {
            performServerSync(getMyId())
            delay(SYNC_INTERVAL)
        }
    }
}
