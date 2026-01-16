package com.kakdela.p2p.data

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.api.WebViewApiClient
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
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * IdentityRepository — центральный узел сетевой логики.
 * Управляет идентификацией, P2P поиском в Wi-Fi (NSD), синхронизацией с сервером
 * и маршрутизацией сообщений (UDP/SMS).
 */
class IdentityRepository(private val context: Context) {

    private val TAG = "IdentityRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private val api = WebViewApiClient
    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    // Lazy инициализация репозитория сообщений
    private val messageRepository by lazy {
        MessageRepository(context, db.messageDao(), this)
    }

    // Слушатели для UI и других сервисов
    private val listeners = CopyOnWriteArrayList<(String, String, String, String) -> Unit>()

    // Хранение IP-адресов пиров
    val wifiPeers = ConcurrentHashMap<String, String>()   // NSD Wi-Fi
    val swarmPeers = ConcurrentHashMap<String, String>()  // UDP глобальные

    private companion object {
        const val SERVICE_TYPE = "_kakdela_p2p._udp."
        const val PORT = 8888
        const val SYNC_INTERVAL = 300_000L // 5 минут
        const val PEPPER = "7fb8a1d2c3e4f5a6b7c8d9e0f1a2b3c4"
    }

    @Volatile
    private var isRunning = false
    private var udpSocket: DatagramSocket? = null

    /* ============================================================
       Жизненный цикл сети
       ============================================================ */

    fun startNetwork() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                CryptoManager.init(context)
                val myId = getMyId()
                Log.i(TAG, "Network starting for Node ID: ${myId.take(8)}")

                // Запуск служб
                launch { startUdpListener() }
                launch { registerInWifi() }
                launch { discoverInWifi() }

