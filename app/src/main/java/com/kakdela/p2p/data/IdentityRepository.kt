package com.kakdela.p2p.data

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Base64
import android.util.Log
import com.kakdela.p2p.security.CryptoManager
import com.kakdela.p2p.ui.call.CallActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs

/**
 * Репозиторий для управления P2P-идентификацией, DHT-таблицей и UDP-сигналингом.
 */
class IdentityRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("p2p_identity", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Локальный сегмент распределенной хеш-таблицы (DHT): Ключ -> (Значение, Время жизни)
    private val localDhtSlice = ConcurrentHashMap<String, Pair<String, Long>>()
    
    // Список обнаруженных IP-адресов узлов в локальной сети
    private val discoveredPeers = CopyOnWriteArraySet<String>()
    
    // Защита от Replay-атак: храним метки времени последних сообщений
    private val seenTimestamps = ConcurrentHashMap<String, Long>()

    // Коллбэк для обработки входящих сообщений (сигналинг звонков, чат)
    var onSignalingMessageReceived: ((type: String, data: String, fromIp: String) -> Unit)? = null

    private val P2P_PORT = 8888
    private val DISCOVERY_PORT = 8889
    private val MAX_MESSAGE_AGE_MS = 30_000L // Сообщение действительно 30 секунд
    private val DHT_TTL_MS = 48 * 60 * 60 * 1000L // Запись в DHT живет 48 часов

    init {
        startListening()     // Слушаем входящий сигналинг и запросы данных
        startDiscovery()     // Слушаем анонсы других узлов
        broadcastPresence()  // Анонсируем себя
        cleanupWorker()      // Очистка устаревших данных
    }

    // --- Identity API ---

    fun getMyId(): String = getMyPublicKeyHash()

    fun getMyPublicKeyHash(): String {
        val savedHash = prefs.getString("my_pub_key_hash", null)
        if (savedHash != null) return savedHash

        val pubKey = CryptoManager.getMyPublicKeyStr()
        val hash = sha256(pubKey)
        prefs.edit().putString("my_pub_key_hash", hash).apply()
        return hash
    }

    fun isKeyReady(): Boolean = CryptoManager.isKeyReady()

    /**
     * Публикует данные пользователя (телефон/имя) в сеть для поиска.
     */
    suspend fun publishIdentity(phoneNumber: String, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val publicKey = CryptoManager.getMyPublicKeyStr()
            val phoneHash = sha256(phoneNumber)
            
            prefs.edit()
                .putString("my_phone", phoneNumber)
                .putString("my_name", name)
                .apply()

            // Рассылаем запись всем узлам: Хеш номера -> Публичный ключ
            sendBroadcastPacket("STORE", phoneHash, publicKey)
            true
        } catch (e: Exception) {
            Log.e("P2P_ID", "Publish failed", e)
            false
        }
    }

    suspend fun updateEmailBackup(email: String, pass: String): Boolean {
        // В P2P логике email используется как ключ поиска ключа восстановления
        return publishIdentity(email, "P2P_Backup_Node")
    }

    // --- Network API ---

    fun sendSignaling(targetIp: String, type: String, data: String) {
        val payload = JSONObject().apply {
            put("type", type)
            put("data", data)
            put("from", getMyPublicKeyHash())
            put("timestamp", System.currentTimeMillis())
        }
        payload.put("signature", sign(payload))
        sendUdp(targetIp, payload.toString())
    }

    fun findPeerInDHT(key: String) {
        val payload = JSONObject().apply {
            put("type", "FIND")
            put("key", key)
            put("from", getMyPublicKeyHash())
            put("timestamp", System.currentTimeMillis())
        }
        payload.put("signature", sign(payload))
        
        // Опрашиваем всех известных пиров
        discoveredPeers.forEach { ip -> sendUdp(ip, payload.toString()) }
    }

    fun sendToSingleAddress(ip: String, message: String) = sendUdp(ip, message)

    // --- UDP Engine ---

    

    private fun startListening() {
        scope.launch {
            val socket = DatagramSocket(P2P_PORT)
            val buffer = ByteArray(65535)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    
                    val senderIp = packet.address.hostAddress ?: continue
                    val messageStr = String(packet.data, 0, packet.length)
                    val json = JSONObject(messageStr)
                    
                    // Проверка подписи отправителя
                    if (!verify(json)) continue

                    val type = json.getString("type")
                    when (type) {
                        "STORE" -> {
                            localDhtSlice[json.getString("key")] = 
                                json.getString("value") to (System.currentTimeMillis() + DHT_TTL_MS)
                        }
                        "FIND" -> {
                            val key = json.getString("key")
                            localDhtSlice[key]?.let { (value, _) ->
                                sendSignaling(senderIp, "STORE_RESPONSE", "$key:$value")
                            }
                        }
                        "OFFER" -> launchIncomingCall(senderIp, json.optString("data"))
                        else -> {
                            // Прокидываем в UI (сообщения чата, ICE-кандидаты и т.д.)
                            onSignalingMessageReceived?.invoke(type, json.optString("data"), senderIp)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("P2P_NET", "Listen error", e)
                }
            }
        }
    }

    private fun sendUdp(ip: String, message: String) {
        scope.launch {
            try {
                val address = InetAddress.getByName(ip)
                val socket = DatagramSocket()
                val data = message.toByteArray()
                socket.send(DatagramPacket(data, data.size, address, P2P_PORT))
                socket.close()
            } catch (e: Exception) {
                Log.e("P2P_NET", "Send failed to $ip", e)
            }
        }
    }

    private fun sendBroadcastPacket(type: String, key: String, value: String) {
        val payload = JSONObject().apply {
            put("type", type)
            put("key", key)
            put("value", value)
            put("from", getMyPublicKeyHash())
            put("timestamp", System.currentTimeMillis())
        }
        payload.put("signature", sign(payload))
        
        // Отправляем всем известным узлам напрямую + Broadcast в подсеть
        val msg = payload.toString()
        discoveredPeers.forEach { sendUdp(it, msg) }
        sendUdp("255.255.255.255", msg)
    }

    private fun startDiscovery() {
        scope.launch {
            try {
                val socket = DatagramSocket(DISCOVERY_PORT)
                val buffer = ByteArray(512)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val ip = packet.address.hostAddress ?: continue
                    if (ip != getLocalIp()) {
                        discoveredPeers.add(ip)
                    }
                }
            } catch (e: Exception) {
                Log.e("P2P_DISCOVERY", "Stopped", e)
            }
        }
    }

    private fun broadcastPresence() {
        scope.launch {
            val socket = DatagramSocket().apply { broadcast = true }
            val msg = "IAM_HERE".toByteArray()
            while (isActive) {
                try {
                    val address = InetAddress.getByName("255.255.255.255")
                    socket.send(DatagramPacket(msg, msg.size, address, DISCOVERY_PORT))
                } catch (e: Exception) {}
                delay(15_000) // Анонс каждые 15 секунд
            }
        }
    }

    // --- Security Helpers ---

    private fun sign(json: JSONObject): String {
        // Создаем копию без поля signature для корректного хеширования
        val clone = JSONObject(json.toString()).apply { remove("signature") }
        val signatureBytes = CryptoManager.sign(clone.toString().toByteArray())
        return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
    }

    private fun verify(json: JSONObject): Boolean {
        return try {
            val from = json.getString("from")
            val timestamp = json.getLong("timestamp")
            val signature = json.getString("signature")

            // 1. Проверка времени (защита от повторов)
            if (abs(System.currentTimeMillis() - timestamp) > MAX_MESSAGE_AGE_MS) return false
            
            // 2. Проверка дубликата по ID сообщения (from + timestamp)
            val msgId = "$from$timestamp"
            if (seenTimestamps.containsKey(msgId)) return false
            seenTimestamps[msgId] = timestamp

            // 3. Проверка криптографической подписи
            val clone = JSONObject(json.toString()).apply { remove("signature") }
            val pubKey = CryptoManager.getPublicKeyByHash(from) ?: return false
            
            CryptoManager.verify(
                clone.toString().toByteArray(),
                Base64.decode(signature, Base64.NO_WRAP),
                pubKey
            )
        } catch (e: Exception) {
            false
        }
    }

    private fun launchIncomingCall(ip: String, sdp: String) {
        val intent = Intent(context, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("targetIp", ip)
            putExtra("isIncoming", true)
            putExtra("remoteSdp", sdp)
        }
        context.startActivity(intent)
    }

    private fun cleanupWorker() {
        scope.launch {
            while (isActive) {
                delay(60_000)
                val now = System.currentTimeMillis()
                seenTimestamps.entries.removeIf { now - it.value > MAX_MESSAGE_AGE_MS }
                localDhtSlice.entries.removeIf { it.value.second < now }
            }
        }
    }

    private fun getLocalIp(): String? = try {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
    } catch (e: Exception) {
        null
    }

    private fun sha256(input: String): String = 
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

