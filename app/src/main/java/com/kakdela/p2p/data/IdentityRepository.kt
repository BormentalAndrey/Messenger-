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
import java.util.UUID
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

    // ---------- СЕТЕВОЙ ЦИКЛ ----------

    fun startNetwork() {
        if (!listening) {
            CryptoManager.init(context)
            startListening()
            registerInWifi()
            discoverInWifi()
            Log.d(TAG, "P2P network subsystem initialized")
        }
    }

    fun stopNetwork() {
        listening = false
        socket?.close()
        try {
            nsdManager.unregisterService(registrationListener)
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
        job.cancel()
    }

    fun addListener(l: (String, String, String, String) -> Unit) = listeners.add(l)
    fun removeListener(l: (String, String, String, String) -> Unit) = listeners.remove(l)

    fun getMyId(): String = prefs.getString("my_security_hash", "") ?: ""

    // ---------- ПЕРЕДАЧА ДАННЫХ ----------

    fun sendSignaling(targetIp: String, type: String, data: String) {
        scope.launch {
            val json = JSONObject().apply {
                put("type", type)
                put("data", data)
            }
            sendUdp(targetIp, "WEBRTC_SIGNAL", json.toString())
        }
    }

    fun announceMyself(wrapper: UserRegistrationWrapper) {
        scope.launch {
            try {
                // Синхронизация с центральным шлюзом
                api.registerUser(wrapper)
                // Оповещение локальных узлов
                wifiPeers.values.forEach { ip ->
                    sendUdp(ip, "PRESENCE", "ONLINE")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Presence announce failed: ${e.message}")
            }
        }
    }

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
            Log.e(TAG, "DHT Sync failed: ${e.message}")
            nodeDao.getAllNodes().map {
                UserPayload(it.userHash, it.phone_hash, it.ip, it.port, it.publicKey, it.phone, it.email, it.lastSeen)
            }
        }
    }

    fun sendMessageSmart(targetHash: String, targetPhone: String?, message: String) = scope.launch {
        // Стратегия: Local -> Swarm Cache -> DHT/Server -> SMS
        val targetIp = wifiPeers[targetHash] ?: swarmPeers[targetHash] ?: findPeerOnServer(targetHash)?.ip
        
        if (targetIp != null && targetIp != "0.0.0.0") {
            if (sendUdp(targetIp, "CHAT", message)) return@launch
        }
        
        targetPhone?.let { sendAsSms(it, message) }
    }

    private suspend fun findPeerOnServer(hash: String): UserPayload? {
        val cached = nodeDao.getNodeByHash(hash)
        if (cached != null && (System.currentTimeMillis() - cached.lastSeen) < 120_000) {
            return UserPayload(cached.userHash, cached.phone_hash, cached.ip, cached.port, cached.publicKey, cached.phone, cached.email, cached.lastSeen)
        }
        return fetchAllNodesFromServer().find { it.hash == hash }
    }

    // ---------- UDP ПРОТОКОЛ ----------

    private fun startListening() = scope.launch {
        try {
            socket = DatagramSocket(PORT)
            listening = true
            val buffer = ByteArray(16384)
            while (listening && isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket?.receive(packet)
                val fromIp = packet.address.hostAddress ?: ""
                val rawData = String(packet.data, 0, packet.length)
                handleIncoming(rawData, fromIp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Socket listener died: ${e.message}")
            listening = false
        }
    }

    private fun handleIncoming(raw: String, fromIp: String) {
        scope.launch {
            try {
                val json = JSONObject(raw)
                val type = json.getString("type")
                val fromHash = json.optString("from")

                when (type) {
                    "QUERY_PEER" -> {
                        val target = json.getString("data")
                        nodeDao.getNodeByHash(target)?.let {
                            val resp = JSONObject().apply { put("hash", it.userHash); put("ip", it.ip) }
                            sendUdp(fromIp, "PEER_FOUND", resp.toString())
                        }
                    }
                    "PEER_FOUND" -> {
                        val data = JSONObject(json.getString("data"))
                        swarmPeers[data.getString("hash")] = data.getString("ip")
                    }
                    "CHAT", "WEBRTC_SIGNAL" -> {
                        val pubKey = json.getString("pubkey")
                        val sig = Base64.decode(json.getString("signature"), Base64.NO_WRAP)
                        val unsigned = JSONObject(raw).apply { remove("signature") }.toString().toByteArray()

                        if (CryptoManager.verify(sig, unsigned, pubKey)) {
                            CryptoManager.savePeerPublicKey(fromHash, pubKey)
                            nodeDao.updateNetworkInfo(fromHash, fromIp, PORT, pubKey, System.currentTimeMillis())
                            listeners.forEach { it(type, json.getString("data"), fromIp, fromHash) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invalid packet from $fromIp")
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
            val signature = CryptoManager.sign(json.toString().toByteArray())
            json.put("signature", Base64.encodeToString(signature, Base64.NO_WRAP))

            val bytes = json.toString().toByteArray()
            val address = InetAddress.getByName(ip)
            val packet = DatagramPacket(bytes, bytes.size, address, PORT)
            
            DatagramSocket().use { it.send(packet) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "UDP send failed to $ip: ${e.message}")
            false
        }
    }

    // ---------- NSD (ЛОКАЛЬНЫЙ ПОИСК) ----------

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) { Log.d(TAG, "NSD Service active: ${info.serviceName}") }
        override fun onRegistrationFailed(info: NsdServiceInfo, err: Int) { Log.e(TAG, "NSD Reg failed: $err") }
        override fun onServiceUnregistered(info: NsdServiceInfo) {}
        override fun onUnregistrationFailed(info: NsdServiceInfo, err: Int) {}
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(service: NsdServiceInfo) {
            if (service.serviceType == SERVICE_TYPE && !service.serviceName.contains(getMyId())) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        resolved.host?.hostAddress?.let { ip ->
                            // Извлекаем hash из имени (KakDela-HASH)
                            val peerHash = resolved.serviceName.substringAfter("KakDela-")
                            wifiPeers[peerHash] = ip
                        }
                    }
                    override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
                })
            }
        }
        override fun onServiceLost(s: NsdServiceInfo) { wifiPeers.values.remove(s.host?.hostAddress) }
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) {}
        override fun onStartDiscoveryFailed(t: String, e: Int) {}
        override fun onStopDiscoveryFailed(t: String, e: Int) {}
    }

    private fun registerInWifi() {
        val info = NsdServiceInfo().apply {
            // Формат имени: KakDela-HASH (чтобы сразу видеть, чей это узел)
            serviceName = "KakDela-${getMyId().take(8)}-${UUID.randomUUID().toString().take(4)}"
            serviceType = SERVICE_TYPE
            port = PORT
        }
        try { nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener) } catch (e: Exception) {}
    }

    private fun discoverInWifi() {
        try { nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) } catch (e: Exception) {}
    }

    // ---------- УТИЛИТЫ ----------

    private fun sendAsSms(phone: String, message: String) {
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phone, null, "[P2P] $message", null, null)
        } catch (e: Exception) {
            Log.e(TAG, "SMS Fallback failed")
        }
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
