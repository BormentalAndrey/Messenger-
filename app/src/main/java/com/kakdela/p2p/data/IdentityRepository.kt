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

/**
 * IdentityRepository: Гибридный мессенджер (P2P + Server + SMS).
 * Реализует логику Torrent-сети для хранения данных о 2500 узлах.
 */
class IdentityRepository(private val context: Context) {

    private val TAG = "P2P_Repo"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val P2P_PORT = 8888
    private val DISCOVERY_PORT = 8889
    private val MAX_DRIFT = 60_000L // Защита от повторных атак (1 мин)

    private val listeners = CopyOnWriteArrayList<(type: String, data: String, fromIp: String, fromId: String) -> Unit>()
    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()

    // Настройка API (InfinityFree или ваш VPS)
    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/") 
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    private var mainSocket: DatagramSocket? = null

    init {
        CryptoManager.generateKeysIfNeeded(context)
        initMainSocket()
        startDiscoveryListener() // Слушаем Wi-Fi окружение
        startPresenceBroadcast() // Объявляем себя в Wi-Fi
        startBackgroundSync()    // Torrent-синхронизация с сервером
    }

    // ==================== Идентификация и Контакты ====================

    fun getMyId(): String = sha256(CryptoManager.getMyPublicKeyStr())
    private fun getMyPublicKeyStr(): String = CryptoManager.getMyPublicKeyStr()

    /**
     * Возвращает только тех пользователей из сети, которые есть в контактах телефона
     */
    suspend fun getMyContactList(): List<NodeEntity> = withContext(Dispatchers.IO) {
        val localPhones = ContactUtils.getNormalizedPhoneNumbers(context)
        nodeDao.getAllNodes().filter { node ->
            val cleanPhone = node.phone.replace(Regex("[^0-9]"), "").takeLast(10)
            localPhones.contains(cleanPhone)
        }
    }

    // ==================== Сетевое ядро (P2P) ====================

    private fun initMainSocket() = scope.launch {
        try {
            mainSocket = DatagramSocket(null).apply {
                reuseAddress = true // Fix EADDRINUSE
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

    // ==================== Логика отправки (Hybrid Mode) ====================

    fun sendMessage(targetHash: String, messageText: String) = scope.launch {
        val targetNode = nodeDao.getNode(targetHash)
        
        // Шифруем, если у нас есть публичный ключ собеседника в БД
        val (dataToSend, isEncrypted) = encryptIfPossible(targetNode?.publicKey, messageText)

        val json = JSONObject().apply {
            put("type", "MESSAGE")
            put("data", dataToSend)
            put("encrypted", isEncrypted)
        }
        enrich(json) // Добавляем подпись, ID и таймстамп
        val packetStr = json.toString()

        // 1. Пытаемся отправить напрямую (Wi-Fi или статический IP)
        if (!targetNode?.ip.isNullOrBlank() && targetNode.ip != "0.0.0.0") {
            if (sendUdp(targetNode.ip, packetStr)) return@launch
        }

        // 2. Mesh-отправка: кидаем в локальную сеть
        sendUdp("255.255.255.255", packetStr, true)

        // 3. Пытаемся через сервер (если инета нет у нас, но есть Wi-Fi до сервера)
        try {
            val serverInfo = api.findPeer(mapOf("hash" to targetHash))
            if (!serverInfo.ip.isNullOrBlank()) {
                if (sendUdp(serverInfo.ip, packetStr)) {
                    updateNodeFromServer(targetHash, serverInfo)
                    return@launch
                }
            }
        } catch (_: Exception) {}

        // 4. Последний рубеж: SMS-уведомление
        val phone = targetNode?.phone ?: ""
        if (phone.isNotBlank()) {
            sendSmsFallback(phone, "Новое сообщение в KakDela")
        }
    }

    // ==================== Torrent-синхронизация ====================

    private fun startBackgroundSync() = scope.launch {
        while (isActive) {
            try {
                // Обновляем свои данные на сервере
                api.announceSelf(UserPayload(getMyId(), getLocalIpAddress(), P2P_PORT, getMyPublicKeyStr(), getMyPhoneNumber()))

                // Скачиваем "Torrent-лист" (2500 пользователей)
                val nodes = api.getAllNodes()
                val entities = nodes.map { serverNode ->
                    NodeEntity(
                        userHash = serverNode.hash ?: "",
                        phone = serverNode.phone ?: "",
                        ip = serverNode.ip ?: "",
                        port = P2P_PORT,
                        publicKey = serverNode.publicKey ?: "",
                        lastSeen = System.currentTimeMillis()
                    )
                }
                nodeDao.insertAll(entities)
                nodeDao.trimDatabase() // Оставляем только свежие 2500
                Log.d(TAG, "DHT Synchronized: ${entities.size} nodes")
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed: ${e.message}")
            }
            delay(600_000) // 10 минут
        }
    }

    // ==================== Безопасность и Прием ====================

    private fun startMainPacketListener() = scope.launch {
        val buffer = ByteArray(65535)
        val packet = DatagramPacket(buffer, buffer.size)

        while (isActive) {
            try {
                mainSocket?.receive(packet) ?: continue
                val msg = String(packet.data, 0, packet.length)
                val json = JSONObject(msg)

                if (!verify(json)) continue // Отсекаем поддельные пакеты

                val fromId = json.getString("from")
                val type = json.getString("type")
                val data = json.optString("data")
                val isEnc = json.optBoolean("encrypted")

                val processedData = if (isEnc) decryptSafe(data) else data

                withContext(Dispatchers.Main) {
                    listeners.forEach { it(type, processedData, packet.address.hostAddress, fromId) }
                }
            } catch (e: Exception) { }
        }
    }

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
            val sig = json.getString("signature")
            val pubKey = json.getString("pubkey")
            val cleanJson = JSONObject(json.toString()).apply { remove("signature") }
            CryptoManager.verify(Base64.decode(sig, Base64.NO_WRAP), cleanJson.toString().toByteArray(), pubKey)
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
        try {
            SmsManager.getDefault().sendTextMessage(phone, null, "[KakDela] $text", null, null)
        } catch (_: Exception) {}
    }

    private fun decryptSafe(data: String) = try {
        String(CryptoManager.decrypt(Base64.decode(data, Base64.NO_WRAP)))
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
            nodeDao.insert(NodeEntity(hash, payload.phone?:"", payload.ip?:"", P2P_PORT, payload.publicKey?:"", System.currentTimeMillis()))
        }
    }

    private fun getMyPhoneNumber(): String = "" // Реализовать получение через UI/Настройки

    private fun startDiscoveryListener() = scope.launch { /* Логика обнаружения IAM пакетов */ }
    private fun startPresenceBroadcast() = scope.launch { /* Периодический IAM пакет в сеть */ }

    fun onDestroy() {
        scope.cancel()
        mainSocket?.close()
    }
}
