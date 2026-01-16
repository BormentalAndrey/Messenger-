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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * IdentityRepository — центральный узел P2P логики.
 * Управляет идентификацией, обнаружением в Wi-Fi (NSD), маршрутизацией и синхронизацией.
 */
class IdentityRepository(private val context: Context) {

    private val TAG = "IdentityRepository"
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val api = WebViewApiClient
    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val messageRepository by lazy { MessageRepository(context, db.messageDao(), this) }

    private val listeners = CopyOnWriteArrayList<(String, String, String, String) -> Unit>()
    val wifiPeers = ConcurrentHashMap<String, String>()   // Hash -> Local IP
    val swarmPeers = ConcurrentHashMap<String, String>()  // Hash -> Last known IP

    private companion object {
        const val SERVICE_TYPE = "_kakdela_p2p._udp."
        const val PORT = 8888
        const val SYNC_INTERVAL = 300_000L
        const val PEPPER = "7fb8a1d2c3e4f5a6b7c8d9e0f1a2b3c4"
    }

    @Volatile
    private var isRunning = false
    private var udpSocket: DatagramSocket? = null
    private var networkScope: CoroutineScope? = null

    /* ======================= ЖИЗНЕННЫЙ ЦИКЛ СЕТИ ======================= */

    fun startNetwork() {
        if (isRunning) return
        isRunning = true
        networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        networkScope?.launch {
            try {
                CryptoManager.init(context)
                val myId = getMyId()
                Log.i(TAG, "Network starting for Node ID: ${myId.take(8)}")

                launch { startUdpListener() }
                launch { registerInWifi() }
                launch { discoverInWifi() }

                if (myId.isNotEmpty()) performServerSync(myId)
                startSyncLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Critical startNetwork failure", e)
                stopNetwork()
            }
        }
    }

    fun stopNetwork() {
        isRunning = false
        networkScope?.cancel()
        networkScope = null

        udpSocket?.close()
        udpSocket = null

        try { nsdManager.unregisterService(registrationListener) } catch (_: Exception) {}
        try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}

