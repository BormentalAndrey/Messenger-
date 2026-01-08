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
import java.net.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs

class IdentityRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("p2p_identity", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Константы сервера и портов
    private val CLOUD_API_URL = "http://kakdela.infinityfree.me/api.php"
    private val P2P_PORT = 8888
    private val DISCOVERY_PORT = 8889
    private val MAX_MESSAGE_AGE_MS = 30_000L // 30 секунд для предотвращения атак повтора

    // DHT и сетевые кэши
    private val localDhtSlice = ConcurrentHashMap<String, Pair<String, Long>>()
    private val discoveredPeers = CopyOnWriteArraySet<String>()
    private val seenTimestamps = ConcurrentHashMap<String, Long>()

    // События
    var onSignalingMessageReceived: ((type: String, data: String, fromIp: String) -> Unit)? = null

    init {
        startListening()     // Слушаем входящий UDP сигналинг
        startDiscovery()     // Обнаруживаем соседей в LAN
        broadcastPresence()  // Анонсируем себя в LAN
        cleanupWorker()      // Очистка старых меток времени и DHT
    }

    // --- IDENTITY & CLOUD SYNC API ---

    fun getMyPublicKeyHash(): String {
        val savedHash = prefs.getString("my_pub_key_hash", null)
        if (savedHash != null) return savedHash
        return if (CryptoManager.isKeyReady()) {
            val hash = sha256(CryptoManager.getMyPublicKeyStr())
            prefs.edit().putString("my_pub_key_hash", hash).apply()
            hash
        } else ""
    }

    suspend fun publishIdentity(phoneNumber: String, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!CryptoManager.isKeyReady()) CryptoManager.generateKeys(context)
            
            val publicKey = CryptoManager.getMyPublicKeyStr()
            val normalizedPhone = normalizePhoneNumber(phoneNumber)
            val phoneHash = sha256(normalizedPhone)

            val userData = JSONObject().apply {
                put("name", name)
                put("pub_key", publicKey)
            }

            // 1. Регистрация на Bootstrap сервере
            val success = makeCloudRequest("add_user", JSONObject().apply {
                put("hash", phoneHash)
                put("data", userData)
            })

            if (success) {
                prefs.edit().apply {
                    putString("my_phone", normalizedPhone)
                    putString("my_name", name)
                    putString("my_pub_key_hash", getMyPublicKeyHash())
                }.apply()
            }
            
            // 2. Анонс в локальную сеть
            sendBroadcastPacket("STORE", phoneHash, publicKey)
            
            success
        } catch (e: Exception) {
            Log.e("P2P_ID", "Publish error", e)
            false
        }
    }

    suspend fun syncContacts(phoneNumbers: List<String>): List<JSONObject> = withContext(Dispatchers.IO) {
        val found = mutableListOf<JSONObject>()
        phoneNumbers.distinct().forEach { number ->
            val hash = sha256(normalizePhoneNumber(number))
            try {
                val response = URL("$CLOUD_API_URL?action=get_user&hash=$hash").readText()
                val json = JSONObject(response)
                if (!json.isNull("data")) {
                    val contactData = json.getJSONObject("data")
                    contactData.put("hash", hash)
                    found.add(contactData)
                    // Кэшируем на 48 часов
                    localDhtSlice[hash] = contactData.getString("pub_key") to (System.currentTimeMillis() + 172800000L)
                }
            } catch (e: Exception) { }
        }
        found
    }

    suspend fun updateEmailBackup(email: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val emailHash = sha256(email.lowercase().trim())
        try {
            val response = URL("$CLOUD_API_URL?action=get_user&hash=$emailHash").readText()
            val json = JSONObject(response)
            if (!json.isNull("data")) {
                val encryptedKey = json.getJSONObject("data").getString("enc_private_key")
                // Здесь логика расшифровки ключа паролем через CryptoManager
                return@withContext CryptoManager.restoreIdentity(encryptedKey, pass)
            }
        } catch (e: Exception) { }
        false
    }

    // --- UDP ENGINE (REAL LOGIC) ---

    private fun startListening() {
        scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(P2P_PORT))
                }
                val buffer = ByteArray(65535)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val senderIp = packet.address.hostAddress ?: continue
                    val message = String(packet.data, 0, packet.length)
                    
                    try {
                        val json = JSONObject(message)
                        if (!verify(json)) continue // Криптографическая проверка подписи

                        val type = json.getString("type")
                        val data = json.optString("data")

                        when (type) {
                            "STORE" -> {
                                val key = json.getString("key")
                                val value = json.getString("value")
                                localDhtSlice[key] = value to (System.currentTimeMillis() + 172800000L)
                            }
                            "OFFER" -> launchIncomingCall(senderIp, data)
                            else -> onSignalingMessageReceived?.invoke(type, data, senderIp)
                        }
                    } catch (e: Exception) { Log.e("P2P_NET", "Malformed JSON") }
                }
            } catch (e: Exception) { Log.e("P2P_NET", "Socket error", e) }
            finally { socket?.close() }
        }
    }

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

    private fun sendUdp(ip: String, message: String) {
        scope.launch {
            try {
                val address = InetAddress.getByName(ip)
                val socket = DatagramSocket()
                val bytes = message.toByteArray()
                socket.send(DatagramPacket(bytes, bytes.size, address, P2P_PORT))
                socket.close()
            } catch (e: Exception) { }
        }
    }

    private fun startDiscovery() {
        scope.launch {
            val socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(DISCOVERY_PORT))
            }
            val buffer = ByteArray(512)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val ip = packet.address.hostAddress ?: continue
                if (ip != getLocalIp()) discoveredPeers.add(ip)
            }
        }
    }

    private fun broadcastPresence() {
        scope.launch {
            val socket = DatagramSocket().apply { broadcast = true }
            val msg = "IAM_HERE".toByteArray()
            while (isActive) {
                try {
                    val addr = InetAddress.getByName("255.255.255.255")
                    socket.send(DatagramPacket(msg, msg.size, addr, DISCOVERY_PORT))
                } catch (e: Exception) { }
                delay(15_000)
            }
        }
    }

    // --- SECURITY & HELPERS ---

    private fun sign(json: JSONObject): String {
        val content = JSONObject(json.toString()).apply { remove("signature") }.toString()
        return Base64.encodeToString(CryptoManager.sign(content.toByteArray()), Base64.NO_WRAP)
    }

    private fun verify(json: JSONObject): Boolean {
        return try {
            val from = json.getString("from")
            val timestamp = json.getLong("timestamp")
            val signature = json.getString("signature")

            // Проверка времени и защита от повтора
            if (abs(System.currentTimeMillis() - timestamp) > MAX_MESSAGE_AGE_MS) return false
            val msgId = "$from$timestamp"
            if (seenTimestamps.containsKey(msgId)) return false
            seenTimestamps[msgId] = timestamp

            val content = JSONObject(json.toString()).apply { remove("signature") }.toString()
            val pubKey = CryptoManager.getPublicKeyByHash(from) ?: return false
            
            CryptoManager.verify(content.toByteArray(), Base64.decode(signature, Base64.NO_WRAP), pubKey)
        } catch (e: Exception) { false }
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

    private fun normalizePhoneNumber(phone: String): String = phone.replace(Regex("[^0-9+]"), "")

    private fun sha256(input: String): String = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun getLocalIp(): String? = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
    } catch (e: Exception) { null }

    private suspend fun makeCloudRequest(action: String, body: JSONObject): Boolean {
        return try {
            val conn = URL("$CLOUD_API_URL?action=$action").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.outputStream.write(body.toString().toByteArray())
            val resp = conn.inputStream.bufferedReader().readText()
            JSONObject(resp).optBoolean("success", false)
        } catch (e: Exception) { false }
    }
}
