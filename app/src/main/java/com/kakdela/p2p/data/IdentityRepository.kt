package com.kakdela.p2p.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import com.kakdela.p2p.api.MyServerApi
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.*
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.*
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

class IdentityRepository(private val context: Context) {
    private val TAG = "IdentityRepository"
    private val repositoryJob = SupervisorJob()
    private val scope = CoroutineScope(repositoryJob + Dispatchers.IO)
    private val listeners = CopyOnWriteArrayList<(String, String, String, String) -> Unit>()
    
    private val wifiPeers = mutableMapOf<String, String>() // Hash -> IP
    private val swarmPeers = mutableMapOf<String, String>() // Hash -> IP
    
    private val db = ChatDatabase.getDatabase(context)
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val SERVICE_TYPE = "_kakdela_p2p._udp"

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    private var mainSocket: DatagramSocket? = null
    private var isListening = false

    init {
        CryptoManager.init(context)
        startListening()
        registerInWifi()
        discoverInWifi()
        startKeepAlive()
    }

    // --- ГЛАВНАЯ ЛОГИКА ОТПРАВКИ ---

    fun sendMessageSmart(targetHash: String, targetPhone: String?, message: String) = scope.launch {
        // 1. Поиск в локальном Wi-Fi
        wifiPeers[targetHash]?.let { ip ->
            if (sendUdpInternal(ip, "CHAT", message)) return@launch
        }

        // 2. Роевой поиск
        val swarmIp = searchInSwarm(targetHash).await()
        if (swarmIp != null) {
            if (sendUdpInternal(swarmIp, "CHAT", message)) return@launch
        }

        // 3. Сервер
        val serverPeer = findPeerOnServer(targetHash).await()
        if (serverPeer?.ip != null && serverPeer.ip != "0.0.0.0") {
            if (sendUdpInternal(serverPeer.ip, "CHAT", message)) return@launch
        }

        // 4. SMS
        if (!targetPhone.isNullOrBlank()) {
            sendAsSms(targetPhone, message)
        }
    }

    // --- МЕТОДЫ СОВМЕСТИМОСТИ (BRIDGES) ---
    
    /** Используется WebRTC и FileTransfer для прямой отправки */
    fun sendSignaling(targetIp: String, type: String, data: String) {
        if (targetIp.isNotBlank() && targetIp != "0.0.0.0") {
            scope.launch { sendUdpInternal(targetIp, type, data) }
        } else {
             scope.launch {
                 Log.w(TAG, "sendSignaling: IP is empty")
             }
        }
    }

    fun generateUserHash(phone: String, email: String, pass: String): String {
        return sha256("$phone:$email:$pass")
    }

    fun savePeerPublicKey(hash: String, key: String) {
        CryptoManager.savePeerPublicKey(hash, key)
    }

    suspend fun findPeerInDHT(hash: String): UserPayload? {
        return findPeerOnServer(hash).await()
    }

    // --- WI-FI DISCOVERY ---

