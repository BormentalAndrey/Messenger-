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
import java.util.concurrent.CopyOnWriteArrayList

class IdentityRepository(private val context: Context) {

    private val TAG = "IdentityRepository"
    
    // Используем SupervisorJob, чтобы ошибка в одной корутине не убила всё сетевое взаимодействие
    private val repositoryJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + repositoryJob)
    
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    // Потокобезопасные списки слушателей и кэш пиров
    private val listeners = CopyOnWriteArrayList<(type: String, data: String, fromIp: String, fromHash: String) -> Unit>()
    private val wifiPeers = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val swarmPeers = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()
    private val api = MyServerApiFactory.instance

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val SERVICE_TYPE = "_kakdela_p2p._udp"
    private val PORT = 8888
    private val PEPPER = "7fb8a1d2c3e4f5a6"

    private var socket: DatagramSocket? = null
    private var isListening = false

    // ---------- УПРАВЛЕНИЕ СЕТЬЮ ----------

    fun startNetwork() {
        if (isListening) return
        
        scope.launch {
            try {
                CryptoManager.init(context)
                startListening()
                registerInWifi()
                discoverInWifi()
                Log.i(TAG, "P2P Stack Started Successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start network stack", e)
            }
        }
    }

    fun stopNetwork() {
        isListening = false
        socket?.close()
        socket = null
        
        try {
            nsdManager.unregisterService(registrationListener)
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.w(TAG, "NSD Cleanup warning: ${e.message}")
        }
        
        // Отменяем все активные операции репозитория
        repositoryJob.cancelChildren()
    }

    fun addListener(l: (String, String, String, String) -> Unit) = listeners.add(l)
    fun removeListener(l: (String, String, String, String) -> Unit) = listeners.remove(l)

    fun getMyId(): String = prefs.getString("my_security_hash", "") ?: ""

    // ---------- ПЕРЕДАЧА ДАННЫХ ----------

    fun sendSignaling(targetIp: String, type: String, data: String) {
        scope.launch {
            val json = JSONObject().apply {
                put("subtype", type)
                put("payload", data)
            }
            sendUdp(targetIp, "WEBRTC_SIGNAL", json.toString())
        }
    }

    fun announceMyself(wrapper: UserRegistrationWrapper) {
        scope.launch {
            try {
                // Регистрируем наш узел на глобальном шлюзе (для поиска через 4G)
                api.registerUser(wrapper)
                
                // Оповещаем соседей по Wi-Fi напрямую
                wifiPeers.values.forEach { ip ->
                    sendUdp(ip, "PRESENCE", "ONLINE")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Global announce failed", e)
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
            Log.e(TAG, "Server sync failed, falling back to local DB", e)
            nodeDao.getAllNodes().map {
                UserPayload(it.userHash, it.phone_hash, it.ip, it.port, it.publicKey, it.phone, it.email, it.lastSeen)
            }
        }
    }

    fun sendMessageSmart(targetHash: String, targetPhone: String?, message: String) = scope.launch {
        // Умная маршрутизация: сначала ищем в локальной сети, потом в кэше, потом в глобальном реестре
        val targetIp = wifiPeers[targetHash] 
            ?: swarmPeers[targetHash] 
            ?: findPeerOnServer(targetHash)?.ip

        var delivered = false
        if (!targetIp.isNullOrBlank() && targetIp != "0.0.0.0") {
            delivered = sendUdp(targetIp, "CHAT", message)
        }

        // Если P2P доставка не удалась и есть номер телефона — используем SMS как последний шанс
        if (!delivered && !targetPhone.isNullOrBlank()) {
            sendAsSms(targetPhone, message)
        }
    }

    private suspend fun findPeerOnServer(hash: String): UserPayload? {
        val cached = nodeDao.getNodeByHash(hash)
        // Если данные в кэше свежее 2 минут — доверяем им
        if (cached != null && (System.currentTimeMillis() - cached.lastSeen) < 120_000) {
            return UserPayload(cached.userHash, cached.phone_hash, cached.ip, cached.port, cached.publicKey, cached.phone, cached.email, cached.lastSeen)
        }
        return fetchAllNodesFromServer().find { it.hash == hash }
    }

    // ---------- ПРОТОКОЛ ОБМЕНА (UDP) ----------

    private fun startListening() {
        isListening = true
        scope.launch {
            try {
                socket = DatagramSocket(PORT).apply { reuseAddress = true }
                val buffer = ByteArray(16384)
                
                while (isListening && !socket!!.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    
                    val fromIp = packet.address.hostAddress ?: ""
                    val rawData = String(packet.data, 0, packet.length)
                    
                    // Обработку выносим в отдельную корутину, чтобы не блокировать чтение сокета
                    launch { handleIncoming(rawData, fromIp) }
                }
            } catch (e: Exception) {
                if (isListening) Log.e(TAG, "Socket listener error", e)
                isListening = false
            }
        }
    }

    private suspend fun handleIncoming(raw: String, fromIp: String) = withContext(Dispatchers.Default) {
        try {
            val json = JSONObject(raw)
            val type = json.getString("type")
            val fromHash = json.optString("from")

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
                    swarmPeers[data.getString("hash")] = data.getString("ip")
                }
                "CHAT", "WEBRTC_SIGNAL" -> {
                    val pubKey = json.getString("pubkey")
                    val signatureBase64 = json.getString("signature")
                    
                    // Проверка криптографической подписи сообщения
                    val signature = Base64.decode(signatureBase64, Base64.NO_WRAP)
                    val unsignedJson = JSONObject(raw).apply { remove("signature") }
                    val isVerified = CryptoManager.verify(signature, unsignedJson.toString().toByteArray(), pubKey)

                    if (isVerified) {
                        CryptoManager.savePeerPublicKey(fromHash, pubKey)
                        nodeDao.updateNetworkInfo(fromHash, fromIp, PORT, pubKey, System.currentTimeMillis())
                        
                        // Уведомляем всех подписчиков (UI, Сервисы)
                        listeners.forEach { it(type, json.getString("data"), fromIp, fromHash) }
                    } else {
                        Log.w(TAG, "Bypassed packet: Signature verification failed from $fromHash")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse incoming packet from $fromIp: ${e.message}")
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
            
            // Подписываем данные приватным ключом устройства
            val signature = CryptoManager.sign(json.toString().toByteArray())
            json.put("signature", Base64.encodeToString(signature, Base64.NO_WRAP))

            val bytes = json.toString().toByteArray()
            val address = InetAddress.getByName(ip)
            val packet = DatagramPacket(bytes, bytes.size, address, PORT)
            
            DatagramSocket().use { it.send(packet) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "UDP Send to $ip failed", e)
            false
        }
    }

    // ---------- NSD (ЛОКАЛЬНЫЙ ПОИСК) ----------

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) { Log.i(TAG, "Local NSD Service Registered: ${info.serviceName}") }
        override fun onRegistrationFailed(info: NsdServiceInfo, err: Int) { Log.e(TAG, "NSD Registration error: $err") }
        override fun onServiceUnregistered(info: NsdServiceInfo) {}
        override fun onUnregistrationFailed(info: NsdServiceInfo, err: Int) {}
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(service: NsdServiceInfo) {
            // Если нашли сервис нашего типа и это не мы сами
            if (service.serviceType == SERVICE_TYPE && !service.serviceName.contains(getMyId().take(8))) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        // Извлекаем hash из имени KakDela-HASH-RAND
                        val peerHash = resolved.serviceName.substringAfter("KakDela-").substringBefore("-")
                        wifiPeers[peerHash] = host
                        Log.d(TAG, "Peer discovered via Wi-Fi: $peerHash at $host")
                    }
                    override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
                })
            }
        }
        override fun onServiceLost(s: NsdServiceInfo) {
            wifiPeers.entries.removeIf { it.value == s.host?.hostAddress }
        }
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) {}
        override fun onStartDiscoveryFailed(t: String, e: Int) {}
        override fun onStopDiscoveryFailed(t: String, e: Int) {}
    }

    private fun registerInWifi() {
        val serviceInfo = NsdServiceInfo().apply {
            // Имя содержит часть нашего хеша для быстрой идентификации в локальной сети
            serviceName = "KakDela-${getMyId().take(8)}-${UUID.randomUUID().toString().take(4)}"
            serviceType = SERVICE_TYPE
            port = PORT
        }
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "NSD Register failed", e)
        }
    }

    private fun discoverInWifi() {
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "NSD Discovery failed", e)
        }
    }

    // ---------- ХЕШИРОВАНИЕ И УТИЛИТЫ ----------

    fun generatePhoneDiscoveryHash(phone: String): String {
        val normalized = phone.replace(Regex("[^0-9]"), "").let {
            if (it.startsWith("8")) "7" + it.substring(1) else it
        }
        return sha256(normalized + PEPPER)
    }

    private fun sendAsSms(phone: String, message: String) {
        try {
            val smsManager: SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phone, null, "[P2P] $message", null, null)
            Log.d(TAG, "SMS fallback sent to $phone")
        } catch (e: Exception) {
            Log.e(TAG, "SMS fallback failed", e)
        }
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
