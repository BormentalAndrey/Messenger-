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
    // SupervisorJob позволяет дочерним корутинам падать, не убивая весь репозиторий
    private val repositoryJob = SupervisorJob()
    private val scope = CoroutineScope(repositoryJob + Dispatchers.IO)
    
    // Thread-safe список слушателей
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
        // 1. Поиск в локальном Wi-Fi (быстро)
        wifiPeers[targetHash]?.let { ip ->
            if (sendUdpInternal(ip, "CHAT", message)) return@launch
        }

        // 2. Роевой поиск (через известных пиров)
        val swarmIp = searchInSwarm(targetHash).await()
        if (!swarmIp.isNullOrBlank()) {
            if (sendUdpInternal(swarmIp, "CHAT", message)) return@launch
        }

        // 3. Сервер (глобальный поиск)
        val serverPeer = findPeerOnServer(targetHash).await()
        // Используем safe call для доступа к IP, чтобы избежать Smart Cast Error
        serverPeer?.ip?.let { ip ->
            if (ip.isNotBlank() && ip != "0.0.0.0") {
                if (sendUdpInternal(ip, "CHAT", message)) return@launch
            }
        }

        // 4. SMS (резервный канал)
        if (!targetPhone.isNullOrBlank()) {
            sendAsSms(targetPhone, message)
        }
    }

    // --- МЕТОДЫ СОВМЕСТИМОСТИ (BRIDGES) ---
    
    fun sendSignaling(targetIp: String, type: String, data: String) {
        if (targetIp.isNotBlank() && targetIp != "0.0.0.0") {
            scope.launch { sendUdpInternal(targetIp, type, data) }
        } else {
             Log.w(TAG, "sendSignaling: IP is empty or invalid")
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
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = getMyId()
                serviceType = SERVICE_TYPE
                port = 8888
            }
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, null)
        } catch (e: Exception) {
            Log.e(TAG, "NSD Register failed (possibly no Wifi): ${e.message}")
        }
    }

    private fun discoverInWifi() {
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    // Разрешаем адрес сервиса
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host.hostAddress
                            if (!host.isNullOrBlank()) {
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
        } catch (e: Exception) {
            Log.e(TAG, "NSD Discovery failed: ${e.message}")
        }
    }

    // --- РОЕВОЙ ПОИСК (ИСПРАВЛЕНО ДЛЯ COMPILER SAFETY) ---

    private fun searchInSwarm(targetHash: String): Deferred<String?> = scope.async {
        val cachedNodes = db.nodeDao().getAllNodes().take(100)
        cachedNodes.forEach { node ->
            // ВАЖНО: Копируем в локальную неизменяемую переменную через let
            // Это решает ошибку "Type mismatch: inferred type is String? but String was expected"
            node.ip?.let { safeIp ->
                if (safeIp.isNotBlank() && safeIp != "0.0.0.0") {
                    sendUdpInternal(safeIp, "QUERY_PEER", targetHash)
                }
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
            val buffer = ByteArray(16384) // 16KB буфер
            
            while (isListening && isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    mainSocket?.receive(packet)
                    val rawData = String(packet.data, 0, packet.length)
                    val senderIp = packet.address.hostAddress ?: ""
                    handleIncomingPacket(rawData, senderIp)
                } catch (e: Exception) {
                    if (isListening) Log.e(TAG, "Error receiving packet: ${e.message}")
                }
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Socket init error: ${e.message}")
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
                        // Проверяем БД, если есть инфо о пире — отвечаем
                        val node = db.nodeDao().getNodeByHash(target)
                        node?.ip?.let { targetIp -> 
                            if (targetIp.isNotBlank()) {
                                val response = JSONObject().apply {
                                    put("hash", node.userHash)
                                    put("ip", targetIp)
                                }
                                sendUdpInternal(fromIp, "PEER_FOUND", response.toString())
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
                        val signatureBase64 = json.optString("signature", "")
                        
                        if (signatureBase64.isNotEmpty()) {
                            val signature = Base64.decode(signatureBase64, Base64.NO_WRAP)
                            // Для проверки подписи берем JSON без поля signature
                            val dataToVerify = JSONObject(rawData).apply { remove("signature") }.toString().toByteArray()

                            if (CryptoManager.verify(signature, dataToVerify, pubKey)) {
                                CryptoManager.savePeerPublicKey(senderHash, pubKey)
                                listeners.forEach { it(type, json.getString("data"), fromIp, senderHash) }
                            } else {
                                Log.w(TAG, "Invalid signature from $fromIp")
                            }
                        }
                    }
                }
            } catch (e: Exception) { 
                Log.e(TAG, "Packet parse error: ${e.message}") 
            }
        }
    }

    private fun findPeerOnServer(hash: String): Deferred<UserPayload?> = scope.async {
        try {
            val response = api.getAllNodes()
            response.users?.let { users ->
                // Обновляем кэш узлов
                val entities = users.map { 
                    NodeEntity(
                        userHash = it.hash, 
                        ip = it.ip, 
                        port = it.port, 
                        publicKey = it.publicKey, 
                        lastSeen = System.currentTimeMillis()
                    ) 
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
        } catch (e: Exception) { Log.e(TAG, "SMS failed: ${e.message}") }
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
            // Подпись пакета
            val sig = CryptoManager.sign(json.toString().toByteArray())
            json.put("signature", Base64.encodeToString(sig, Base64.NO_WRAP))

            val bytes = json.toString().toByteArray()
            val address = InetAddress.getByName(ip)
            
            // Создаем новый сокет для отправки и сразу закрываем (use)
            DatagramSocket().use { socket ->
                val packet = DatagramPacket(bytes, bytes.size, address, 8888)
                socket.send(packet)
            }
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
                } catch (e: Exception) { 
                    // Игнорируем ошибки периодического пинга
                }
                delay(180_000) // 3 минуты
            }
        }
    }

    fun getMyId(): String = sha256(CryptoManager.getMyPublicKeyStr())
    fun getMyPublicKeyStr(): String = CryptoManager.getMyPublicKeyStr()
    
    fun addListener(l: (String, String, String, String) -> Unit) = listeners.add(l)
    fun removeListener(l: (String, String, String, String) -> Unit) = listeners.remove(l)
    
    fun onDestroy() { 
        repositoryJob.cancel()
        isListening = false
        mainSocket?.close()
        mainSocket = null
    }
    
    private fun sha256(s: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
