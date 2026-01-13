package com.kakdela.p2p.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class IdentityRepository(private val context: Context) {

    private val TAG = "IdentityRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private val api = WebViewApiClient
    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val messageRepository by lazy { MessageRepository(context, db.messageDao(), this) }
    private val listeners = CopyOnWriteArrayList<(String, String, String, String) -> Unit>()

    val wifiPeers = ConcurrentHashMap<String, String>()
    val swarmPeers = ConcurrentHashMap<String, String>()

    private val SERVICE_TYPE = "_kakdela_p2p._udp."
    private val PORT = 8888
    private val PEPPER = "7fb8a1d2c3e4f5a6b7c8d9e0f1a2b3c4"
    private val SYNC_INTERVAL = 300_000L

    @Volatile
    private var isRunning = false
    private var udpSocket: DatagramSocket? = null

    /* ======================= PUBLIC ======================= */

    fun startNetwork() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                CryptoManager.init(context)
                val myId = getMyId()
                Log.i(TAG, "Network start. MyID=$myId")

                launch { startUdpListener() }
                launch { registerInWifi() }
                launch { discoverInWifi() }

                if (myId.isNotEmpty()) performServerSync(myId)
                startSyncLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Start error", e)
                isRunning = false
            }
        }
    }

    fun stopNetwork() {
        isRunning = false
        try {
            udpSocket?.close()
            nsdManager.unregisterService(registrationListener)
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (_: Exception) {}
        scope.coroutineContext.cancelChildren()
    }

    fun addListener(l: (String, String, String, String) -> Unit) = listeners.add(l)
    fun removeListener(l: (String, String, String, String) -> Unit) = listeners.remove(l)

    fun getMyId(): String {
        var id = prefs.getString("my_security_hash", "") ?: ""
        if (id.isEmpty()) {
            id = CryptoManager.getMyIdentityHash()
            if (id.isNotEmpty()) {
                prefs.edit().putString("my_security_hash", id).apply()
            }
        }
        return id
    }

    fun getPeerPublicKey(hash: String): String? =
        runBlocking(Dispatchers.IO) { nodeDao.getNodeByHash(hash)?.publicKey }

    fun generatePhoneDiscoveryHash(phone: String): String {
        val digits = phone.replace(Regex("[^0-9]"), "")
        return sha256(digits + PEPPER)
    }

    /* ======================= SERVER ======================= */

    private fun startSyncLoop() {
        scope.launch {
            while (isRunning) {
                delay(SYNC_INTERVAL)
                try {
                    val myId = getMyId()
                    if (myId.isNotEmpty()) performServerSync(myId)
                } catch (e: Exception) {
                    Log.e(TAG, "Sync error", e)
                }
            }
        }
    }

    private suspend fun performServerSync(myId: String) {
        val payload = UserPayload(
            hash = myId,
            phone_hash = prefs.getString("my_phone_hash", null),
            publicKey = CryptoManager.getMyPublicKeyStr(),
            ip = "0.0.0.0",
            port = PORT,
            phone = prefs.getString("my_phone", null) ?: "",
            lastSeen = System.currentTimeMillis()
        )
        announceMyself(payload)
    }

    private fun announceMyself(payload: UserPayload) {
        scope.launch {
            try {
                val response = api.announceSelf(payload)
                if (response.success) {
                    wifiPeers.values.forEach { sendUdp(it, "PRESENCE", "ONLINE") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Announce failed", e)
            }
        }
    }

    /* ======================= NODE MANAGEMENT ======================= */

    suspend fun addNodeByHash(hash: String): Boolean {
        return try {
            val existing = nodeDao.getNodeByHash(hash)
            if (existing != null) return true

            val nodes = fetchAllNodesFromServer()
            val node = nodes.find { it.hash == hash } ?: return false

            nodeDao.insert(
                NodeEntity(
                    userHash = node.hash,
                    ip = node.ip ?: "0.0.0.0",
                    port = node.port ?: PORT,
                    publicKey = node.publicKey ?: ""
                )
            )
            wifiPeers[node.hash] = node.ip ?: "0.0.0.0"
            true
        } catch (_: Exception) { false }
    }

    suspend fun fetchAllNodesFromServer(): List<UserPayload> {
        return try {
            val response = api.getAllNodes()
            if (response != null && response.success && response.data is List<*>) {
                response.data.filterIsInstance<UserPayload>()
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun sendMessageSmart(toHash: String, phone: String?, message: String): Boolean {
        return try {
            val ip = swarmPeers[toHash] ?: wifiPeers[toHash]
            if (ip != null) {
                sendUdp(ip, "CHAT", message)
            } else {
                val payload = UserPayload(
                    hash = toHash,
                    phone_hash = null,
                    publicKey = getPeerPublicKey(toHash),
                    ip = null,
                    port = null,
                    phone = phone ?: "",
                    lastSeen = System.currentTimeMillis()
                )
                api.announceSelf(payload)
                true
            }
        } catch (_: Exception) { false }
    }

    suspend fun sendSignaling(toHash: String, sdp: String) {
        val json = JSONObject().apply {
            put("type", "WEBRTC_SIGNAL")
            put("data", sdp)
        }
        sendMessageSmart(toHash, null, json.toString())
    }

    /* ======================= UDP ======================= */

    private fun startUdpListener() {
        scope.launch {
            try {
                DatagramSocket(null).use { socket ->
                    udpSocket = socket
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(PORT))

                    val buffer = ByteArray(65507)
                    while (isRunning) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        handleIncoming(String(packet.data, 0, packet.length), packet.address.hostAddress ?: "")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP error", e)
            } finally {
                udpSocket = null
            }
        }
    }

    private fun handleIncoming(raw: String, fromIp: String) {
        scope.launch {
            try {
                val json = JSONObject(raw)
                val type = json.getString("type")
                val fromHash = json.getString("from")
                val pubKey = json.getString("pubkey")
                val signature = Base64.decode(json.getString("signature"), Base64.NO_WRAP)

                val unsigned = JSONObject(raw).apply { remove("signature") }.toString()
                if (!CryptoManager.verify(signature, unsigned.toByteArray(), pubKey)) return@launch

                nodeDao.updateNetworkInfo(fromHash, fromIp, PORT, pubKey, System.currentTimeMillis())
                swarmPeers[fromHash] = fromIp
                CryptoManager.savePeerPublicKey(fromHash, pubKey)

                if (type.startsWith("CHAT") || type == "WEBRTC_SIGNAL") {
                    messageRepository.handleIncoming(type, json.getString("data"), fromHash)
                }

                withContext(Dispatchers.Main) {
                    listeners.forEach { it(type, json.getString("data"), fromIp, fromHash) }
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun sendUdp(ip: String, type: String, data: String): Boolean =
        withContext(Dispatchers.IO) {
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
                DatagramSocket().use { it.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(ip), PORT)) }
                true
            } catch (_: Exception) { false }
        }

    /* ======================= NSD ======================= */

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(s: NsdServiceInfo) {}
        override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) {}
        override fun onServiceUnregistered(s: NsdServiceInfo) {}
        override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) {}
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(s: NsdServiceInfo) {
            if (s.serviceType != SERVICE_TYPE) return
            if (s.serviceName.contains(getMyId().take(8))) return

            nsdManager.resolveService(s, object : NsdManager.ResolveListener {
                override fun onServiceResolved(r: NsdServiceInfo) {
                    val ip = r.host?.hostAddress ?: return
                    val parts = r.serviceName.split("-")
                    if (parts.size >= 2) wifiPeers[parts[1]] = ip
                }
                override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
            })
        }
        override fun onServiceLost(s: NsdServiceInfo) {}
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
        } catch (_: Exception) {}
    }

    private fun discoverInWifi() {
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (_: Exception) {}
    }

    /* ======================= SMS ======================= */

    private fun sendAsSms(phone: String, message: String) {
        try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                context.getSystemService(SmsManager::class.java)
            else SmsManager.getDefault()
            sms.sendTextMessage(phone, null, "[P2P] $message", null, null)
        } catch (_: Exception) {}
    }

    /* ======================= UTILS ======================= */

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun saveLocalAvatar(context: Context, uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                context.openFileOutput("my_avatar.jpg", Context.MODE_PRIVATE).use { input.copyTo(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Avatar error", e)
        }
    }

    fun getLocalAvatarUri(context: Context): Uri? {
        val file = context.getFileStreamPath("my_avatar.jpg")
        return if (file.exists()) Uri.fromFile(file) else null
    }
}
