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
import java.net.*
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
    private val nsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val messageRepository by lazy {
        MessageRepository(context, db.messageDao(), this)
    }

    private val listeners =
        CopyOnWriteArrayList<(String, String, String, String) -> Unit>()

    val wifiPeers = ConcurrentHashMap<String, String>()
    val swarmPeers = ConcurrentHashMap<String, String>()

    private val SERVICE_TYPE = "_kakdela_p2p._udp."
    private val PORT = 8888
    private val PEPPER = "7fb8a1d2c3e4f5a6b7c8d9e0f1a2b3c4"
    private val SYNC_INTERVAL = 120_000L

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

                launch { startUdpListener() }
                launch { registerInWifi() }
                launch { discoverInWifi() }

                val myId = getMyId()
                if (myId.isNotEmpty()) {
                    performServerSync(myId)
                    startSyncLoop()
                }
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

    fun getCurrentIp(): String {
        try {
            NetworkInterface.getNetworkInterfaces().toList().forEach { intf ->
                intf.inetAddresses.toList().forEach { addr ->
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                        return addr.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "IP error", e)
        }
        return "0.0.0.0"
    }

    fun generatePhoneDiscoveryHash(phone: String): String =
        sha256(phone.filter { it.isDigit() } + PEPPER)

    suspend fun getPeerPublicKey(hash: String): String? =
        nodeDao.getNodeByHash(hash)?.publicKey

    /* ======================= SERVER ======================= */

    private fun startSyncLoop() {
        scope.launch {
            while (isRunning) {
                delay(SYNC_INTERVAL)
                getMyId().takeIf { it.isNotEmpty() }?.let {
                    performServerSync(it)
                }
            }
        }
    }

    private suspend fun performServerSync(myId: String) {
        try {
            val payload = UserPayload(
                hash = myId,
                phone_hash = prefs.getString("my_phone_hash", "") ?: "",
                publicKey = CryptoManager.getMyPublicKeyStr(),
                ip = getCurrentIp(),
                port = PORT,
                phone = prefs.getString("my_phone", "") ?: "",
                lastSeen = System.currentTimeMillis()
            )

            val response = api.announceSelf(payload)
            if (response.success) {
                syncLocalNodesWithServer()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server sync error", e)
        }
    }

    private suspend fun syncLocalNodesWithServer() {
        val response = api.getAllNodes()
        if (response.success) {
            response.users?.forEach {
                if (it.hash != getMyId()) {
                    nodeDao.insert(
                        NodeEntity(
                            userHash = it.hash,
                            phone_hash = it.phone_hash ?: "",
                            ip = it.ip ?: "0.0.0.0",
                            port = it.port ?: PORT,
                            publicKey = it.publicKey ?: "",
                            lastSeen = it.lastSeen ?: System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    /* ======================= MESSAGING ======================= */

    suspend fun sendMessageSmart(
        toHash: String,
        phone: String?,
        message: String
    ): Boolean {
        val ip = swarmPeers[toHash] ?: wifiPeers[toHash]
        return if (ip != null) {
            sendUdp(ip, "CHAT", message)
        } else false
    }

    suspend fun sendSignaling(toHash: String, sdp: String) {
        val json = JSONObject()
            .put("type", "WEBRTC_SIGNAL")
            .put("data", sdp)
        sendMessageSmart(toHash, null, json.toString())
    }

    /* ======================= UDP ======================= */

    private fun startUdpListener() {
        scope.launch {
            try {
                DatagramSocket(PORT).use { socket ->
                    udpSocket = socket
                    val buf = ByteArray(65507)
                    while (isRunning) {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        handleIncoming(
                            String(packet.data, 0, packet.length),
                            packet.address.hostAddress ?: ""
                        )
                    }
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "UDP error", e)
            }
        }
    }

    private fun handleIncoming(raw: String, fromIp: String) {
        scope.launch {
            try {
                val json = JSONObject(raw)
                val signature = Base64.decode(json.getString("signature"), Base64.NO_WRAP)
                val unsigned = JSONObject(raw).apply { remove("signature") }.toString()

                val pubKey = json.getString("pubkey")
                if (!CryptoManager.verify(signature, unsigned.toByteArray(), pubKey)) return@launch

                val type = json.getString("type")
                val data = json.getString("data")
                val fromHash = json.getString("from")

                swarmPeers[fromHash] = fromIp
                nodeDao.updateNetworkInfo(fromHash, fromIp, PORT, pubKey, System.currentTimeMillis())

                if (type == "CHAT" || type == "WEBRTC_SIGNAL") {
                    messageRepository.handleIncoming(type, data, fromHash)
                }

                withContext(Dispatchers.Main) {
                    listeners.forEach { it(type, data, fromIp, fromHash) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Inbound error", e)
            }
        }
    }

    private suspend fun sendUdp(ip: String, type: String, data: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject()
                    .put("type", type)
                    .put("data", data)
                    .put("from", getMyId())
                    .put("pubkey", CryptoManager.getMyPublicKeyStr())
                    .put("timestamp", System.currentTimeMillis())

                val sig = CryptoManager.sign(json.toString().toByteArray())
                json.put("signature", Base64.encodeToString(sig, Base64.NO_WRAP))

                DatagramSocket().use {
                    it.send(
                        DatagramPacket(
                            json.toString().toByteArray(),
                            json.toString().toByteArray().size,
                            InetAddress.getByName(ip),
                            PORT
                        )
                    )
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "UDP send error", e)
                false
            }
        }

    /* ======================= NSD ======================= */

    private fun registerInWifi() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "KakDela-${getMyId().take(8)}-${UUID.randomUUID().toString().take(4)}"
            serviceType = SERVICE_TYPE
            port = PORT
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun discoverInWifi() {
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(s: NsdServiceInfo) {}
        override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) {}
        override fun onServiceUnregistered(s: NsdServiceInfo) {}
        override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) {}
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(s: NsdServiceInfo) {
            if (s.serviceType == SERVICE_TYPE) {
                nsdManager.resolveService(s, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(r: NsdServiceInfo) {
                        val ip = r.host.hostAddress ?: return
                        val hash = r.serviceName.split("-").getOrNull(1) ?: return
                        wifiPeers[hash] = ip
                    }
                    override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
                })
            }
        }

        override fun onServiceLost(s: NsdServiceInfo) {}
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) {}
        override fun onStartDiscoveryFailed(t: String, e: Int) {}
        override fun onStopDiscoveryFailed(t: String, e: Int) {}
    }

    /* ======================= AVATAR ======================= */

    fun saveLocalAvatar(context: Context, uri: Uri) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            context.openFileOutput("my_avatar.jpg", Context.MODE_PRIVATE)
                .use { input.copyTo(it) }
        }
    }

    fun getLocalAvatarUri(context: Context): Uri? {
        val file = context.getFileStreamPath("my_avatar.jpg")
        return if (file.exists()) Uri.fromFile(file) else null
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
