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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

class IdentityRepository(private val context: Context) {

    private val TAG = "IdentityRepository"

    private val repositoryJob = SupervisorJob()
    private val scope = CoroutineScope(repositoryJob + Dispatchers.IO)

    private val listeners =
        CopyOnWriteArrayList<(type: String, data: String, fromIp: String, fromHash: String) -> Unit>()

    private val wifiPeers = mutableMapOf<String, String>()   // hash -> ip
    private val swarmPeers = mutableMapOf<String, String>() // hash -> ip

    private val db = ChatDatabase.getDatabase(context)
    private val nsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val SERVICE_TYPE = "_kakdela_p2p._udp"
    private val PORT = 8888

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    private var socket: DatagramSocket? = null
    private var listening = false

    init {
        CryptoManager.init(context)
        startListening()
        registerInWifi()
        discoverInWifi()
        startKeepAlive()
    }

    /* ===================== PUBLIC API ===================== */

    fun sendMessageSmart(targetHash: String, targetPhone: String?, message: String) =
        scope.launch {

            wifiPeers[targetHash]?.let { ip ->
                if (sendUdpInternal(ip, "CHAT", message)) return@launch
            }

            searchInSwarm(targetHash).await()?.let { ip ->
                if (sendUdpInternal(ip, "CHAT", message)) return@launch
            }

            findPeerOnServer(targetHash).await()?.ip?.let { ip ->
                if (ip.isNotBlank() && ip != "0.0.0.0") {
                    if (sendUdpInternal(ip, "CHAT", message)) return@launch
                }
            }

            if (!targetPhone.isNullOrBlank()) {
                sendAsSms(targetPhone, message)
            }
        }

    fun sendSignaling(targetIp: String, type: String, data: String) {
        if (targetIp.isNotBlank() && targetIp != "0.0.0.0") {
            scope.launch { sendUdpInternal(targetIp, type, data) }
        }
    }

    fun addListener(l: (String, String, String, String) -> Unit) = listeners.add(l)
    fun removeListener(l: (String, String, String, String) -> Unit) = listeners.remove(l)

    fun onDestroy() {
        repositoryJob.cancel()
        listening = false
        socket?.close()
        socket = null
    }

    /* ===================== WIFI DISCOVERY ===================== */

