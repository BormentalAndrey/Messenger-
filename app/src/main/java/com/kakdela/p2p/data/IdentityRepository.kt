package com.kakdela.p2p.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import com.kakdela.p2p.api.MyServerApi
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.api.UserRegistrationWrapper
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.*
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Центральный репозиторий управления личностью и сетевыми узлами.
 * Реализует гибридную логику обнаружения: Локальная сеть (NSD), P2P Рой (UDP) и серверный Discovery.
 */
class IdentityRepository(private val context: Context) {
    private val TAG = "IdentityRepository"
    private val repositoryJob = SupervisorJob()
    private val scope = CoroutineScope(repositoryJob + Dispatchers.IO)
    
    private val listeners = CopyOnWriteArrayList<(String, String, String, String) -> Unit>()
    
    private val wifiPeers = mutableMapOf<String, String>()  
    private val swarmPeers = mutableMapOf<String, String>() 
    
    private val db = ChatDatabase.getDatabase(context)
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val SERVICE_TYPE = "_kakdela_p2p._udp"
    
    private val PEPPER = "7fb8a1d2c3e4f5a6" 

    // Оптимизированный клиент для InfinityFree
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/")
            .client(okHttpClient)
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
    }

    // --- УПРАВЛЕНИЕ ЛИЧНОСТЬЮ ---

    fun generateSecurityHash(phone: String, email: String, pass: String): String {
        // Приводим телефон к стандарту 7... перед хешированием безопасности
        val cleanPhone = normalizePhoneInternal(phone)
        return sha256("$cleanPhone|$email|$pass")
    }

    /**
     * ИСПРАВЛЕНО: Теперь генерирует хэш от полного номера (7900...),
     * чтобы он совпадал с тем, что AuthManager отправляет на сервер.
     */
    fun generatePhoneDiscoveryHash(phone: String): String {
        val normalized = normalizePhoneInternal(phone)
        return sha256(normalized + PEPPER)
    }

    private fun normalizePhoneInternal(raw: String): String {
        var digits = raw.replace(Regex("[^0-9]"), "")
        return when {
            digits.length == 11 && digits.startsWith("8") -> "7" + digits.substring(1)
            digits.length == 10 && digits.startsWith("9") -> "7" + digits
            else -> digits
        }
    }

    fun savePeerPublicKey(hash: String, key: String) {
        CryptoManager.savePeerPublicKey(hash, key)
    }

    // --- СЕТЕВЫЕ ОПЕРАЦИИ (UDP & SIGNALLING) ---

    fun sendSignaling(targetIp: String, type: String, data: String) {
        if (targetIp.isNotBlank() && targetIp != "0.0.0.0") {
            scope.launch { sendUdpInternal(targetIp, type, data) }
        }
    }

    suspend fun announceMyself(wrapper: UserRegistrationWrapper): Boolean {
        return try {
            val response = api.announceSelf(payload = wrapper)
            response.success
        } catch (e: Exception) {
            Log.e(TAG, "Announce failed: ${e.message}")
            false
        }
    }

    fun findPeerInDHT(hash: String): Deferred<UserPayload?> = scope.async {
        swarmPeers[hash]?.let { ip ->
            return@async UserPayload(hash = hash, ip = ip, publicKey = "", port = 8888)
        }
        return@async findPeerOnServer(hash).await()
    }

    fun sendMessageSmart(targetHash: String, targetPhone: String?, message: String) = scope.launch {
        // 1. Приоритет: Локальный Wi-Fi
        wifiPeers[targetHash]?.let { ip ->
            if (sendUdpInternal(ip, "CHAT", message)) return@launch
        }
        
        // 2. Swarm поиск через соседей
        searchInSwarm(targetHash).await()?.let { ip ->
            if (sendUdpInternal(ip, "CHAT", message)) return@launch
        }
        
        // 3. Последний известный IP с сервера
        findPeerOnServer(targetHash).await()?.ip?.let { ip ->
            if (ip.isNotBlank() && ip != "0.0.0.0") {
                if (sendUdpInternal(ip, "CHAT", message)) return@launch
            }
        }
        
        // 4. Fallback: SMS при полном отсутствии сети
        if (!targetPhone.isNullOrBlank()) {
            sendAsSms(targetPhone, message)
        }
    }

    // --- СИНХРОНИЗАЦИЯ С СЕРВЕРОМ ---

    suspend fun fetchAllNodesFromServer(): List<UserPayload> {
        return try {
            val response = api.getAllNodes()
            response.users ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Fetch all nodes failed: ${e.message}")
            emptyList()
        }
    }

    private fun findPeerOnServer(hash: String): Deferred<UserPayload?> = scope.async {
        try {
            val response = api.getAllNodes()
            val users = response.users ?: return@async null
            
            // Фоновое обновление кэша
            scope.launch {
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
                db.nodeDao().updateCache(entities)
            }
            
            return@async users.find { it.hash == hash }
        } catch (e: Exception) {
            null
        }
    }

    fun startKeepAlive(securityHash: String, phoneHash: String, email: String, phone: String) {
        scope.launch {
            while (isActive) {
                try {
                    val payload = UserPayload(
                        hash = securityHash,
                        phone_hash = phoneHash,
                        publicKey = getMyPublicKeyStr(),
                        phone = normalizePhoneInternal(phone),
                        email = email,
                        port = 8888
                    )
                    api.announceSelf(payload = UserRegistrationWrapper(hash = securityHash, data = payload))
                } catch (e: Exception) { 
                    Log.e(TAG, "KeepAlive failed")
                }
                delay(180_000) // Раз в 3 минуты
            }
        }
    }

    // --- UDP И NSD ---

    private fun registerInWifi() {
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = getMyId()
                serviceType = SERVICE_TYPE
                port = 8888
            }
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, null)
        } catch (e: Exception) { }
    }

    private fun discoverInWifi() {
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host.hostAddress
                            if (!host.isNullOrBlank()) wifiPeers[info.serviceName] = host
                        }
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                    })
                }
                override fun onServiceLost(service: NsdServiceInfo) { wifiPeers.remove(service.serviceName) }
                override fun onDiscoveryStopped(p0: String?) {}
                override fun onStartDiscoveryFailed(p0: String?, p1: Int) {}
                override fun onStopDiscoveryFailed(p0: String?, p1: Int) {}
            })
        } catch (e: Exception) { }
    }

    private fun searchInSwarm(targetHash: String): Deferred<String?> = scope.async {
        db.nodeDao().getAllNodes().shuffled().take(50).forEach { node ->
            if (node.ip.isNotBlank() && node.ip != "0.0.0.0") {
                sendUdpInternal(node.ip, "QUERY_PEER", targetHash)
            }
        }
        delay(1500) 
        return@async swarmPeers[targetHash]
    }

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
                packet.address.hostAddress?.let { handleIncomingPacket(rawData, it) }
            }
        } catch (e: Exception) { 
            isListening = false 
        }
    }

    private fun handleIncomingPacket(rawData: String, fromIp: String) {
        scope.launch {
            try {
                val json = JSONObject(rawData)
                val type = json.optString("type")
                when (type) {
                    "QUERY_PEER" -> {
                        val target = json.getString("data")
                        db.nodeDao().getNodeByHash(target)?.let { node ->
                            val resp = JSONObject().apply { 
                                put("hash", node.userHash)
                                put("ip", node.ip) 
                            }
                            sendUdpInternal(fromIp, "PEER_FOUND", resp.toString())
                        }
                    }
                    "PEER_FOUND" -> {
                        val data = JSONObject(json.getString("data"))
                        swarmPeers[data.getString("hash")] = data.getString("ip")
                    }
                    "CHAT", "WEBRTC_SIGNAL" -> {
                        val senderHash = json.getString("from")
                        val pubKey = json.getString("pubkey")
                        val sigBase64 = json.optString("signature", "")
                        
                        if (sigBase64.isNotEmpty()) {
                            val sig = Base64.decode(sigBase64, Base64.NO_WRAP)
                            val dataToVerify = JSONObject(rawData).apply { remove("signature") }.toString().toByteArray()
                            
                            if (CryptoManager.verify(sig, dataToVerify, pubKey)) {
                                CryptoManager.savePeerPublicKey(senderHash, pubKey)
                                listeners.forEach { it(type, json.getString("data"), fromIp, senderHash) }
                            }
                        }
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private suspend fun sendUdpInternal(ip: String, type: String, data: String): Boolean = withContext(Dispatchers.IO) {
        if (ip.isBlank() || ip == "0.0.0.0") return@withContext false
        try {
            val json = JSONObject().apply {
                put("type", type)
                put("data", data)
                put("from", getMyId())
                put("pubkey", getMyPublicKeyStr())
                put("timestamp", System.currentTimeMillis())
            }
            val sig = CryptoManager.sign(json.toString().toByteArray())
            json.put("signature", Base64.encodeToString(sig, Base64.NO_WRAP))
            
            val bytes = json.toString().toByteArray()
            DatagramSocket().use { socket ->
                socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(ip), 8888))
            }
            true
        } catch (e: Exception) { false }
    }

    private fun sendAsSms(phone: String, message: String) {
        try { 
            SmsManager.getDefault().sendTextMessage(phone, null, "[P2P] $message", null, null) 
        } catch (e: Exception) { }
    }

    // --- ХЕЛПЕРЫ ---
    
    fun getMyId() = sha256(CryptoManager.getMyPublicKeyStr())
    fun getMyPublicKeyStr() = CryptoManager.getMyPublicKeyStr()
    fun addListener(l: (String, String, String, String) -> Unit) = listeners.add(l)
    fun removeListener(l: (String, String, String, String) -> Unit) = listeners.remove(l)
    
    fun onDestroy() { 
        repositoryJob.cancel()
        isListening = false
        mainSocket?.close() 
    }
    
    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