        Log.i(TAG, "Network services stopped")
    }

    /* ======================= ПРОФИЛЬ И ИДЕНТИЧНОСТЬ ======================= */

    fun getMyId(): String {
        var id = prefs.getString("my_security_hash", "") ?: ""
        if (id.isEmpty()) {
            id = CryptoManager.getMyIdentityHash()
            if (id.isNotEmpty()) prefs.edit().putString("my_security_hash", id).apply()
        }
        return id
    }

    fun getLocalAvatarUri(): String? = prefs.getString("local_avatar_uri", null)
    fun saveLocalAvatar(uri: String) = prefs.edit().putString("local_avatar_uri", uri).apply()

    suspend fun getCachedNode(hash: String): NodeEntity? =
        withContext(Dispatchers.IO) { nodeDao.getNodeByHash(hash) }

    suspend fun getPeerPublicKey(hash: String): String? =
        withContext(Dispatchers.IO) { nodeDao.getNodeByHash(hash)?.publicKey }

    /* ======================= УМНАЯ ОТПРАВКА ======================= */

    suspend fun sendMessageSmart(targetHash: String, phone: String?, message: String): Boolean =
        withContext(Dispatchers.IO) {
            val ip = wifiPeers[targetHash] ?: swarmPeers[targetHash] ?: getCachedNode(targetHash)?.ip
            var delivered = false

            if (!ip.isNullOrBlank() && ip != "0.0.0.0") {
                delivered = sendUdp(ip, "CHAT_MSG", message)
            }

            // ✅ Исправлено: if как блок, не выражение
            if (!delivered) {
                if (!phone.isNullOrBlank()) {
                    sendAsSms(phone, message)
                    delivered = true
                    Log.i(TAG, "P2P failed, message sent via SMS to $phone")
                }
            }

            delivered
        }

    /* ======================= СЕРВЕРНАЯ СИНХРОНИЗАЦИЯ ======================= */

    private fun startSyncLoop() = networkScope?.launch {
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
            }
        } catch (e: Exception) { Log.e(TAG, "Server sync failed", e) }
    }

    suspend fun fetchAllNodesFromServer(): List<UserPayload> = withContext(Dispatchers.IO) {
        try {
            val users = api.getAllNodes().users.orEmpty()
            if (users.isNotEmpty()) {
                nodeDao.upsertAll(users.map {
                    NodeEntity(
                        userHash = it.hash,
                        phone_hash = it.phone_hash ?: "",
                        ip = it.ip ?: "0.0.0.0",
                        port = it.port ?: PORT,
                        publicKey = it.publicKey ?: "",
                        phone = it.phone ?: "",
                        lastSeen = it.lastSeen ?: System.currentTimeMillis()
                    )
                })
            }
            users
        } catch (e: Exception) {
            Log.w(TAG, "API error, fallback to local DB")
            nodeDao.getAllNodes().map { entity ->
                UserPayload(
                    hash = entity.userHash,
                    phone_hash = entity.phone_hash,
                    ip = entity.ip,
                    port = entity.port,
                    publicKey = entity.publicKey,
                    phone = entity.phone,
                    lastSeen = entity.lastSeen
                )
            }
        }
    }

    suspend fun addNodeByHash(hash: String): Boolean = withContext(Dispatchers.IO) {
        val node = fetchAllNodesFromServer().find { it.hash == hash }
        if (node != null) {
            saveNodeToDb(node)
            true
        } else {
            false
        }
    }

    private suspend fun saveNodeToDb(node: UserPayload) = nodeDao.upsert(
        NodeEntity(
            userHash = node.hash,
            phone_hash = node.phone_hash ?: "",
            ip = node.ip ?: "0.0.0.0",
            port = node.port ?: PORT,
            publicKey = node.publicKey ?: "",
            phone = node.phone ?: "",
            lastSeen = node.lastSeen ?: System.currentTimeMillis()
        )
    )

    /* ======================= UDP ТРАНСПОРТ ======================= */

    private fun startUdpListener() = networkScope?.launch {
        try {
            udpSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
            }
            val buffer = ByteArray(65507)
            Log.i(TAG, "UDP Socket listening on $PORT")

            while (isRunning) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    udpSocket?.receive(packet)
                } catch (e: Exception) {
                    if (!isRunning) break else throw e
                }

                val fromIp = packet.address?.hostAddress ?: continue
                val rawString = String(packet.data, 0, packet.length, Charsets.UTF_8)
                handleIncoming(rawString, fromIp)
            }
        } catch (e: Exception) {
            if (isRunning) Log.e(TAG, "UDP listener error", e)
        } finally {
            udpSocket?.close()
        }
    }

    private fun handleIncoming(raw: String, fromIp: String) = networkScope?.launch(Dispatchers.Default) {
        try {
            val json = JSONObject(raw)
            val type = json.getString("type")
            val fromHash = json.getString("from")
            val pubKey = json.getString("pubkey")
            val timestamp = json.getLong("timestamp")
            val data = json.getString("data")
            val signature = Base64.decode(json.getString("signature"), Base64.NO_WRAP)

            if (!CryptoManager.verify(signature, (data + timestamp).toByteArray(), pubKey)) {
                Log.w(TAG, "SECURITY ALERT: Signature mismatch from $fromIp")
                return@launch
            }

            CryptoManager.savePeerPublicKey(fromHash, pubKey)
            nodeDao.updateNetworkInfo(fromHash, fromIp, PORT, pubKey, System.currentTimeMillis())
            swarmPeers[fromHash] = fromIp

            if (type.startsWith("CHAT")) messageRepository.handleIncoming(type, data, fromHash)
            listeners.forEach { it(type, data, fromIp, fromHash) }
        } catch (e: Exception) { Log.e(TAG, "Malformed packet: ${e.message}") }
    }

    suspend fun sendUdp(ip: String, type: String, data: String): Boolean = withContext(Dispatchers.IO) {
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

            DatagramSocket().use { it.send(DatagramPacket(bytes, bytes.size, address, PORT)) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "UDP Send error to $ip: ${e.message}")
            false
        }
    }

    fun sendSignaling(targetIp: String, type: String, data: String) =
        networkScope?.launch { sendUdp(targetIp, type, data) }

    /* ======================= WI-FI NSD ======================= */

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(s: NsdServiceInfo) { Log.i(TAG, "NSD Service Registered") }
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
        override fun onServiceLost(s: NsdServiceInfo) {
            val hostAddress = s.host?.hostAddress
            if (hostAddress != null) wifiPeers.values.removeAll { it == hostAddress }
        }
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
        catch (e: Exception) { Log.e(TAG, "NSD Registration error", e) }
    }

    private fun discoverInWifi() {
        try { nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
        catch (e: Exception) { Log.e(TAG, "NSD Discovery error", e) }
    }

    /* ======================= УТИЛИТЫ ======================= */

    private fun sendAsSms(phone: String, message: String) {
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= 31)
                context.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            smsManager?.sendTextMessage(phone, null, "[P2P] $message", null, null)
        } catch (e: Exception) { Log.e(TAG, "SMS failed", e) }
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
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

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
