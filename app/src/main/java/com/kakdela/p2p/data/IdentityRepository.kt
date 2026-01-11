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
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Репозиторий идентификации и сетевого обнаружения.
 * Реализует гибридную схему: Wi-Fi (NSD) + Центральный сервер (с обходом Anti-Bot) + UDP P2P.
 */
class IdentityRepository(private val context: Context) {

    private val TAG = "IdentityRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Используем auth_prefs для хранения ID и данных авторизации
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private val api = MyServerApiFactory.instance
    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    // Ленивая инициализация MessageRepository для разрыва циклической зависимости
    private val messageRepository by lazy { 
        MessageRepository(context, db.messageDao(), this) 
    }

    private val listeners = CopyOnWriteArrayList<(String, String, String, String) -> Unit>()
    
    // Активные пиры в локальной сети (Hash -> IP)
    val wifiPeers = ConcurrentHashMap<String, String>()
    // Активные пиры в глобальной сети, от которых недавно пришли пакеты (Hash -> IP)
    val swarmPeers = ConcurrentHashMap<String, String>()

    private val SERVICE_TYPE = "_kakdela_p2p._udp."
    private val PORT = 8888
    private val PEPPER = "7fb8a1d2c3e4f5a6"
    // Интервал 5 минут, так как бесплатные хостинги быстро забывают клиентов
    private val SYNC_INTERVAL = 300_000L 

    @Volatile
    private var isRunning = false

    /* ======================= PUBLIC API ======================= */

    fun startNetwork() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                // 1. Инициализация криптографии (критично перед получением ID)
                CryptoManager.init(context)
                
                // 2. Получение ID
                val myId = getMyId()
                Log.i(TAG, "Network starting. Local Node ID: $myId")

                // 3. Запуск слушателей
                launch { startUdpListener() }
                launch { registerInWifi() }
                launch { discoverInWifi() }
                
                // 4. Первая синхронизация с сервером
                if (myId.isNotEmpty()) {
                    performServerSync(myId)
                }