    private fun registerInWifi() {
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = getMyId()
                serviceType = SERVICE_TYPE
                port = PORT
            }
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, null)
        } catch (e: Exception) {
            Log.e(TAG, "NSD register failed: ${e.message}")
        }
    }

    private fun discoverInWifi() {
        try {
            nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                object : NsdManager.DiscoveryListener {

                    override fun onDiscoveryStarted(p0: String) {}

                    override fun onServiceFound(service: NsdServiceInfo) {
                        nsdManager.resolveService(service,
                            object : NsdManager.ResolveListener {
                                override fun onServiceResolved(info: NsdServiceInfo) {
                                    info.host?.hostAddress?.let { ip ->
                                        wifiPeers[info.serviceName] = ip
                                    }
                                }

                                override fun onResolveFailed(
                                    serviceInfo: NsdServiceInfo,
                                    errorCode: Int
                                ) {
                                }
                            })
                    }

                    override fun onServiceLost(service: NsdServiceInfo) {
                        wifiPeers.remove(service.serviceName)
                    }

                    override fun onDiscoveryStopped(p0: String) {}
                    override fun onStartDiscoveryFailed(p0: String, p1: Int) {}
                    override fun onStopDiscoveryFailed(p0: String, p1: Int) {}
                })
        } catch (e: Exception) {
            Log.e(TAG, "NSD discovery failed: ${e.message}")
        }
    }

    /* ===================== SWARM ===================== */

    private fun searchInSwarm(targetHash: String): Deferred<String?> = scope.async {
        val nodes = db.nodeDao().getAllNodes().take(100)
        nodes.forEach { node ->
            node.ip?.let { safeIp ->
                if (safeIp.isNotBlank() && safeIp != "0.0.0.0") {
                    sendUdpInternal(safeIp, "QUERY_PEER", targetHash)
                }
            }
        }
        delay(2000)
        swarmPeers[targetHash]
    }

    /* ===================== UDP ===================== */

    private fun startListening() = scope.launch {
        if (listening) return@launch

        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
            }
            listening = true

            val buffer = ByteArray(16 * 1024)

            while (isActive && listening) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket?.receive(packet)

                val data = String(packet.data, 0, packet.length)
                val fromIp = packet.address.hostAddress ?: ""

                handleIncomingPacket(data, fromIp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "UDP listen error: ${e.message}")
        }
    }

    private fun handleIncomingPacket(raw: String, fromIp: String) = scope.launch {
        try {
            val json = JSONObject(raw)
            when (json.optString("type")) {

                "QUERY_PEER" -> {
                    val target = json.getString("data")
                    val node = db.nodeDao().getNodeByHash(target)
                    node?.ip?.let { ip ->
                        if (ip.isNotBlank()) {
                            val resp = JSONObject()
                                .put("hash", node.userHash)
                                .put("ip", ip)
                            sendUdpInternal(fromIp, "PEER_FOUND", resp.toString())
                        }
                    }
                }

                "PEER_FOUND" -> {
                    val obj = JSONObject(json.getString("data"))
                    swarmPeers[obj.getString("hash")] = obj.getString("ip")
                }

                "CHAT", "WEBRTC_SIGNAL" -> {
                    val fromHash = json.getString("from")
                    val pubKey = json.getString("pubkey")
                    val sigB64 = json.optString("signature")

                    if (sigB64.isNotEmpty()) {
                        val signature = Base64.decode(sigB64, Base64.NO_WRAP)
                        val unsigned = JSONObject(raw).apply { remove("signature") }
                        val ok = CryptoManager.verify(
                            signature,
                            unsigned.toString().toByteArray(),
                            pubKey
                        )

                        if (ok) {
                            CryptoManager.savePeerPublicKey(fromHash, pubKey)
                            listeners.forEach {
                                it(json.getString("type"), json.getString("data"), fromIp, fromHash)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Packet parse error: ${e.message}")
        }
    }

    private suspend fun sendUdpInternal(ip: String, type: String, data: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject()
                    .put("type", type)
                    .put("data", data)
                    .put("from", getMyId())
                    .put("pubkey", getMyPublicKeyStr())
                    .put("timestamp", System.currentTimeMillis())

                val sig = CryptoManager.sign(json.toString().toByteArray())
                json.put("signature", Base64.encodeToString(sig, Base64.NO_WRAP))

                val bytes = json.toString().toByteArray()
                DatagramSocket().use {
                    it.send(
                        DatagramPacket(
                            bytes,
                            bytes.size,
                            InetAddress.getByName(ip),
                            PORT
                        )
                    )
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "UDP send failed: ${e.message}")
                false
            }
        }

    /* ===================== SERVER ===================== */

    private fun findPeerOnServer(hash: String): Deferred<UserPayload?> = scope.async {
        try {
            val resp = api.getAllNodes()
            resp.users?.let { users ->
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
        } catch (_: Exception) {
        }
        null
    }

    private fun startKeepAlive() = scope.launch {
        while (isActive) {
            try {
                api.announceSelf(
                    UserPayload(
                        hash = getMyId(),
                        publicKey = getMyPublicKeyStr(),
                        port = PORT
                    )
                )
            } catch (_: Exception) {
            }
            delay(180_000)
        }
    }

    /* ===================== UTILS ===================== */

    private fun sendAsSms(phone: String, msg: String) {
        try {
            SmsManager.getDefault()
                .sendTextMessage(phone, null, "[P2P] $msg", null, null)
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed: ${e.message}")
        }
    }

    fun getMyId(): String = sha256(getMyPublicKeyStr())
    fun getMyPublicKeyStr(): String = CryptoManager.getMyPublicKeyStr()

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
