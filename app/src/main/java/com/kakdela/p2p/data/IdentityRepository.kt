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
 * Core P2P Repository.
 * Handles Wi-Fi Discovery (NSD), Server Sync (Anti-Bot bypass), and UDP Messaging.
 */
class IdentityRepository(private val context: Context) {

    private val TAG = "IdentityRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private val api = MyServerApiFactory.instance
    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val messageRepository by lazy { 
        MessageRepository(context, db.messageDao(), this) 
    }

    private val listeners = CopyOnWriteArrayList<(String, String, String, String) -> Unit>()
    
    val wifiPeers = ConcurrentHashMap<String, String>()
    val swarmPeers = ConcurrentHashMap<String, String>()

    private val SERVICE_TYPE = "_kakdela_p2p._udp."
    private val PORT = 8888
    private val PEPPER = "7fb8a1d2c3e4f5a6b7c8d9e0f1a2b3c4"
    private val SYNC_INTERVAL = 300_000L 

    @Volatile
    private var isRunning = false

    /* ======================= PUBLIC API ======================= */

    fun startNetwork() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                CryptoManager.init(context)
                val myId = getMyId()
                Log.i(TAG, "Network starting. Local Node ID: $myId")

                launch { startUdpListener() }
                launch { registerInWifi() }
                launch { discoverInWifi() }
                
                if (myId.isNotEmpty()) {
                    performServerSync(myId)
                }

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

    fun getMyId(): String {
        var id = prefs.getString("my_security_hash", "") ?: ""
        if (id.isEmpty()) {
            id = CryptoManager.getMyIdentityHash()
            if (id.isNotEmpty()) {
                prefs.edit().putString("my_security_hash", id).apply()
                Log.i(TAG, "New Identity generated: $id")
            }
        }
        return id
    }

    /**
     * MANUAL ADD NODE FEATURE
     * Used when auto-discovery fails. Forces a server lookup for a specific hash.
     */
    suspend fun addNodeByHash(targetHash: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Force fetch latest list from server
            val allNodes = fetchAllNodesFromServer()
            
            // Look for specific hash
            val foundNode = allNodes.find { it.hash == targetHash }

            if (foundNode != null) {
                // Force save to local DB
                nodeDao.insert(NodeEntity(
                    userHash = foundNode.hash,
                    phone_hash = foundNode.phone_hash ?: "",
                    ip = foundNode.ip ?: "0.0.0.0",
                    port = foundNode.port,
                    publicKey = foundNode.publicKey,
                    phone = foundNode.phone ?: "",
                    lastSeen = System.currentTimeMillis()
                ))
                Log.i(TAG, "Manual Node Add Success: $targetHash")
                return@withContext true
            } else {
                Log.w(TAG, "Manual Node Add Failed: Hash not found on server")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Manual Add Error: ${e.message}")
            false
        }
    }

    /* ======================= SERVER & SYNC ======================= */

    private fun startSyncLoop() {
        scope.launch {
            while (isRunning) {
                try {
                    val myId = getMyId()
                    if (myId.isNotEmpty()) performServerSync(myId)
                } catch (e: Exception) {
                    Log.e(TAG, "Sync loop failed: ${e.message}")
                }
                delay(SYNC_INTERVAL)
            }
        }
    }

    private suspend fun performServerSync(myId: String) = withContext(Dispatchers.IO) {
        try {
            val myPayload = UserPayload(
                hash = myId,
                phone_hash = prefs.getString("my_phone_hash", null),
                ip = null, 
                port = PORT,
                publicKey = CryptoManager.getMyPublicKeyStr(),
                phone = prefs.getString("my_phone", null),
                email = prefs.getString("my_email", null),
                lastSeen = System.currentTimeMillis()
            )
            
            announceMyself(myPayload)
            fetchAllNodesFromServer()
        } catch (e: Exception) {
            Log.e(TAG, "Server sync error: ${e.message}")
        }
    }

    suspend fun fetchAllNodesFromServer(): List<UserPayload> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAllNodes()
            val users = response.users.orEmpty()
            
