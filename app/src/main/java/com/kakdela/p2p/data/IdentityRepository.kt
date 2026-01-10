package com.kakdela.p2p.data

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
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
import java.util.concurrent.CopyOnWriteArrayList

class IdentityRepository(private val context: Context) {

    private val TAG = "IdentityRepository"
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private val listeners = CopyOnWriteArrayList<(String, String, String, String) -> Unit>()
    private val wifiPeers = mutableMapOf<String, String>()
    private val swarmPeers = mutableMapOf<String, String>()

    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()
    private val api = MyServerApiFactory.instance

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val SERVICE_TYPE = "_kakdela_p2p._udp"
    private val PORT = 8888
    private val PEPPER = "7fb8a1d2c3e4f5a6"

    private var socket: DatagramSocket? = null
    private var listening = false

    // ---------- PUBLIC API ----------

    fun startNetwork() {
        if (!listening) {
            CryptoManager.init(context)
            startListening()
            registerInWifi()
            discoverInWifi()
            Log.d(TAG, "P2P network started")
        }
    }

    fun stopNetwork() {
        listening = false
        socket?.close()
        try { nsdManager.unregisterService(registrationListener) } catch (e: Exception) {}
        try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (e: Exception) {}
        job.cancel()
    }

    fun addListener(l: (String, String, String, String) -> Unit) = listeners.add(l)
    fun removeListener(l: (String, String, String, String) -> Unit) = listeners.remove(l)

    /**
     * Получает текущий Security Hash пользователя.
     * Используется во всем приложении для идентификации.
     */
    fun getMyId(): String {
        return prefs.getString("my_security_hash", "") ?: ""
    }

    /**
     * Метод для WebRTC и FileTransfer.
     * Обертывает данные в JSON с типом и отправляет через sendUdp.
     */
    fun sendSignaling(targetIp: String, type: String, data: String) {
        scope.launch {
            val json = JSONObject().apply {
                put("type", type)
                put("data", data)
            }
            // Отправляем как WEBRTC_SIGNAL, внутри которого лежит подтип (OFFER, ICE, FILE_CHUNK)
            sendUdp(targetIp, "WEBRTC_SIGNAL", json.toString())
        }
    }

