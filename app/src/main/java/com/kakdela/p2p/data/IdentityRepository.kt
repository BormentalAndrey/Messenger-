package com.kakdela.p2p.data

import android.content.Context
import android.content.Intent
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
import com.kakdela.p2p.ui.call.CallActivity
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
import kotlin.random.Random

/**
 * IdentityRepository: Гибридный мессенджер (P2P + Server + SMS)
 * Сохраняет и синхронизирует информацию о пользователях сети, шифрует сообщения.
 */
class IdentityRepository(private val context: Context) {

    private val TAG = "P2P_Repo"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val P2P_PORT = 8888
    private val DISCOVERY_PORT = 8889
    private val MAX_DRIFT = 60_000L // 1 минута

    private val listeners = CopyOnWriteArrayList<(type: String, data: String, fromIp: String, fromId: String) -> Unit>()
    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()

    // Настройка API
    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/") // твой сервер
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    private var mainSocket: DatagramSocket? = null

    init {
        CryptoManager.generateKeysIfNeeded(context)
        initMainSocket()
        startDiscoveryListener()
        startPresenceBroadcast()
        startBackgroundSync()
    }

    // ==================== Идентификация ====================

    fun getMyId(): String = sha256(CryptoManager.getMyPublicKeyStr())
    private fun getMyPublicKeyStr(): String = CryptoManager.getMyPublicKeyStr()

    suspend fun getMyContactList(): List<NodeEntity> = withContext(Dispatchers.IO) {
        val localPhones = ContactUtils.getNormalizedPhoneNumbers(context)
        nodeDao.getAllNodes().filter { node ->
            val cleanPhone = node.phone.replace(Regex("[^0-9]"), "").takeLast(10)
            localPhones.contains(cleanPhone)
        }
    }

    // ==================== Сетевой уровень ====================