                if (myId.isNotEmpty()) performServerSync(myId)
                startSyncLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Critical startNetwork failure", e)
                isRunning = false
            }
        }
    }

    fun stopNetwork() {
        isRunning = false
        udpSocket?.close()
        udpSocket = null

        try { nsdManager.unregisterService(registrationListener) } catch (_: Exception) {}
        try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
        scope.coroutineContext.cancelChildren()
        Log.i(TAG, "Network services stopped")
    }

    /* ============================================================
       Идентификация
       ============================================================ */

    fun getMyId(): String {
        var id = prefs.getString("my_security_hash", "") ?: ""
        if (id.isEmpty()) {
            id = CryptoManager.getMyIdentityHash()
            if (id.isNotEmpty()) prefs.edit().putString("my_security_hash", id).apply()
        }
        return id
    }

    suspend fun getCachedNode(hash: String): NodeEntity? =
        withContext(Dispatchers.IO) { nodeDao.getNodeByHash(hash) }

    suspend fun getPeerPublicKey(hash: String): String? =
        withContext(Dispatchers.IO) { nodeDao.getNodeByHash(hash)?.publicKey }

    /* ============================================================
       Серверная синхронизация (DHT Lite)
       ============================================================ */

    private fun startSyncLoop() = scope.launch {
        while (isRunning) {
            try {
                val myId = getMyId()
                if (myId.isNotEmpty()) performServerSync(myId)
            } catch (e: Exception) {
                Log.e(TAG, "Sync loop error", e)
            }
            delay(SYNC_INTERVAL)
        }
    }

    private suspend fun performServerSync(myId: String) = withContext(Dispatchers.IO) {
        try {
            val phone = prefs.getString("my_phone", null)
            val phoneHash = prefs.getString("my_phone_hash", null)
                ?: generatePhoneDiscoveryHash(phone ?: "").also {
                    prefs.edit().putString("my_phone_hash", it).apply()
                }

            val currentIp = getLocalIpAddress() ?: "0.0.0.0"

            val payload = UserPayload(
                hash = myId,
                phone_hash = phoneHash,
                ip = currentIp,
                port = PORT,
                publicKey = CryptoManager.getMyPublicKeyStr(),
                phone = phone,
                lastSeen = System.currentTimeMillis()
            )

            val response = api.announceSelf(payload)
            if (response.success) {
                saveNodeToDb(payload)
                fetchAllNodesFromServer()
                Log.d(TAG, "Server sync successful for $currentIp")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server sync failed", e)
        }
    }

    suspend fun fetchAllNodesFromServer(): List<UserPayload> = withContext(Dispatchers.IO) {
        try {
            val users = api.getAllNodes().users.orEmpty()
            if (users.isNotEmpty()) {
                nodeDao.upsertAll(users.map {
                    NodeEntity(
                        it.hash,
                        it.phone_hash ?: "",
                        it.ip ?: "0.0.0.0",
                        it.port,
                        it.publicKey,
                        it.phone ?: "",
                        it.lastSeen ?: System.currentTimeMillis()
                    )
                })
            }
            users
        } catch (e: Exception) {
            Log.w(TAG, "Fetch users from server failed, using local DB")
            nodeDao.getAllNodes().map {
                UserPayload(it.userHash, it.phone_hash, it.ip, it.port, it.publicKey, it.phone, it.lastSeen)
            }
        }
    }

    private suspend fun saveNodeToDb(node: UserPayload) {
        nodeDao.upsert(
            NodeEntity(
                node.hash,
                node.phone_hash ?: "",
                node.ip ?: "0.0.0.0",
                node.port,
                node.publicKey,
                node.phone ?: "",
                node.lastSeen ?: System.currentTimeMillis()
            )
        )
    }

    /* ============================================================
       Умная отправка (P2P → SMS)
       ============================================================ */

    suspend fun sendMessageSmart(targetHash: String, phone: String?, message: String): Boolean =
        withContext(Dispatchers.IO) {
            val ip = wifiPeers[targetHash] ?: swarmPeers[targetHash] ?: getCachedNode(targetHash)?.ip
            var delivered = false

            if (!ip.isNullOrBlank() && ip != "0.0.0.0") delivered = sendUdp(ip, "CHAT", message)
            if (!delivered && !phone.isNullOrBlank()) {
                sendAsSms(phone, message)
                delivered = true
                Log.i(TAG, "P2P failed, sent via SMS to $phone")
            }

            delivered
        }

    /* ============================================================
       UDP Listener & Sender
       ============================================================ */

    private fun startUdpListener() = scope.launch {
        try {
            udpSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
            }

            val buffer = ByteArray(65507)
            Log.i(TAG, "UDP Socket listening on $PORT")

            while (isRunning) {
                val packet = DatagramPacket(buffer, buffer.size)
                udpSocket?.receive(packet) ?: break
                val fromIp = packet.address.hostAddress ?: continue
                val rawString = String(packet.data, 0, packet.length, Charsets.UTF_8)
                handleIncoming(rawString, fromIp)
            }
        } catch (e: Exception) {
            if (isRunning) Log.e(TAG, "UDP listener error", e)
        } finally { udpSocket?.close() }
    }

    private fun handleIncoming(raw: String, fromIp: String) {
        scope.launch(Dispatchers.Default) {
            try {
                val json = JSONObject(raw)
                val type = json.getString("type")
                val fromHash = json.getString("from")
                val pubKey = json.getString("pubkey")
                val timestamp = json.getLong("timestamp")
                val data = json.getString("data")
                val signatureStr = json.getString("signature")

                val signature = Base64.decode(signatureStr, Base64.NO_WRAP)
                if (!CryptoManager.verify(signature, (data + timestamp).toByteArray(), pubKey)) {
                    Log.w(TAG, "SECURITY ALERT: Invalid signature from $fromIp")
                    return@launch
                }

                CryptoManager.savePeerPublicKey(fromHash, pubKey)
                nodeDao.updateNetworkInfo(fromHash, fromIp, PORT, pubKey, System.currentTimeMillis())
                swarmPeers[fromHash] = fromIp

                if (type.startsWith("CHAT")) messageRepository.handleIncoming(type, data, fromHash)
                listeners.forEach { it(type, data, fromIp, fromHash) }

            } catch (e: Exception) {
                Log.e(TAG, "Malformed UDP packet received", e)
            }
        }
    }

    private suspend fun sendUdp(ip: String, type: String, data: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val signature = CryptoManager.sign((data + timestamp).toByteArray())

            val json = JSONObject().apply {
                put("type", type)
                put("data", data)
                put("from", getMyId())
                put("pubkey", CryptoManager.getMyPublicKeyStr())
                put("timestamp", timestamp)
                put("signature", Base64.encodeToString(signature, Base64.NO_WRAP))
            }

            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            val address = InetAddress.getByName(ip)

            DatagramSocket().use { socket ->
                socket.send(DatagramPacket(bytes, bytes.size, address, PORT))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "UDP Send error to $ip: ${e.message}")
            false
        }
    }

    /* ============================================================
       NSD Wi-Fi Discovery
       ============================================================ */

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(s: NsdServiceInfo) { Log.i(TAG, "NSD Service Registered: ${s.serviceName}") }
        override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) { Log.e(TAG, "NSD Reg failed: $e") }
        override fun onServiceUnregistered(s: NsdServiceInfo) {}
        override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) {}
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(s: NsdServiceInfo) {
            if (s.serviceType != SERVICE_TYPE) return
            nsdManager.resolveService(s, object : NsdManager.ResolveListener {
                override fun onServiceResolved(r: NsdServiceInfo) {
                    val host = r.host?.hostAddress ?: return
                    val peerHash = r.serviceName.split("-").getOrNull(1) ?: return
                    if (peerHash != getMyId()) wifiPeers[peerHash] = host
                }
                override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
            })
        }
        override fun onServiceLost(s: NsdServiceInfo) { wifiPeers.values.remove(s.host?.hostAddress) }
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) {}
        override fun onStartDiscoveryFailed(t: String, e: Int) {}
        override fun onStopDiscoveryFailed(t: String, e: Int) {}
    }

    private fun registerInWifi() {
        val id = getMyId()
        if (id.isEmpty()) return
        val info = NsdServiceInfo().apply {
            serviceName = "KakDela-${id.take(8)}"
            serviceType = SERVICE_TYPE
            port = PORT
        }
        try { nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener) }
        catch (e: Exception) { Log.e(TAG, "NSD registration start failed", e) }
    }

    private fun discoverInWifi() {
        try { nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
        catch (e: Exception) { Log.e(TAG, "NSD discovery start failed", e) }
    }

    /* ============================================================
       SMS & Utils
       ============================================================ */

    private fun sendAsSms(phone: String, message: String) {
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(SmsManager::class.java)
            } else @Suppress("DEPRECATION") SmsManager.getDefault()
            smsManager?.sendTextMessage(phone, null, "[P2P] $message", null, null)
        } catch (e: Exception) {
            Log.e(TAG, "SMS delivery failed", e)
        }
    }

    fun generatePhoneDiscoveryHash(phone: String): String {
        val digits = phone.replace(Regex("[^0-9]"), "")
        val normalized = when {
            digits.length == 10 && digits.startsWith("9") -> "7$digits"
            digits.length == 11 && digits.startsWith("8") -> "7${digits.substring(1)}"
            else -> digits
        }
        return sha256(normalized + PEPPER)
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun getLocalIpAddress(): String? =
        try {
            java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress.indexOf(':') < 0 }
                ?.hostAddress
        } catch (_: Exception) { null }

    fun addListener(l: (String, String, String, String) -> Unit) = listeners.add(l)
    fun removeListener(l: (String, String, String, String) -> Unit) = listeners.remove(l)
}
