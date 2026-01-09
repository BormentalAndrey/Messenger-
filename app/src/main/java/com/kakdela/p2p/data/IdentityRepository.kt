package com.kakdela.p2p.data

import android.content.Context
import android.net.wifi.WifiManager
import android.telephony.SmsManager
import android.text.format.Formatter
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
import java.net.*
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

class IdentityRepository(private val context: Context) {

    private val TAG = "IdentityRepository"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val P2P_PORT = 8888
    private val DISCOVERY_PORT = 8889
    private val MAX_TIME_DRIFT = 60_000L

    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()

    private val listeners =
        CopyOnWriteArrayList<(type: String, data: String, fromIp: String, fromId: String) -> Unit>()

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    private var mainSocket: DatagramSocket? = null

    init {
        CryptoManager.init(context)
        startMainSocket()
        startDiscoveryListener()
        startPresenceBroadcast()
        startServerSync()
    }

    /* ======================= IDENTITY ======================= */

    fun getMyId(): String = sha256(CryptoManager.getMyPublicKeyStr())

    fun getMyPublicKeyStr(): String = CryptoManager.getMyPublicKeyStr()

    fun savePeerPublicKey(hash: String, key: String) {
        CryptoManager.savePeerPublicKey(hash, key)
    }

    /* ======================= LISTENERS ======================= */

    fun addListener(listener: (String, String, String, String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String, String, String, String) -> Unit) {
        listeners.remove(listener)
    }

    /* ======================= SIGNALING ======================= */

    fun sendSignaling(targetIp: String, type: String, data: String) = scope.launch {
        val json = JSONObject().apply {
            put("type", type)
            put("data", data)
        }
        enrich(json)
        sendUdp(targetIp, json.toString())
    }

    /* ======================= UDP ======================= */