            if (users.isNotEmpty()) {
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
            Log.e(TAG, "Fetch failed, using cache. Error: ${e.message}")
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
                    wifiPeers.values.forEach { ip -> sendUdp(ip, "PRESENCE", "ONLINE") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Announce Error: ${e.message}")
            }
        }
    }

    /* ======================= MESSAGING ======================= */

    fun sendMessageSmart(targetHash: String, phone: String?, message: String): Boolean {
        var delivered = false
        runBlocking(Dispatchers.IO) { 
            var ip = wifiPeers[targetHash] 
            if (ip == null) ip = swarmPeers[targetHash]
            if (ip == null) ip = findPeerOnServer(targetHash)?.ip

            if (!ip.isNullOrBlank() && ip != "0.0.0.0") {
                delivered = sendUdp(ip, "CHAT", message)
            }

            if (!delivered && !phone.isNullOrBlank()) {
                sendAsSms(phone, message)
                delivered = true 
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
                DatagramSocket(null).use { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(PORT))
                    val buffer = ByteArray(65507) 
                    
                    while (isRunning) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)
                            val data = String(packet.data, 0, packet.length)
                            handleIncoming(data, packet.address.hostAddress ?: "")
                        } catch (e: Exception) {
                            if (isRunning) Log.e(TAG, "Packet receive error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "UDP Bind failed: ${e.message}") }
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
                
                val signature = Base64.decode(sigBase64, Base64.NO_WRAP)
                val unsignedJson = JSONObject(raw).apply { remove("signature") }.toString()

                if (!CryptoManager.verify(signature, unsignedJson.toByteArray(), pubKey)) {
                    return@launch
                }

                nodeDao.updateNetworkInfo(fromHash, fromIp, PORT, pubKey, System.currentTimeMillis())
                swarmPeers[fromHash] = fromIp
                CryptoManager.savePeerPublicKey(fromHash, pubKey)

                if (type == "CHAT" || type == "CHAT_FILE") {
                    messageRepository.handleIncoming(type, json.getString("data"), fromHash)
                }
                listeners.forEach { it(type, json.getString("data"), fromIp, fromHash) }
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
                it.soTimeout = 2000 
                it.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(ip), PORT)) 
            }
            true
        } catch (e: Exception) { false }
    }

    // NSD Logic remains same...
    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(s: NsdServiceInfo) {}
        override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) {}
        override fun onServiceUnregistered(s: NsdServiceInfo) {}
        override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) {}
    }
    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(s: NsdServiceInfo) {
            if (s.serviceType != SERVICE_TYPE || s.serviceName.contains(getMyId().take(8))) return
            nsdManager.resolveService(s, object : NsdManager.ResolveListener {
                override fun onServiceResolved(r: NsdServiceInfo) {
                    val host = r.host?.hostAddress ?: return
                    val parts = r.serviceName.split("-")
                    if (parts.size >= 2) wifiPeers[parts[1]] = host
                }
                override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
            })
        }
        override fun onServiceLost(s: NsdServiceInfo) { wifiPeers.entries.removeIf { it.value == s.host?.hostAddress } }
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) {}
        override fun onStartDiscoveryFailed(t: String, e: Int) {}
        override fun onStopDiscoveryFailed(t: String, e: Int) {}
    }
    private fun registerInWifi() {
        try {
            val safeName = "KakDela-${getMyId().take(8)}-${UUID.randomUUID().toString().take(4)}"
            val serviceInfo = NsdServiceInfo().apply { serviceName = safeName; serviceType = SERVICE_TYPE; port = PORT }
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (_: Exception) {}
    }
    private fun discoverInWifi() {
        try { nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) } catch (_: Exception) {}
    }

    private fun sendAsSms(phone: String, message: String) {
        try {
            val sms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.sendTextMessage(phone, null, "[P2P] $message", null, null)
        } catch (_: Exception) {}
    }

    fun generatePhoneDiscoveryHash(phone: String): String {
        val digits = phone.replace(Regex("[^0-9]"), "")
        val normalized = if (digits.length == 11 && digits.startsWith("8")) "7${digits.substring(1)}" else digits
        return sha256(normalized + PEPPER)
    }

    private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
