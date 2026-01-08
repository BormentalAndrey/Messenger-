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
import com.kakdela.p2p.utils.ContactUtils
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
import kotlin.math.abs

/**
 * IdentityRepository: Гибридный мессенджер (P2P + Server + SMS)
 */
class IdentityRepository(private val context: Context) {

    private val TAG = "P2P_Repo"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val P2P_PORT = 8888
    private val DISCOVERY_PORT = 8889
    private val MAX_DRIFT = 60_000L

    private val listeners = CopyOnWriteArrayList<(type: String, data: String, fromIp: String, fromId: String) -> Unit>()
    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    private var mainSocket: DatagramSocket? = null

    init {
        CryptoManager.init(context) // Инициализация Tink
        initMainSocket()
        startDiscoveryListener()
        startPresenceBroadcast()
        startBackgroundSync()
    }

    // ==================== Идентификация ====================

    fun getMyId(): String = sha256(CryptoManager.getMyPublicKeyStr())
    
    fun getMyPublicKeyStr(): String = CryptoManager.getMyPublicKeyStr()

    fun savePeerPublicKey(hash: String, key: String) {
        CryptoManager.savePeerPublicKey(hash, key)
    }

    // ==================== P2P: Listeners ====================

    fun addListener(listener: (String, String, String, String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String, String, String, String) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Исправлено: Соответствие вызовам из WebRTC и FileTransfer.
     */
    fun sendSignaling(targetIp: String, type: String, data: String) = scope.launch {
        val json = JSONObject().apply {
            put("type", type)
            put("data", data)
        }
        enrich(json)
        sendUdp(targetIp, json.toString())
    }

    // ==================== Сетевой уровень ====================

    private fun initMainSocket() = scope.launch {
        try {
            mainSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(P2P_PORT))
            }
            startMainPacketListener()
        } catch (e: Exception) {
            Log.e(TAG, "Socket bind failed", e)
        }
    }

    private suspend fun sendUdp(ip: String, message: String, isBroadcast: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            if (ip.isBlank()) return@withContext false
            DatagramSocket().use { socket ->
                socket.broadcast = isBroadcast
                val bytes = message.toByteArray()
                val packet = DatagramPacket(bytes, bytes.size, InetAddress.getByName(ip), P2P_PORT)
                socket.send(packet)
            }
            true
        } catch (e: Exception) { false }
    }

    // ==================== Отправка сообщений ====================

    fun sendMessage(targetHash: String, messageText: String) = scope.launch {
        val targetNode = nodeDao.getNode(targetHash)
        
        // E2EE шифрование через Tink
        val (dataToSend, isEncrypted) = encryptIfPossible(targetNode?.publicKey, messageText)
        
        val json = JSONObject().apply {
            put("type", "MESSAGE")
            put("data", dataToSend)
            put("encrypted", isEncrypted)
        }
        enrich(json)
        val packetStr = json.toString()

        // 1. Direct P2P
        if (targetNode != null && !targetNode.ip.isNullOrBlank() && targetNode.ip != "0.0.0.0") {
            if (sendUdp(targetNode.ip, packetStr)) return@launch
        }

        // 2. DHT / Server Lookup
        try {
            val response = api.findPeer(mapOf("hash" to targetHash))
            response.ip?.let { serverIp ->
                if (sendUdp(serverIp, packetStr)) {
                    updateNodeFromServer(targetHash, response)
                    return@launch
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Server lookup failed for $targetHash")
        }

        // 3. Mesh Broadcast & SMS Fallback
        sendUdp("255.255.255.255", packetStr, true)
        
        targetNode?.phone?.takeIf { it.isNotBlank() }?.let {
            sendSmsFallback(it, "Новое P2P сообщение")
        }
    }

    // ==================== Прием пакетов ====================

    private fun startMainPacketListener() = scope.launch {
        val buffer = ByteArray(65535)
        val packet = DatagramPacket(buffer, buffer.size)
        while (isActive) {
            try {
                mainSocket?.receive(packet) ?: continue
                val msg = String(packet.data, 0, packet.length)
                val json = JSONObject(msg)
                
                if (!verify(json)) continue

                val fromId = json.getString("from")
                val type = json.getString("type")
                val data = json.optString("data")
                
                // Автоматическая расшифровка если помечено Tink
                val processedData = if (json.optBoolean("encrypted")) {
                    CryptoManager.decryptMessage(data)
                } else data

                withContext(Dispatchers.Main) {
                    listeners.forEach { it(type, processedData, packet.address.hostAddress, fromId) }
                }
            } catch (e: Exception) {
                // Игнорируем битые пакеты
            }
        }
    }

    // ==================== Безопасность ====================

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
        if (abs(System.currentTimeMillis() - ts) > MAX_DRIFT) false
        else {
            val sig = Base64.decode(json.getString("signature"), Base64.NO_WRAP)
            val pubKey = json.getString("pubkey")
            val clean = JSONObject(json.toString()).apply { remove("signature") }
            CryptoManager.verify(sig, clean.toString().toByteArray(), pubKey)
        }
    } catch (e: Exception) { false }

    private fun encryptIfPossible(pubKey: String?, text: String): Pair<String, Boolean> {
        if (pubKey.isNullOrBlank()) return text to false
        val encrypted = CryptoManager.encryptMessage(text, pubKey)
        return if (encrypted.startsWith("[Ошибка]")) text to false else encrypted to true
    }

    // ==================== Фоновые задачи ====================

    private fun startBackgroundSync() = scope.launch {
        while (isActive) {
            try {
                val payload = UserPayload(getMyId(), getLocalIpAddress(), P2P_PORT, getMyPublicKeyStr(), "")
                api.announceSelf(payload)
                
                val nodes = api.getAllNodes()
                nodeDao.insertAll(nodes.map { s ->
                    NodeEntity(s.hash ?: "", s.email ?: "", "", s.phone ?: "", s.ip ?: "", P2P_PORT, s.publicKey ?: "", System.currentTimeMillis())
                })
            } catch (e: Exception) {
                Log.e(TAG, "Sync error: ${e.message}")
            }
            delay(600_000)
        }
    }

    private fun startDiscoveryListener() = scope.launch {
        val dsSocket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(DISCOVERY_PORT))
        }
        val buffer = ByteArray(2048)
        val packet = DatagramPacket(buffer, buffer.size)
        while (isActive) {
            try {
                dsSocket.receive(packet)
                val json = JSONObject(String(packet.data, 0, packet.length))
                if (json.optString("type") == "IAM") {
                    val fromId = json.getString("from")
                    val pubKey = json.getString("pubkey")
                    savePeerPublicKey(fromId, pubKey)
                    nodeDao.insert(NodeEntity(fromId, "", "", "", packet.address.hostAddress, P2P_PORT, pubKey, System.currentTimeMillis()))
                }
            } catch (e: Exception) {}
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

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun getLocalIpAddress(): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
    }

    private fun sendSmsFallback(phone: String, text: String) {
        try { SmsManager.getDefault().sendTextMessage(phone, null, "[KakDela] $text", null, null) } catch (e: Exception) {}
    }

    private fun updateNodeFromServer(hash: String, p: UserPayload) {
        scope.launch {
            nodeDao.insert(NodeEntity(hash, p.email ?: "", "", p.phone ?: "", p.ip ?: "", P2P_PORT, p.publicKey ?: "", System.currentTimeMillis()))
        }
    }

    fun onDestroy() {
        scope.cancel()
        mainSocket?.close()
    }
}
