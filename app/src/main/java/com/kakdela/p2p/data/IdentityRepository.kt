package com.kakdela.p2p.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Base64
import android.util.Log
import com.kakdela.p2p.api.MyServerApiFactory
import com.kakdela.p2p.api.UserPayload
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

    // ---------- NETWORK MANAGEMENT ----------

    fun startNetwork() {
        CryptoManager.init(context)
        startListening()
        registerInWifi()
        discoverInWifi()
        Log.d(TAG, "P2P network started")
    }

    fun stopNetwork() {
        listening = false
        socket?.close()
        job.cancel()
    }

    fun addListener(l: (String, String, String, String) -> Unit) = listeners.add(l)
    fun removeListener(l: (String, String, String, String) -> Unit) = listeners.remove(l)

    // ---------- SYNC LOGIC (Используется в ContactP2PManager) ----------

    suspend fun fetchAllNodesFromServer(): List<UserPayload> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAllNodes()
            val users = response.users ?: emptyList()

            if (users.isNotEmpty()) {
                // Маппинг UserPayload -> NodeEntity
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
            // В случае ошибки возвращаем локальный кэш (превращаем обратно в UserPayload для совместимости)
            nodeDao.getAllNodes().map {
                UserPayload(
                    hash = it.userHash,
                    phone_hash = it.phone_hash,
                    ip = it.ip,
                    port = it.port,
                    publicKey = it.publicKey,
                    phone = it.phone,
                    email = it.email,
                    lastSeen = it.lastSeen
                )
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
        // 3. DHT (Database / Swarm search)
        searchInSwarm(targetHash).await()?.let {
            if (sendUdp(it, "CHAT", message)) return@launch
        }
        // 4. Server (Last known IP)
        findPeerOnServer(targetHash)?.ip?.let {
            if (sendUdp(it, "CHAT", message)) return@launch
        }
        // 5. Fallback SMS
        targetPhone?.let { sendAsSms(it, message) }
    }

    // ---------- DISCOVERY ----------

    private fun searchInSwarm(targetHash: String): Deferred<String?> = scope.async {
        nodeDao.getAllNodes().shuffled().take(30).forEach {
            if (it.ip.isNotBlank() && it.ip != "0.0.0.0") {
                sendUdp(it.ip, "QUERY_PEER", targetHash)
            }
        }
        delay(1500)
        swarmPeers[targetHash]
    }

    private suspend fun findPeerOnServer(hash: String): UserPayload? {
        // Пытаемся получить свежие данные, если нет - берем из кэша
        fetchAllNodesFromServer() 
        return nodeDao.getNodeByHash(hash)?.let {
            UserPayload(it.userHash, it.phone_hash, it.ip, it.port, it.publicKey, it.phone, it.email, it.lastSeen)
        }
    }

    // ---------- UDP LISTENER ----------

    private fun startListening() = scope.launch {
        if (listening) return@launch
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
        }
    }

    private fun handleIncoming(raw: String, fromIp: String) {
        scope.launch {
            try {
                val json = JSONObject(raw)
                val type = json.getString("type")

                when (type) {
                    "QUERY_PEER" -> {
                        val target = json.getString("data")
                        nodeDao.getNodeByHash(target)?.let {
                            val resp = JSONObject().apply {
                                put("hash", it.userHash)
                                put("ip", it.ip)
                            }
                            sendUdp(fromIp, "PEER_FOUND", resp.toString())
                        }
                    }
                    "PEER_FOUND" -> {
                        val data = JSONObject(json.getString("data"))
                        val hash = data.getString("hash")
                        val ip = data.getString("ip")
                        swarmPeers[hash] = ip
                        nodeDao.updateNetworkInfo(hash, ip, 8888, json.getString("pubkey"), System.currentTimeMillis())
                    }
                    "CHAT", "WEBRTC_SIGNAL" -> {
                        val from = json.getString("from")
                        val pubKey = json.getString("pubkey")
                        val sig = Base64.decode(json.getString("signature"), Base64.NO_WRAP)
                        val unsigned = JSONObject(raw).apply { remove("signature") }.toString().toByteArray()

                        if (CryptoManager.verify(sig, unsigned, pubKey)) {
                            CryptoManager.savePeerPublicKey(from, pubKey)
                            listeners.forEach { it(type, json.getString("data"), fromIp, from) }
                        }
                    }
                }
            } catch (_: Exception) {}
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

    // ---------- WIFI NSD ----------

    private fun registerInWifi() {
        val info = NsdServiceInfo().apply {
            serviceName = getMyId()
            serviceType = SERVICE_TYPE
            port = PORT
        }
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, null)
    }

    private fun discoverInWifi() {
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
            override fun onServiceFound(s: NsdServiceInfo) {
                nsdManager.resolveService(s, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(i: NsdServiceInfo) {
                        i.host?.hostAddress?.let { wifiPeers[i.serviceName] = it }
                    }
                    override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
                })
            }
            override fun onServiceLost(s: NsdServiceInfo) { wifiPeers.remove(s.serviceName) }
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        })
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