    private fun registerInWifi() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = getMyId()
            serviceType = SERVICE_TYPE
            port = 8888
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, null)
    }

    private fun discoverInWifi() {
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host.hostAddress
                        if (host != null) {
                            wifiPeers[info.serviceName] = host
                        }
                    }
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) { wifiPeers.remove(service.serviceName) }
            override fun onDiscoveryStopped(p0: String?) {}
            override fun onStartDiscoveryFailed(p0: String?, p1: Int) {}
            override fun onStopDiscoveryFailed(p0: String?, p1: Int) {}
        })
    }

    // --- РОЕВОЙ ПОИСК (ИСПРАВЛЕН NPE) ---

    private fun searchInSwarm(targetHash: String): Deferred<String?> = scope.async {
        val cachedNodes = db.nodeDao().getAllNodes().take(100)
        cachedNodes.forEach { node ->
            // Исправлена ошибка Type Mismatch: проверяем на null/empty перед вызовом
            val nodeIp = node.ip
            if (!nodeIp.isNullOrBlank()) {
                sendUdpInternal(nodeIp, "QUERY_PEER", targetHash)
            }
        }
        delay(2000)
        return@async swarmPeers[targetHash]
    }

    // --- UDP ---

    private fun startListening() = scope.launch(Dispatchers.IO) {
        if (isListening) return@launch
        try {
            mainSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(8888))
            }
            isListening = true
            val buffer = ByteArray(16384)
            while (isListening && isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                mainSocket?.receive(packet)
                val rawData = String(packet.data, 0, packet.length)
                val senderIp = packet.address.hostAddress ?: ""
                handleIncomingPacket(rawData, senderIp)
            }
        } catch (e: Exception) { isListening = false }
    }

    private fun handleIncomingPacket(rawData: String, fromIp: String) {
        try {
            val json = JSONObject(rawData)
            val type = json.optString("type")
            
            when (type) {
                "QUERY_PEER" -> {
                    val target = json.getString("data")
                    scope.launch {
                        db.nodeDao().getNodeByHash(target)?.let {
                            sendUdpInternal(fromIp, "PEER_FOUND", JSONObject().apply {
                                put("hash", it.userHash); put("ip", it.ip)
                            }.toString())
                        }
                    }
                }
                "PEER_FOUND" -> {
                    val data = JSONObject(json.getString("data"))
                    swarmPeers[data.getString("hash")] = data.getString("ip")
                }
                "CHAT", "WEBRTC_SIGNAL" -> {
                    val senderHash = json.getString("from")
                    val pubKey = json.getString("pubkey")
                    val signature = Base64.decode(json.getString("signature"), Base64.NO_WRAP)
                    val dataToVerify = JSONObject(rawData).apply { remove("signature") }.toString().toByteArray()

                    if (CryptoManager.verify(signature, dataToVerify, pubKey)) {
                        CryptoManager.savePeerPublicKey(senderHash, pubKey)
                        listeners.forEach { it(type, json.getString("data"), fromIp, senderHash) }
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Packet parse error", e) }
    }

    private fun findPeerOnServer(hash: String): Deferred<UserPayload?> = scope.async {
        try {
            val response = api.getAllNodes()
            response.users?.let { users ->
                val entities = users.map { 
                    NodeEntity(userHash = it.hash, ip = it.ip, port = it.port, publicKey = it.publicKey, lastSeen = System.currentTimeMillis()) 
                }
                db.nodeDao().updateCache(entities)
                return@async users.find { it.hash == hash }
            }
        } catch (e: Exception) { Log.e(TAG, "Server error: ${e.message}") }
        null
    }

    private fun sendAsSms(phone: String, message: String) {
        try {
            SmsManager.getDefault().sendTextMessage(phone, null, "[P2P]$message", null, null)
        } catch (e: Exception) { Log.e(TAG, "SMS failed") }
    }

    private suspend fun sendUdpInternal(ip: String, type: String, data: String): Boolean = withContext(Dispatchers.IO) {
        if (ip.isBlank() || ip == "0.0.0.0") return@withContext false
        try {
            val json = JSONObject().apply {
                put("type", type); put("data", data); put("from", getMyId())
                put("pubkey", getMyPublicKeyStr()); put("timestamp", System.currentTimeMillis())
            }
            val sig = CryptoManager.sign(json.toString().toByteArray())
            json.put("signature", Base64.encodeToString(sig, Base64.NO_WRAP))

            val bytes = json.toString().toByteArray()
            DatagramSocket().use { it.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(ip), 8888)) }
            true
        } catch (e: Exception) { 
            Log.e(TAG, "UDP send failed to $ip: ${e.message}")
            false 
        }
    }

    private fun startKeepAlive() {
        scope.launch {
            while (isActive) {
                try {
                    api.announceSelf(payload = UserPayload(hash = getMyId(), publicKey = getMyPublicKeyStr(), port = 8888))
                } catch (e: Exception) { }
                delay(180_000)
            }
        }
    }

    fun getMyId() = sha256(CryptoManager.getMyPublicKeyStr())
    fun getMyPublicKeyStr() = CryptoManager.getMyPublicKeyStr()
    fun addListener(l: (String, String, String, String) -> Unit) = listeners.add(l)
    fun removeListener(l: (String, String, String, String) -> Unit) = listeners.remove(l)
    fun onDestroy() { repositoryJob.cancel(); isListening = false; mainSocket?.close() }
    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