    private fun initMainSocket() = scope.launch {
        try {
            mainSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(P2P_PORT))
            }
            Log.d(TAG, "P2P Socket ready on $P2P_PORT")
            startMainPacketListener()
        } catch (e: Exception) {
            Log.e(TAG, "Socket bind failed", e)
        }
    }

    private suspend fun sendUdp(ip: String, message: String, isBroadcast: Boolean = false): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = isBroadcast
                    val bytes = message.toByteArray()
                    val packet = DatagramPacket(bytes, bytes.size, InetAddress.getByName(ip), P2P_PORT)
                    socket.send(packet)
                }
                true
            } catch (e: Exception) { false }
        }
    }

    // ==================== Отправка сообщений ====================

    fun sendMessage(targetHash: String, messageText: String) = scope.launch {
        val targetNode = nodeDao.getNode(targetHash)

        val (dataToSend, isEncrypted) = encryptIfPossible(targetNode?.publicKey, messageText)
        val json = JSONObject().apply {
            put("type", "MESSAGE")
            put("data", dataToSend)
            put("encrypted", isEncrypted)
        }
        enrich(json)
        val packetStr = json.toString()

        // 1. Прямое соединение
        if (!targetNode?.ip.isNullOrBlank() && targetNode.ip != "0.0.0.0") {
            if (sendUdp(targetNode.ip, packetStr)) return@launch
        }

        // 2. Mesh Broadcast
        sendUdp("255.255.255.255", packetStr, true)

        // 3. Через сервер
        try {
            val serverInfo = api.findPeer(mapOf("hash" to targetHash))
            if (!serverInfo.ip.isNullOrBlank()) {
                if (sendUdp(serverInfo.ip, packetStr)) {
                    updateNodeFromServer(targetHash, serverInfo)
                    return@launch
                }
            }
        } catch (_: Exception) {}

        // 4. SMS fallback
        targetNode?.phone?.takeIf { it.isNotBlank() }?.let { sendSmsFallback(it, "Новое сообщение в KakDela") }
    }

    // ==================== Torrent / Серверная синхронизация ====================

    private fun startBackgroundSync() = scope.launch {
        while (isActive) {
            try {
                val payload = UserPayload(
                    hash = getMyId(),
                    ip = getLocalIpAddress(),
                    port = P2P_PORT,
                    publicKey = getMyPublicKeyStr(),
                    phone = getMyPhoneNumber()
                )
                api.announceSelf(payload)

                val nodes = api.getAllNodes()
                val entities = nodes.map { serverNode ->
                    NodeEntity(
                        userHash = serverNode.hash ?: "",
                        email = serverNode.email ?: "",
                        passwordHash = "", // можно хранить хеш локально
                        phone = serverNode.phone ?: "",
                        ip = serverNode.ip ?: "",
                        port = P2P_PORT,
                        publicKey = serverNode.publicKey ?: "",
                        lastSeen = System.currentTimeMillis()
                    )
                }
                nodeDao.insertAll(entities)
                nodeDao.trimDatabase()
                Log.d(TAG, "DHT Synchronized: ${entities.size} nodes")
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed: ${e.message}")
            }
            delay(600_000) // 10 мин
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
                val isEnc = json.optBoolean("encrypted")
                val processedData = if (isEnc) decryptSafe(data) else data

                withContext(Dispatchers.Main) {
                    listeners.forEach { it(type, processedData, packet.address.hostAddress, fromId) }
                }
            } catch (_: Exception) {}
        }
    }

    // ==================== Подпись и проверка ====================

    private fun enrich(json: JSONObject) {
        json.put("from", getMyId())
        json.put("pubkey", getMyPublicKeyStr())
        json.put("timestamp", System.currentTimeMillis())
        json.put("signature", sign(json))
    }

    private fun verify(json: JSONObject): Boolean = try {
        val ts = json.getLong("timestamp")
        if (abs(System.currentTimeMillis() - ts) > MAX_DRIFT) false
        else {
            val sig = Base64.decode(json.getString("signature"), Base64.NO_WRAP)
            val pubKey = json.getString("pubkey")
            val cleanJson = JSONObject(json.toString()).apply { remove("signature") }
            CryptoManager.verify(sig, cleanJson.toString().toByteArray(), pubKey)
        }
    } catch (_: Exception) { false }

    private fun sign(json: JSONObject): String {
        val clean = JSONObject(json.toString()).apply { remove("signature") }
        val sig = CryptoManager.sign(clean.toString().toByteArray())
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    // ==================== Утилиты ====================

    private fun getLocalIpAddress(): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
    }

    private fun sendSmsFallback(phone: String, text: String) {
        try { SmsManager.getDefault().sendTextMessage(phone, null, "[KakDela] $text", null, null) }
        catch (_: Exception) {}
    }

    private fun decryptSafe(data: String) = try {
        val bytes = Base64.decode(data, Base64.NO_WRAP)
        String(CryptoManager.decrypt(bytes))
    } catch (_: Exception) { "<ошибка расшифровки>" }

    private fun encryptIfPossible(pubKey: String?, text: String): Pair<String, Boolean> {
        if (pubKey.isNullOrBlank()) return text to false
        return try {
            val enc = CryptoManager.encryptFor(pubKey, text.toByteArray())
            Base64.encodeToString(enc, Base64.NO_WRAP) to true
        } catch (_: Exception) { text to false }
    }

    private fun updateNodeFromServer(hash: String, payload: UserPayload) {
        scope.launch {
            nodeDao.insert(NodeEntity(
                userHash = hash,
                email = payload.email ?: "",
                passwordHash = "",
                phone = payload.phone ?: "",
                ip = payload.ip ?: "",
                port = P2P_PORT,
                publicKey = payload.publicKey ?: "",
                lastSeen = System.currentTimeMillis()
            ))
        }
    }

    private fun getMyPhoneNumber(): String = "" // Можно получить через UI

    // ==================== Discovery / Presence ====================

    private fun startDiscoveryListener() = scope.launch {
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(DISCOVERY_PORT))
        }
        val buffer = ByteArray(1024)
        val packet = DatagramPacket(buffer, buffer.size)
        while (isActive) {
            try {
                socket.receive(packet)
                val msg = String(packet.data, 0, packet.length)
                val json = JSONObject(msg)
                if (json.getString("type") == "IAM") {
                    val fromId = json.getString("from")
                    val ip = packet.address.hostAddress
                    val pubKey = json.getString("pubkey")
                    CryptoManager.savePeerPublicKey(fromId, pubKey)
                    // Сохраняем узел в базу
                    nodeDao.insert(NodeEntity(
                        userHash = fromId,
                        email = "",
                        passwordHash = "",
                        phone = "",
                        ip = ip,
                        port = P2P_PORT,
                        publicKey = pubKey,
                        lastSeen = System.currentTimeMillis()
                    ))
                }
            } catch (_: Exception) {}
        }
    }

    private fun startPresenceBroadcast() = scope.launch {
        val broadcastIp = "255.255.255.255"
        while (isActive) {
            try {
                val json = JSONObject().apply {
                    put("type", "IAM")
                    put("from", getMyId())
                    put("pubkey", getMyPublicKeyStr())
                }
                sendUdp(broadcastIp, json.toString(), true)
            } catch (_: Exception) {}
            delay(30_000L) // каждые 30 секунд
        }
    }

    fun onDestroy() {
        scope.cancel()
        mainSocket?.close()
    }
}