                // 5. Запуск периодического цикла обновления
                startSyncLoop()
                
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
        Log.i(TAG, "Network stopped")
    }

    fun addListener(l: (String, String, String, String) -> Unit) = listeners.add(l)
    fun removeListener(l: (String, String, String, String) -> Unit) = listeners.remove(l)

    /**
     * Возвращает уникальный хеш текущего устройства.
     * Генерируется на основе публичного ключа ECC/RSA.
     */
    fun getMyId(): String {
        var id = prefs.getString("my_security_hash", "") ?: ""
        
        if (id.isEmpty()) {
            id = CryptoManager.getMyIdentityHash()
            if (id.isNotEmpty()) {
                prefs.edit().putString("my_security_hash", id).apply()
                Log.i(TAG, "New Identity generated and saved: $id")
            } else {
                Log.w(TAG, "Identity hash is empty! Keys might not be ready.")
            }
        }
        return id
    }

    /* ======================= SERVER & SYNC ======================= */

    private fun startSyncLoop() {
        scope.launch {
            while (isRunning) {
                try {
                    val myId = getMyId()
                    if (myId.isNotEmpty()) {
                        performServerSync(myId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sync loop iteration failed: ${e.message}")
                }
                delay(SYNC_INTERVAL)
            }
        }
    }

    private suspend fun performServerSync(myId: String) {
        // Выполняем в IO диспетчере, чтобы не блокировать UI
        withContext(Dispatchers.IO) {
            try {
                val myPayload = UserPayload(
                    hash = myId,
                    phone_hash = prefs.getString("my_phone_hash", null),
                    ip = null, // IP определит сервер
                    port = PORT,
                    publicKey = CryptoManager.getMyPublicKeyStr(),
                    phone = prefs.getString("my_phone", null),
                    email = prefs.getString("my_email", null),
                    lastSeen = System.currentTimeMillis()
                )
                
                announceMyself(myPayload)
                
                // Если анонс прошел (или даже если нет), пробуем обновить список узлов
                fetchAllNodesFromServer()
            } catch (e: Exception) {
                Log.e(TAG, "Server sync error: ${e.message}")
            }
        }
    }

    suspend fun fetchAllNodesFromServer(): List<UserPayload> = withContext(Dispatchers.IO) {
        try {
            // Retrofit использует CookieStore, поэтому запрос пройдет, если кука есть
            val response = api.getAllNodes()
            val users = response.users.orEmpty()
            
            if (users.isNotEmpty()) {
                // Обновляем локальный кеш БД
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
                Log.d(TAG, "Fetched ${users.size} nodes from server")
            }
            users
        } catch (e: Exception) {
            Log.e(TAG, "Fetch failed (using local cache): ${e.message}")
            // В случае ошибки возвращаем локальный кеш
            nodeDao.getAllNodes().map {
                UserPayload(it.userHash, it.phone_hash, it.ip, it.port, it.publicKey, it.phone, null, it.lastSeen)
            }
        }
    }

    private fun announceMyself(userPayload: UserPayload) {
        scope.launch {
            try {
                val wrapper = UserRegistrationWrapper(hash = userPayload.hash, data = userPayload)
                val response = api.announceSelf(payload = wrapper)
                
                if (response.success) {
                    Log.d(TAG, "Announce OK")
                    // При успешном анонсе сообщаем всем в локальной сети, что мы онлайн
                    wifiPeers.values.forEach { ip ->
                        sendUdp(ip, "PRESENCE", "ONLINE")
                    }
                } else {
                    Log.w(TAG, "Announce Server Error: ${response.error}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Announce Transport Error: ${e.message}")
            }
        }
    }

    /* ======================= ROUTING & MESSAGING ======================= */

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
     * Умная отправка сообщения с каскадным поиском маршрута.
     */
    fun sendMessageSmart(targetHash: String, phone: String?, message: String): Boolean {
        var delivered = false
        
        // Используем runBlocking для ожидания результата (т.к. метод возвращает Boolean),
        // но строго в IO диспетчере
        runBlocking(Dispatchers.IO) { 
            // 1. Приоритет Wi-Fi
            var ip = wifiPeers[targetHash] 
            
            // 2. Если нет в Wi-Fi, ищем в активных P2P соединениях (Swarm)
            if (ip == null) ip = swarmPeers[targetHash]
            
            // 3. Если нет, ищем в глобальном кеше сервера
            if (ip == null) ip = findPeerOnServer(targetHash)?.ip

            // Попытка отправки через UDP
            if (!ip.isNullOrBlank() && ip != "0.0.0.0") {
                delivered = sendUdp(ip, "CHAT", message)
                if (delivered) Log.d(TAG, "Sent via UDP to $ip")
            }

            // 4. Fallback: SMS (если UDP не прошел и есть номер телефона)
            if (!delivered && !phone.isNullOrBlank()) {
                sendAsSms(phone, message)
                delivered = true 
            }
        }
        return delivered
    }

    private suspend fun findPeerOnServer(hash: String): UserPayload? {
        // Сначала ищем в локальной БД
        val cached = nodeDao.getNodeByHash(hash)
        // Если запись свежая (менее 5 минут), используем её
        if (cached != null && System.currentTimeMillis() - cached.lastSeen < 300_000) {
            return UserPayload(cached.userHash, cached.phone_hash, cached.ip, cached.port, cached.publicKey, cached.phone, null, cached.lastSeen)
        }
        // Иначе пробуем загрузить с сервера
        return fetchAllNodesFromServer().find { it.hash == hash }
    }

    fun getPeerPublicKey(hash: String): String? {
        return runBlocking { nodeDao.getNodeByHash(hash)?.publicKey }
    }

    /* ======================= UDP CORE ======================= */

    private fun startUdpListener() {
        scope.launch {
            try {
                DatagramSocket(null).use { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(PORT))
                    
                    val buffer = ByteArray(65507) 
                    Log.i(TAG, "UDP Listener started on port $PORT")
                    
                    while (isRunning) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet) // Блокирующий вызов
                            
                            val ip = packet.address.hostAddress ?: continue
                            val data = String(packet.data, 0, packet.length)
                            
                            // Обрабатываем входящее сообщение в отдельной корутине
                            handleIncoming(data, ip)
                        } catch (e: Exception) {
                            if (isRunning) Log.e(TAG, "Packet receive error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP Bind failed (Port $PORT busy?): ${e.message}")
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
                
                // 1. Проверка цифровой подписи
                val signature = Base64.decode(sigBase64, Base64.NO_WRAP)
                // Воссоздаем JSON без поля signature для проверки
                val unsignedJson = JSONObject(raw).apply { remove("signature") }.toString()

                if (!CryptoManager.verify(signature, unsignedJson.toByteArray(), pubKey)) {
                    Log.w(TAG, "SECURITY ALERT: Invalid signature from IP $fromIp claiming to be $fromHash")
                    return@launch
                }

                // 2. Обновляем информацию об узле (раз подпись верна - это настоящий пользователь)
                nodeDao.updateNetworkInfo(fromHash, fromIp, PORT, pubKey, System.currentTimeMillis())
                swarmPeers[fromHash] = fromIp
                
                // Сохраняем публичный ключ собеседника для шифрования будущих сообщений
                CryptoManager.savePeerPublicKey(fromHash, pubKey)

                // 3. Передаем payload в MessageRepository
                if (type == "CHAT" || type == "CHAT_FILE") {
                    messageRepository.handleIncoming(type, json.getString("data"), fromHash)
                }

                // 4. Уведомляем UI (опционально)
                listeners.forEach { it(type, json.getString("data"), fromIp, fromHash) }
                
            } catch (e: Exception) {
                Log.e(TAG, "Malformed UDP packet: ${e.message}")
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

            // Подписываем пакет своим приватным ключом
            val sig = CryptoManager.sign(json.toString().toByteArray())
            json.put("signature", Base64.encodeToString(sig, Base64.NO_WRAP))

            val bytes = json.toString().toByteArray()
            val addr = InetAddress.getByName(ip)
            
            DatagramSocket().use { 
                it.soTimeout = 2000 // Тайм-аут 2 секунды
                it.send(DatagramPacket(bytes, bytes.size, addr, PORT)) 
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "UDP Send failed to $ip: ${e.message}")
            false
        }
    }

    /* ======================= NSD & WI-FI ======================= */

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(s: NsdServiceInfo) { Log.d(TAG, "NSD Service Registered: ${s.serviceName}") }
        override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) { Log.e(TAG, "NSD Reg Failed: $e") }
        override fun onServiceUnregistered(s: NsdServiceInfo) {}
        override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) {}
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(s: NsdServiceInfo) {
            if (s.serviceType != SERVICE_TYPE) return
            
            // Игнорируем свой собственный сигнал
            if (s.serviceName.contains(getMyId().take(8))) return
            
            nsdManager.resolveService(s, object : NsdManager.ResolveListener {
                override fun onServiceResolved(r: NsdServiceInfo) {
                    val host = r.host?.hostAddress ?: return
                    val parts = r.serviceName.split("-")
                    // Формат имени: KakDela-HASH-UUID
                    if (parts.size >= 2) {
                        val peerHash = parts[1]
                        wifiPeers[peerHash] = host
                        Log.i(TAG, "NSD Found Peer: $peerHash at $host")
                    }
                }
                override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
            })
        }
        override fun onServiceLost(s: NsdServiceInfo) {
            // Удаляем из списка Wi-Fi, если сервис пропал
            wifiPeers.entries.removeIf { it.value == s.host?.hostAddress }
        }
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) {}
        override fun onStartDiscoveryFailed(t: String, e: Int) {}
        override fun onStopDiscoveryFailed(t: String, e: Int) {}
    }

    private fun registerInWifi() {
        try {
            val safeName = "KakDela-${getMyId().take(8)}-${UUID.randomUUID().toString().take(4)}"
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = safeName
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

    /* ======================= SMS FALLBACK ======================= */

    private fun sendAsSms(phone: String, message: String) {
        try {
            val sms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                context.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            
            sms.sendTextMessage(phone, null, "[P2P] $message", null, null)
            Log.i(TAG, "Message sent via SMS to $phone")
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
