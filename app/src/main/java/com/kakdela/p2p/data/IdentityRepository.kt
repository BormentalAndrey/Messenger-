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
    
    // SupervisorJob гарантирует, что крэш в одной сетевой задаче не остановит весь стек
    private val repositoryJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + repositoryJob)
    
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    // Потокобезопасные структуры данных для работы из разных CoroutineContext
    private val listeners = CopyOnWriteArrayList<(type: String, data: String, fromIp: String, fromHash: String) -> Unit>()
    private val wifiPeers = ConcurrentHashMap<String, String>()
    private val swarmPeers = ConcurrentHashMap<String, String>()

    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()
    private val api = MyServerApiFactory.instance

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val SERVICE_TYPE = "_kakdela_p2p._udp"
    private val PORT = 8888
    private val PEPPER = "7fb8a1d2c3e4f5a6"

    private var socket: DatagramSocket? = null
    private var isListening = false

    // ---------- СИСТЕМНЫЕ МЕТОДЫ ----------

    fun startNetwork() {
        if (isListening) return
        scope.launch {
            try {
                CryptoManager.init(context)
                startListening()
                registerInWifi()
                discoverInWifi()
                Log.i(TAG, "P2P Network Engine Started")
            } catch (e: Exception) {
                Log.e(TAG, "Startup Error: ${e.message}")
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
            Log.w(TAG, "Clean stop warning: ${e.message}")
        }
        repositoryJob.cancelChildren() // Остановка всех фоновых задач без отмены самого Job
    }

    fun addListener(l: (String, String, String, String) -> Unit) = listeners.add(l)
    fun removeListener(l: (String, String, String, String) -> Unit) = listeners.remove(l)

    fun getMyId(): String = prefs.getString("my_security_hash", "") ?: ""

    // ---------- ВНЕШНЕЕ ВЗАИМОДЕЙСТВИЕ ----------

    fun announceMyself(wrapper: UserRegistrationWrapper) {
        scope.launch {
            try {
                // Пытаемся синхронизироваться с центральным узлом для глобальной видимости
                api.registerUser(wrapper)
                
                // Рассылаем статус ONLINE всем узлам в локальной сети
                wifiPeers.values.forEach { ip ->
                    sendUdp(ip, "PRESENCE", "ONLINE")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Announce error: ${e.message}")
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
            Log.e(TAG, "Cloud sync failed, using offline cache")
            nodeDao.getAllNodes().map {
                UserPayload(it.userHash, it.phone_hash, it.ip, it.port, it.publicKey, it.phone, it.email, it.lastSeen)
            }
        }
    }

    fun sendMessageSmart(targetHash: String, targetPhone: String?, message: String) = scope.launch {
        // Умная маршрутизация: LAN -> WAN Cache -> Server Registry -> SMS Fallback
        val targetIp = wifiPeers[targetHash] 
            ?: swarmPeers[targetHash] 
            ?: findPeerOnServer(targetHash)?.ip

        var success = false
        if (!targetIp.isNullOrBlank() && targetIp != "0.0.0.0") {
            success = sendUdp(targetIp, "CHAT", message)
        }

        if (!success && !targetPhone.isNullOrBlank()) {
            sendAsSms(targetPhone, message)
        }
    }

    private suspend fun findPeerOnServer(hash: String): UserPayload? {
        val cached = nodeDao.getNodeByHash(hash)
        // Если кэш свежее 3 минут, используем его
        if (cached != null && (System.currentTimeMillis() - cached.lastSeen) < 180_000) {
            return UserPayload(cached.userHash, cached.phone_hash, cached.ip, cached.port, cached.publicKey, cached.phone, cached.email, cached.lastSeen)
        }
        return fetchAllNodesFromServer().find { it.hash == hash }
    }

    // ---------- UDP СТЕК ----------

    private fun startListening() {
        isListening = true
        scope.launch {
            try {
                socket = DatagramSocket(PORT).apply { reuseAddress = true }
                val buffer = ByteArray(16384)
                
                while (isListening && !socket!!.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    
                    val ip = packet.address.hostAddress ?: ""
                    val data = String(packet.data, 0, packet.length)
                    
                    // Обработка каждого пакета в отдельной корутине Default (CPU intensive)
                    launch(Dispatchers.Default) { handleIncoming(data, ip) }
                }
            } catch (e: Exception) {
                if (isListening) Log.e(TAG, "Socket error: ${e.message}")
                isListening = false
            }
        }
    }

    private suspend fun handleIncoming(raw: String, fromIp: String) {
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
                    val signature = Base64.decode(json.getString("signature"), Base64.NO_WRAP)
                    
                    // Проверка целостности и авторства сообщения
                    val unsignedData = JSONObject(raw).apply { remove("signature") }.toString()
                    
                    if (CryptoManager.verify(signature, unsignedData.toByteArray(), pubKey)) {
                        CryptoManager.savePeerPublicKey(fromHash, pubKey)
                        nodeDao.updateNetworkInfo(fromHash, fromIp, PORT, pubKey, System.currentTimeMillis())
                        
                        listeners.forEach { it(type, json.getString("data"), fromIp, fromHash) }
                    } else {
                        Log.w(TAG, "Signature mismatch from $fromHash")
                    }
                }
                // ИСПРАВЛЕНО: Добавлена ветка else для исчерпывающего when
                else -> {
                    Log.v(TAG, "Received unhandled packet type: $type")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Parser error: ${e.message}")
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
            
            // Подпись пакета приватным ключом
            val sig = CryptoManager.sign(json.toString().toByteArray())
            json.put("signature", Base64.encodeToString(sig, Base64.NO_WRAP))

            val bytes = json.toString().toByteArray()
            val address = InetAddress.getByName(ip)
            
            DatagramSocket().use { it.send(DatagramPacket(bytes, bytes.size, address, PORT)) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "UDP Error to $ip: ${e.message}")
            false
        }
    }

    // ---------- NSD / LOCAL DISCOVERY ----------

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) { Log.d(TAG, "Local Service Live") }
        override fun onRegistrationFailed(info: NsdServiceInfo, err: Int) {}
        override fun onServiceUnregistered(info: NsdServiceInfo) {}
        override fun onUnregistrationFailed(info: NsdServiceInfo, err: Int) {}
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(service: NsdServiceInfo) {
            if (service.serviceType == SERVICE_TYPE && !service.serviceName.contains(getMyId().take(6))) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        val peerHash = resolved.serviceName.substringAfter("KakDela-").substringBefore("-")
                        wifiPeers[peerHash] = host
                    }
                    override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
                })
            }
        }
        override fun onServiceLost(s: NsdServiceInfo) {
            wifiPeers.values.remove(s.host?.hostAddress)
        }
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) {}
        override fun onStartDiscoveryFailed(t: String, e: Int) {}
        override fun onStopDiscoveryFailed(t: String, e: Int) {}
    }

    private fun registerInWifi() {
        val info = NsdServiceInfo().apply {
            serviceName = "KakDela-${getMyId().take(8)}-${UUID.randomUUID().toString().take(4)}"
            serviceType = SERVICE_TYPE
            port = PORT
        }
        try { nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener) } catch (e: Exception) {}
    }

    private fun discoverInWifi() {
        try { nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) } catch (e: Exception) {}
    }

    // ---------- БЕЗОПАСНОСТЬ И ТЕЛЕФОНИЯ ----------

    fun generatePhoneDiscoveryHash(phone: String): String {
        val normalized = phone.replace(Regex("[^0-9]"), "").let {
            if (it.startsWith("8")) "7" + it.substring(1) else it
        }
        return sha256(normalized + PEPPER)
    }

    private fun sendAsSms(phone: String, message: String) {
        try {
            val sms: SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            sms.sendTextMessage(phone, null, "[P2P] $message", null, null)
        } catch (e: Exception) {
            Log.e(TAG, "SMS Failure")
        }
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
