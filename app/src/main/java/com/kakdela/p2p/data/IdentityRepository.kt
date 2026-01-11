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
    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    // Инъекция MessageRepository для обработки входящих сообщений
    // Используем lazy, чтобы избежать круговой зависимости при инициализации
    private val messageRepository by lazy { 
        MessageRepository(context, db.messageDao(), this) 
    }

    private val listeners = CopyOnWriteArrayList<(String, String, String, String) -> Unit>()
    val wifiPeers = ConcurrentHashMap<String, String>()
    val swarmPeers = ConcurrentHashMap<String, String>()

    private val SERVICE_TYPE = "_kakdela_p2p._udp."
    private val PORT = 8888
    private val PEPPER = "7fb8a1d2c3e4f5a6"
    private val SYNC_INTERVAL = 600_000L // 10 минут

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
                startSyncLoop()
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

    private fun startSyncLoop() {
        scope.launch {
            while (isRunning) {
                try {
                    val myId = getMyId()
                    if (myId.isNotEmpty()) {
                        val myPayload = UserPayload(
                            hash = myId,
                            phone_hash = prefs.getString("my_phone_hash", ""),
                            ip = null, 
                            port = PORT,
                            publicKey = CryptoManager.getMyPublicKeyStr(),
                            phone = prefs.getString("my_phone", ""),
                            email = prefs.getString("my_email", null),
                            lastSeen = System.currentTimeMillis()
                        )
                        announceMyself(myPayload)
                        fetchAllNodesFromServer()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sync loop error: ${e.message}")
                }
                delay(SYNC_INTERVAL)
            }
        }
    }

    suspend fun fetchAllNodesFromServer(): List<UserPayload> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAllNodes()
            val users = response.users.orEmpty()
            
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
        } catch (e: Exception) {
            Log.e(TAG, "Fetch failed, using local cache: ${e.message}")
            nodeDao.getAllNodes().map {
                UserPayload(it.userHash, it.phone_hash, it.ip, it.port, it.publicKey, it.phone, null, it.lastSeen)
            }
        }
    }

    fun announceMyself(userPayload: UserPayload) {
        scope.launch {
            try {
                val wrapper = UserRegistrationWrapper(hash = userPayload.hash, data = userPayload)
                api.announceSelf(payload = wrapper)
                
                // Рассылаем Presence пакет всем знакомым в Wi-Fi
                wifiPeers.values.forEach { ip ->
                    sendUdp(ip, "PRESENCE", "ONLINE")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Announce failed", e)
            }
        }
    }

    /* ======================= ROUTING & SIGNALING ======================= */

    fun sendSignaling(targetIp: String, type: String, data: String) {
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("subtype", type)
                    put("payload", data)
                }
                sendUdp(targetIp, "WEBRTC_SIGNAL", json.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Signaling error to $targetIp", e)
            }
        }
    }

    /**
     * Умная отправка сообщения. 
     * Пытается доставить через Wi-Fi -> Swarm -> SMS.
     */
    fun sendMessageSmart(targetHash: String, phone: String?, message: String): Boolean {
        var delivered = false
        runBlocking { // Используем runBlocking внутри scope.launch для последовательной проверки
            val ip = wifiPeers[targetHash] 
                ?: swarmPeers[targetHash] 
                ?: findPeerOnServer(targetHash)?.ip

            if (!ip.isNullOrBlank() && ip != "0.0.0.0") {
                delivered = sendUdp(ip, "CHAT", message)
            }

            if (!delivered && !phone.isNullOrBlank()) {
                sendAsSms(phone, message)
                delivered = true // Считаем доставленным, если ушло по SMS
            }
        }
        return delivered
    }

    private suspend fun findPeerOnServer(hash: String): UserPayload? {
        val cached = nodeDao.getNodeByHash(hash)
        if (cached != null && System.currentTimeMillis() - cached.lastSeen < 300_000) {
            return UserPayload(cached.userHash, cached.phone_hash, cached.ip, cached.port, cached.publicKey, cached.phone, null, cached.lastSeen)
        }
        return fetchAllNodesFromServer().find { it.hash == hash }
    }

    fun getPeerPublicKey(hash: String): String? {
        return runBlocking { nodeDao.getNodeByHash(hash)?.publicKey }
    }

    /* ======================= UDP CORE ======================= */

    private fun startUdpListener() {
        scope.launch {
            try {
                DatagramSocket(PORT).use { socket ->
                    socket.reuseAddress = true
                    val buffer = ByteArray(65507) // Максимальный размер UDP пакета
                    while (isRunning) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val ip = packet.address.hostAddress ?: continue
                        val data = String(packet.data, 0, packet.length)
                        handleIncoming(data, ip)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP listener stopped", e)
            }
        }
    }

    private fun handleIncoming(raw: String, fromIp: String) {
        scope.launch(Dispatchers.Default) {
            try {
                val json = JSONObject(raw)
                val type = json.getString("type")
                val fromHash = json.getString("from")
                val pubKey = json.getString("pubkey")
                val sigBase64 = json.getString("signature")
                
                // Проверка подписи отправителя
                val signature = Base64.decode(sigBase64, Base64.NO_WRAP)
                val unsignedJson = JSONObject(raw).apply { remove("signature") }.toString()

                if (!CryptoManager.verify(signature, unsignedJson.toByteArray(), pubKey)) {
                    Log.w(TAG, "Invalid signature from $fromHash")
                    return@launch
                }

                // Обновляем информацию об узле в БД
                nodeDao.updateNetworkInfo(fromHash, fromIp, PORT, pubKey, System.currentTimeMillis())
                swarmPeers[fromHash] = fromIp

                // Если это сообщение чата — передаем в MessageRepository для расшифровки и сохранения
                if (type == "CHAT" || type == "CHAT_FILE") {
                    messageRepository.handleIncoming(type, json.getString("data"), fromHash)
                }

                // Уведомляем других слушателей (UI, CallService и т.д.)
                listeners.forEach { it(type, json.getString("data"), fromIp, fromHash) }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming UDP: ${e.message}")
            }
        }
    }

    suspend fun sendUdp(ip: String, type: String, data: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("type", type)
                put("data", data)
                put("from", getMyId())
                put("pubkey", CryptoManager.getMyPublicKeyStr())
                put("timestamp", System.currentTimeMillis())
            }

            // Подписываем пакет нашим приватным ключом
            val sig = CryptoManager.sign(json.toString().toByteArray())
            json.put("signature", Base64.encodeToString(sig, Base64.NO_WRAP))

            val bytes = json.toString().toByteArray()
            val addr = InetAddress.getByName(ip)
            DatagramSocket().use { 
                it.soTimeout = 2000
                it.send(DatagramPacket(bytes, bytes.size, addr, PORT)) 
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "UDP send failed to $ip: ${e.message}")
            false
        }
    }

    /* ======================= NSD & WI-FI ======================= */

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(s: NsdServiceInfo) { Log.d(TAG, "NSD Registered") }
        override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) {}
        override fun onServiceUnregistered(s: NsdServiceInfo) {}
        override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) {}
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(s: NsdServiceInfo) {
            if (s.serviceType != SERVICE_TYPE) return
            // Не резолвим самих себя
            if (s.serviceName.contains(getMyId().take(8))) return
            
            nsdManager.resolveService(s, object : NsdManager.ResolveListener {
                override fun onServiceResolved(r: NsdServiceInfo) {
                    val host = r.host?.hostAddress ?: return
                    // Извлекаем hash из имени сервиса "KakDela-HASH-RAND"
                    val parts = r.serviceName.split("-")
                    if (parts.size >= 2) {
                        val peerHash = parts[1]
                        wifiPeers[peerHash] = host
                        Log.d(TAG, "Resolved Wi-Fi peer: $peerHash at $host")
                    }
                }
                override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
            })
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
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "KakDela-${getMyId().take(8)}-${UUID.randomUUID().toString().take(4)}"
                serviceType = SERVICE_TYPE
                port = PORT
            }
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "NSD Register failed: ${e.message}")
        }
    }

    private fun discoverInWifi() {
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "NSD Discovery failed: ${e.message}")
        }
    }

    /* ======================= UTILS ======================= */

    private fun sendAsSms(phone: String, message: String) {
        try {
            val sms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                context.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.sendTextMessage(phone, null, "[P2P] $message", null, null)
            Log.i(TAG, "SMS Sent to $phone")
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed: ${e.message}")
        }
    }

    fun generatePhoneDiscoveryHash(phone: String): String {
        val digits = phone.replace(Regex("[^0-9]"), "")
        val normalized = if (digits.length == 11 && digits.startsWith("8")) "7${digits.substring(1)}" else digits
        return sha256(normalized + PEPPER)
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