    /**
     * Анонсирует присутствие узла в сети (Server + UDP Broadcast)
     */
    fun announceMyself(wrapper: UserRegistrationWrapper) {
        scope.launch {
            try {
                // 1. Отправка на сервер
                api.registerUser(wrapper)
                // 2. Локальный бродкаст (если нужно)
                wifiPeers.values.forEach { ip ->
                    sendUdp(ip, "PRESENCE", "ONLINE")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Announce failed: ${e.message}")
            }
        }
    }

    // ---------- SYNC LOGIC ----------

    suspend fun fetchAllNodesFromServer(): List<UserPayload> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAllNodes()
            val users = response.users ?: emptyList()

            if (users.isNotEmpty()) {
                val entities = users.map { 
                    NodeEntity(
                        userHash = it.hash,
                        phone_hash = it.phone_hash ?: "",
                        ip = it.ip ?: "0.0.0.0",
                        port = it.port,
                        publicKey = it.publicKey,
                        phone = it.phone,
                        lastSeen = it.lastSeen ?: System.currentTimeMillis()
                    )
                }
                nodeDao.updateCache(entities)
            }
            users
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync nodes from server: ${e.message}")
            nodeDao.getAllNodes().map {
                UserPayload(it.userHash, it.phone_hash, it.ip, it.port, it.publicKey, it.phone, it.email, it.lastSeen)
            }
        }
    }

    // ---------- HASHING ----------

    fun generatePhoneDiscoveryHash(phone: String): String {
        val normalized = normalizePhone(phone)
        return sha256(normalized + PEPPER)
    }

    private fun normalizePhone(raw: String): String {
        val d = raw.replace(Regex("[^0-9]"), "")
        return when {
            d.length == 11 && d.startsWith("8") -> "7${d.substring(1)}"
            d.length == 10 && d.startsWith("9") -> "7$d"
            else -> d
        }
    }

    // ---------- SENDING ----------

    fun sendMessageSmart(targetHash: String, targetPhone: String?, message: String) = scope.launch {
        // 1. WiFi (Local)
        wifiPeers[targetHash]?.let {
            if (sendUdp(it, "CHAT", message)) return@launch
        }
        // 2. Swarm (Memory Cache)
        swarmPeers[targetHash]?.let {
             if (sendUdp(it, "CHAT", message)) return@launch
        }
        // 3. DHT / Server Lookup
        val serverPeer = findPeerOnServer(targetHash)
        serverPeer?.ip?.let {
            if (it != "0.0.0.0" && sendUdp(it, "CHAT", message)) return@launch
        }
        
        // 4. Fallback SMS (если критично)
        targetPhone?.let { sendAsSms(it, message) }
    }

    private suspend fun findPeerOnServer(hash: String): UserPayload? {
        val cached = nodeDao.getNodeByHash(hash)
        if (cached != null && (System.currentTimeMillis() - cached.lastSeen) < 60000) {
            return UserPayload(cached.userHash, cached.phone_hash, cached.ip, cached.port, cached.publicKey, cached.phone, cached.email, cached.lastSeen)
        }
        fetchAllNodesFromServer()
        return nodeDao.getNodeByHash(hash)?.let {
            UserPayload(it.userHash, it.phone_hash, it.ip, it.port, it.publicKey, it.phone, it.email, it.lastSeen)
        }
    }

    // ---------- UDP LISTENER ----------

    private fun startListening() = scope.launch {
        try {
            socket = DatagramSocket(PORT)
            listening = true
            val buffer = ByteArray(16_384)
            while (listening && isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket?.receive(packet)
                handleIncoming(String(packet.data, 0, packet.length), packet.address.hostAddress ?: "")
            }
        } catch (e: Exception) {
            listening = false
            Log.e(TAG, "Socket error: ${e.message}")
        }
    }

    private fun handleIncoming(raw: String, fromIp: String) {
        scope.launch {
            try {
                val json = JSONObject(raw)
                val type = json.getString("type")

                // Обработка простых запросов без подписи
                if (type == "QUERY_PEER") {
                    val target = json.getString("data")
                    nodeDao.getNodeByHash(target)?.let {
                        val resp = JSONObject().apply {
                            put("hash", it.userHash)
                            put("ip", it.ip)
                        }
                        sendUdp(fromIp, "PEER_FOUND", resp.toString())
                    }
                    return@launch
                }

                // Проверка подписи для CHAT и WEBRTC
                val from = json.optString("from")
                val pubKey = json.optString("pubkey")
                val sigStr = json.optString("signature")
                
                if (from.isNotEmpty() && pubKey.isNotEmpty() && sigStr.isNotEmpty()) {
                     val sig = Base64.decode(sigStr, Base64.NO_WRAP)
                     val unsigned = JSONObject(raw).apply { remove("signature") }.toString().toByteArray()
                     
                     if (CryptoManager.verify(sig, unsigned, pubKey)) {
                         CryptoManager.savePeerPublicKey(from, pubKey)
                         nodeDao.updateNetworkInfo(from, fromIp, PORT, pubKey, System.currentTimeMillis())
                         
                         // Передаем данные подписчикам (UI, WebRTCClient, etc.)
                         val data = json.getString("data")
                         listeners.forEach { it(type, data, fromIp, from) }
                     }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Packet parsing error: ${e.message}")
            }
        }
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

            val bytes = json.toString().toByteArray()
            DatagramSocket().use {
                it.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(ip), PORT))
            }
            true
        } catch (e: Exception) { false }
    }

    // ---------- WIFI NSD (Local Discovery) ----------

    // Важно: Сохраняем ссылки на листенеры, чтобы не было утечек и можно было отписаться
    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
            Log.d(TAG, "NSD Service Registered: ${NsdServiceInfo.serviceName}")
        }
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "NSD Registration failed: $errorCode")
        }
        override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(s: NsdServiceInfo) {
            if (s.serviceType == SERVICE_TYPE && s.serviceName != getMyId()) {
                nsdManager.resolveService(s, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(i: NsdServiceInfo) {
                        i.host?.hostAddress?.let { 
                            wifiPeers[i.serviceName] = it 
                            Log.d(TAG, "NSD Peer Found: ${i.serviceName} at $it")
                        }
                    }
                    override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
                })
            }
        }
        override fun onServiceLost(s: NsdServiceInfo) { wifiPeers.remove(s.serviceName) }
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) {}
        override fun onStartDiscoveryFailed(t: String, e: Int) {}
        override fun onStopDiscoveryFailed(t: String, e: Int) {}
    }

    private fun registerInWifi() {
        val info = NsdServiceInfo().apply {
            serviceName = "KakDela-${UUID.randomUUID().toString().take(8)}" // Уникальное имя во избежание конфликтов
            serviceType = SERVICE_TYPE
            port = PORT
        }
        try {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "NSD Register error: ${e.message}")
        }
    }

    private fun discoverInWifi() {
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "NSD Discovery error: ${e.message}")
        }
    }

    // ---------- UTILS ----------

    private fun sendAsSms(phone: String, message: String) {
        try {
            val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
            SmsManager.getSmsManagerForSubscriptionId(subId)
                .sendTextMessage(phone, null, "[P2P] $message", null, null)
        } catch (_: Exception) {}
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
