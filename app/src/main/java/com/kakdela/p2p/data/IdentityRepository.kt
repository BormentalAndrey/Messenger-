package com.kakdela.p2p.data

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import com.kakdela.p2p.api.MyServerApiFactory
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.api.UserRegistrationWrapper
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class IdentityRepository(private val context: Context) {

    private val TAG = "IdentityRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private val api = MyServerApiFactory.instance
    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val listeners = CopyOnWriteArrayList<(String, String, String, String) -> Unit>()
    private val wifiPeers = ConcurrentHashMap<String, String>()
    private val swarmPeers = ConcurrentHashMap<String, String>()

    private val SERVICE_TYPE = "_kakdela_p2p._udp"
    private val PORT = 8888
    private val PEPPER = "7fb8a1d2c3e4f5a6"

    @Volatile
    private var isRunning = false

    /* ======================= PUBLIC API ======================= */

    fun startNetwork() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                CryptoManager.init(context)
                startUdpListener()
                registerInWifi()
                discoverInWifi()
                Log.i(TAG, "P2P network stack started")
            } catch (e: Exception) {
                Log.e(TAG, "Network start failed", e)
                isRunning = false
            }
        }
    }

    fun stopNetwork() {
        isRunning = false
        try {
            nsdManager.unregisterService(registrationListener)
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (_: Exception) {}
        scope.coroutineContext.cancelChildren()
    }

    fun addListener(l: (String, String, String, String) -> Unit) = listeners.add(l)
    fun removeListener(l: (String, String, String, String) -> Unit) = listeners.remove(l)

    fun getMyId(): String = prefs.getString("my_security_hash", "") ?: ""

    /* ======================= SERVER & SYNC ======================= */

    suspend fun fetchAllNodesFromServer(): List<UserPayload> = withContext(Dispatchers.IO) {
        try {
            val users = api.getAllNodes().users.orEmpty()
            nodeDao.updateCache(users.map {
                NodeEntity(
                    userHash = it.hash,
                    phone_hash = it.phone_hash ?: "",
                    ip = it.ip ?: "0.0.0.0",
                    port = it.port,
                    publicKey = it.publicKey,
                    phone = it.phone ?: "",
                    lastSeen = it.lastSeen ?: System.currentTimeMillis()
                )
            })
            users
        } catch (_: Exception) {
            nodeDao.getAllNodes().map {
                UserPayload(
                    it.userHash,
                    it.phone_hash,
                    it.ip,
                    it.port,
                    it.publicKey,
                    it.phone,
                    null,
                    it.lastSeen
                )
            }
        }
    }

    fun announceMyself(wrapper: UserRegistrationWrapper) {
        scope.launch {
            try {
                // Сериализация wrapper в JSON
                val payloadJson = JSONObject().apply {
                    put("hash", wrapper.data.hash)
                    put("phone", wrapper.data.phone)
                    put("phone_hash", wrapper.data.phone_hash)
                    put("publicKey", wrapper.data.publicKey)
                    put("ip", wrapper.data.ip)
                    put("port", wrapper.data.port)
                    put("lastSeen", wrapper.data.lastSeen)
                    put("email", wrapper.data.email)
                }

                val wrapperJson = JSONObject().apply {
                    put("payload", payloadJson)
                    put("signature", Base64.encodeToString(
                        CryptoManager.sign(payloadJson.toString().toByteArray()), Base64.NO_WRAP
                    ))
                }

                // Отправка на сервер
                api.announceSelf(UserRegistrationWrapper(wrapper.data.hash, wrapper.data))

                // Уведомление Wi-Fi пиров
                wifiPeers.values.forEach { sendUdp(it, "PRESENCE", "ONLINE") }

            } catch (e: Exception) {
                Log.e(TAG, "Announce failed", e)
            }
        }
    }

    /* ======================= ROUTING & WEBRTC ======================= */

    fun sendSignaling(targetIp: String, type: String, data: String) {
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("subtype", type)
                    put("payload", data)
                }
                sendUdp(targetIp, "WEBRTC_SIGNAL", json.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Signaling error", e)
            }
        }
    }

    fun sendMessageSmart(targetHash: String, phone: String?, message: String) =
        scope.launch {
            val ip = wifiPeers[targetHash]
                ?: swarmPeers[targetHash]
                ?: findPeerOnServer(targetHash)?.ip

            val delivered = !ip.isNullOrBlank() && ip != "0.0.0.0" && sendUdp(ip, "CHAT", message)

            if (!delivered && !phone.isNullOrBlank()) {
                sendAsSms(phone, message)
            }
        }

    private suspend fun findPeerOnServer(hash: String): UserPayload? {
        val cached = nodeDao.getNodeByHash(hash)
        if (cached != null && System.currentTimeMillis() - cached.lastSeen < 180_000) {
            return UserPayload(
                cached.userHash,
                cached.phone_hash,
                cached.ip,
                cached.port,
                cached.publicKey,
                cached.phone,
                null,
                cached.lastSeen
            )
        }
        return fetchAllNodesFromServer().find { it.hash == hash }
    }

    /* ======================= UDP CORE ======================= */

    private fun startUdpListener() {
        scope.launch {
            try {
                DatagramSocket(PORT).use { socket ->
                    socket.reuseAddress = true
                    val buffer = ByteArray(16_384)
                    while (isRunning && !socket.isClosed) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val ip = packet.address.hostAddress ?: continue
                        val data = String(packet.data, 0, packet.length)
                        launch(Dispatchers.Default) { handleIncoming(data, ip) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP listener failed", e)
                isRunning = false
            }
        }
    }

    private suspend fun handleIncoming(raw: String, fromIp: String) {
        try {
            val json = JSONObject(raw)
            val type = json.getString("type")
            val fromHash = json.getString("from")
            val pubKey = json.getString("pubkey")
            val sig = Base64.decode(json.getString("signature"), Base64.NO_WRAP)
            val unsigned = JSONObject(raw).apply { remove("signature") }.toString()

            if (!CryptoManager.verify(sig, unsigned.toByteArray(), pubKey)) return

            CryptoManager.savePeerPublicKey(fromHash, pubKey)
            nodeDao.updateNetworkInfo(fromHash, fromIp, PORT, pubKey, System.currentTimeMillis())

            listeners.forEach { it(type, json.getString("data"), fromIp, fromHash) }
        } catch (_: Exception) {}
    }

    private suspend fun sendUdp(ip: String, type: String, data: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("type", type)
                put("data", data)
                put("from", getMyId())
                put("pubkey", CryptoManager.getMyPublicKeyStr())
                put("timestamp", System.currentTimeMillis())
            }

            val sig = CryptoManager.sign(json.toString().toByteArray())
            json.put("signature", Base64.encodeToString(sig, Base64.NO_WRAP))

            val addr = InetAddress.getByName(ip)
            DatagramSocket().use { it.send(DatagramPacket(json.toString().toByteArray(), json.toString().toByteArray().size, addr, PORT)) }
            true
        } catch (_: Exception) { false }
    }

    /* ======================= NSD ======================= */

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(s: NsdServiceInfo) {}
        override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) {}
        override fun onServiceUnregistered(s: NsdServiceInfo) {}
        override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) {}
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(s: NsdServiceInfo) {
            if (s.serviceType != SERVICE_TYPE) return
            if (s.serviceName.contains(getMyId().take(6))) return
            nsdManager.resolveService(s, object : NsdManager.ResolveListener {
                override fun onServiceResolved(r: NsdServiceInfo) {
                    val host = r.host?.hostAddress ?: return
                    val parts = r.serviceName.split("-")
                    if (parts.size >= 2) wifiPeers[parts[1]] = host
                }
                override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
            })
        }
        override fun onServiceLost(s: NsdServiceInfo) { wifiPeers.entries.removeIf { it.value == s.host?.hostAddress } }
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) {}
        override fun onStartDiscoveryFailed(t: String, e: Int) {}
        override fun onStopDiscoveryFailed(t: String, e: Int) {}
    }

    private fun registerInWifi() {
        try {
            nsdManager.registerService(NsdServiceInfo().apply {
                serviceName = "KakDela-${getMyId().take(8)}-${UUID.randomUUID().toString().take(4)}"
                serviceType = SERVICE_TYPE
                port = PORT
            }, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (_: Exception) {}
    }

    private fun discoverInWifi() {
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (_: Exception) {}
    }

    /* ======================= UTILS ======================= */

    private fun sendAsSms(phone: String, message: String) {
        try {
            val sms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                context.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.sendTextMessage(phone, null, "[P2P] $message", null, null)
        } catch (_: Exception) {}
    }

    fun generatePhoneDiscoveryHash(phone: String): String {
        val digits = phone.replace(Regex("[^0-9]"), "")
        val normalized = when {
            digits.length == 11 && digits.startsWith("8") -> "7${digits.substring(1)}"
            digits.length == 10 && digits.startsWith("9") -> "7$digits"
            else -> digits
        }
        return sha256(normalized + PEPPER)
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