    private fun startMainSocket() = scope.launch {
        try {
            mainSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(P2P_PORT))
            }
            listenMainSocket()
        } catch (e: Exception) {
            Log.e(TAG, "UDP bind failed", e)
        }
    }

    private suspend fun sendUdp(
        ip: String,
        message: String,
        broadcast: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = broadcast
                val data = message.toByteArray()
                val packet = DatagramPacket(
                    data,
                    data.size,
                    InetAddress.getByName(ip),
                    P2P_PORT
                )
                socket.send(packet)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /* ======================= MESSAGES ======================= */

    fun sendMessage(targetHash: String, text: String) = scope.launch {
        val node = nodeDao.getNode(targetHash)

        val (payload, encrypted) = encryptIfPossible(node?.publicKey, text)

        val json = JSONObject().apply {
            put("type", "MESSAGE")
            put("data", payload)
            put("encrypted", encrypted)
        }
        enrich(json)

        val packet = json.toString()

        if (node != null && node.ip.isNotBlank() && node.ip != "0.0.0.0") {
            if (sendUdp(node.ip, packet)) return@launch
        }

        try {
            val r = api.findPeer(mapOf("hash" to targetHash))
            r.ip?.let {
                if (sendUdp(it, packet)) {
                    updateNodeFromServer(targetHash, r)
                    return@launch
                }
            }
        } catch (_: Exception) {}

        sendUdp("255.255.255.255", packet, true)

        node?.phone?.takeIf { it.isNotBlank() }?.let {
            sendSmsFallback(it, "Новое сообщение")
        }
    }

    /* ======================= RECEIVE ======================= */

    private fun listenMainSocket() = scope.launch {
        val buffer = ByteArray(65535)
        val packet = DatagramPacket(buffer, buffer.size)

        while (isActive) {
            try {
                mainSocket?.receive(packet) ?: continue
                val json = JSONObject(String(packet.data, 0, packet.length))

                if (!verify(json)) continue

                val type = json.getString("type")
                val from = json.getString("from")
                val rawData = json.optString("data")

                val data =
                    if (json.optBoolean("encrypted"))
                        CryptoManager.decryptMessage(rawData)
                    else rawData

                withContext(Dispatchers.Main) {
                    listeners.forEach {
                        it(type, data, packet.address.hostAddress, from)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /* ======================= SECURITY ======================= */

    private fun enrich(json: JSONObject) {
        json.put("from", getMyId())
        json.put("pubkey", getMyPublicKeyStr())
        json.put("timestamp", System.currentTimeMillis())
        json.put("signature", sign(json))
    }

    private fun sign(json: JSONObject): String {
        val clean = JSONObject(json.toString()).apply { remove("signature") }
        val sig = CryptoManager.sign(clean.toString().toByteArray())
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }

    private fun verify(json: JSONObject): Boolean = try {
        val ts = json.getLong("timestamp")
        if (abs(System.currentTimeMillis() - ts) > MAX_TIME_DRIFT) return false

        val sig = Base64.decode(json.getString("signature"), Base64.NO_WRAP)
        val pubKey = json.getString("pubkey")
        val clean = JSONObject(json.toString()).apply { remove("signature") }

        CryptoManager.verify(sig, clean.toString().toByteArray(), pubKey)
    } catch (_: Exception) {
        false
    }

    private fun encryptIfPossible(pubKey: String?, text: String): Pair<String, Boolean> {
        if (pubKey.isNullOrBlank()) return text to false
        val encrypted = CryptoManager.encryptMessage(text, pubKey)
        return if (encrypted.startsWith("[Ошибка]")) text to false else encrypted to true
    }

    /* ======================= BACKGROUND ======================= */

    private fun startServerSync() = scope.launch {
        while (isActive) {
            try {
                api.announceSelf(
                    UserPayload(
                        hash = getMyId(),
                        ip = getLocalIp(),
                        port = P2P_PORT,
                        publicKey = getMyPublicKeyStr(),
                        phone = "",
                        email = null,
                        passwordHash = null
                    )
                )

                val nodes = api.getAllNodes()
                nodeDao.insertAll(nodes.map {
                    NodeEntity(
                        userHash = it.hash ?: "",
                        email = it.email ?: "",
                        passwordHash = "",
                        phone = it.phone ?: "",
                        ip = it.ip ?: "",
                        port = it.port,
                        publicKey = it.publicKey ?: "",
                        lastSeen = System.currentTimeMillis()
                    )
                })
            } catch (_: Exception) {}

            delay(600_000)
        }
    }

    private fun startDiscoveryListener() = scope.launch {
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(DISCOVERY_PORT))
        }

        val buffer = ByteArray(2048)
        val packet = DatagramPacket(buffer, buffer.size)

        while (isActive) {
            try {
                socket.receive(packet)
                val json = JSONObject(String(packet.data, 0, packet.length))

                if (json.optString("type") == "IAM") {
                    val id = json.getString("from")
                    val key = json.getString("pubkey")

                    savePeerPublicKey(id, key)

                    nodeDao.insert(
                        NodeEntity(
                            userHash = id,
                            email = "",
                            passwordHash = "",
                            phone = "",
                            ip = packet.address.hostAddress,
                            port = P2P_PORT,
                            publicKey = key,
                            lastSeen = System.currentTimeMillis()
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private fun startPresenceBroadcast() = scope.launch {
        while (isActive) {
            val json = JSONObject().apply {
                put("type", "IAM")
                put("from", getMyId())
                put("pubkey", getMyPublicKeyStr())
            }
            sendUdp("255.255.255.255", json.toString(), true)
            delay(30_000)
        }
    }

    /* ======================= UTILS ======================= */

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun getLocalIp(): String {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        return Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
    }

    private fun sendSmsFallback(phone: String, text: String) {
        try {
            SmsManager.getDefault()
                .sendTextMessage(phone, null, "[KakDela] $text", null, null)
        } catch (_: Exception) {}
    }

    private fun updateNodeFromServer(hash: String, p: UserPayload) {
        scope.launch {
            nodeDao.insert(
                NodeEntity(
                    userHash = hash,
                    email = p.email ?: "",
                    passwordHash = "",
                    phone = p.phone ?: "",
                    ip = p.ip ?: "",
                    port = p.port,
                    publicKey = p.publicKey ?: "",
                    lastSeen = System.currentTimeMillis()
                )
            )
        }
    }

    fun onDestroy() {
        scope.cancel()
        mainSocket?.close()
    }
}
