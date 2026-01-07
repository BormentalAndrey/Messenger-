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

class IdentityRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("p2p_identity", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Локальная копия DHT: Key (Hash) -> Pair(Value, ExpirationTimestamp)
    private val localDhtSlice = ConcurrentHashMap<String, Pair<String, Long>>()
    private val discoveredPeers = CopyOnWriteArraySet<String>()
    private val seenTimestamps = ConcurrentHashMap<String, Long>()

    // Коллбэк для UI и менеджеров (теперь передает полные данные сообщения)
    var onSignalingMessageReceived: ((type: String, data: String, fromIp: String) -> Unit)? = null

    private val P2P_PORT = 8888
    private val DISCOVERY_PORT = 8889
    private val MAX_MESSAGE_AGE_MS = 30_000
    private val DHT_TTL_MS = 48 * 60 * 60 * 1000L // 48 часов хранения

    init {
        startListening()
        startDiscovery()
        broadcastPresence()
        cleanupWorker()
    }

    /* ----------------------------------------------------
       PUBLIC IDENTITY API
     ---------------------------------------------------- */

    fun getMyId(): String {
        val stored = prefs.getString("my_pub_key_hash", null)
        if (stored != null) return stored
        
        val currentPubKey = CryptoManager.getMyPublicKeyStr()
        val h = hash(currentPubKey)
        prefs.edit().putString("my_pub_key_hash", h).apply()
        return h
    }

    fun getMyPublicKeyHash(): String = getMyId()

    /* ----------------------------------------------------
       P2P OPERATIONS & DHT
     ---------------------------------------------------- */

    suspend fun publishIdentity(phoneNumber: String, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val publicKey = CryptoManager.getMyPublicKeyStr()
            val phoneHash = hash(phoneNumber)

            prefs.edit().apply {
                putString("my_phone", phoneNumber)
                putString("my_name", name)
                putString("my_pub_key", publicKey)
                apply()
            }

            // Рассылаем свою личность соседям для сохранения в DHT
            sendBroadcastPacket("STORE", phoneHash, publicKey)
            true
        } catch (e: Exception) { false }
    }

    fun findPeerInDHT(key: String) {
        val payload = JSONObject().apply {
            put("type", "FIND")
            put("key", key)
            put("from", getMyPublicKeyHash())
            put("timestamp", System.currentTimeMillis())
        }
        payload.put("signature", sign(payload))
        val msg = payload.toString()
        
        // Отправляем запрос всем известным узлам
        discoveredPeers.forEach { ip -> sendUdp(ip, msg) }
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

    private fun sendBroadcastPacket(type: String, key: String, value: String) {
        val payload = JSONObject().apply {
            put("type", type)
            put("key", key)
            put("value", value)
            put("from", getMyPublicKeyHash())
            put("timestamp", System.currentTimeMillis())
        }
        payload.put("signature", sign(payload))
        val msg = payload.toString()
        discoveredPeers.forEach { sendUdp(it, msg) }
    }

    /* ----------------------------------------------------
       NETWORK ENGINE (UDP)
     ---------------------------------------------------- */

    private fun startListening() {
        scope.launch {
            val socket = DatagramSocket(P2P_PORT)
            val buffer = ByteArray(65535)

            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val senderIp = packet.address.hostAddress ?: continue
                    val rawData = String(packet.data, 0, packet.length)
                    val json = JSONObject(rawData)

                    if (!verify(json)) continue

                    val type = json.getString("type")

                    when (type) {
                        "STORE" -> {
                            val key = json.getString("key")
                            val value = json.getString("value")
                            localDhtSlice[key] = Pair(value, System.currentTimeMillis() + DHT_TTL_MS)
                            Log.d("P2P_DHT", "Stored data for key: $key")
                        }
                        "FIND" -> {
                            val key = json.getString("key")
                            localDhtSlice[key]?.let { (value, _) ->
                                // Формируем ответ: "искомый_хеш:публичный_ключ"
                                val responseData = "$key:$value"
                                sendSignaling(senderIp, "STORE_RESPONSE", responseData)
                            }
                        }
                        "OFFER" -> launchIncomingCall(senderIp, json.optString("data"))
                        "ANSWER", "ICE", "CANDIDATE", "STORE_RESPONSE", "CHAT_MSG" -> {
                            val msgData = json.optString("data")
                            onSignalingMessageReceived?.invoke(type, msgData, senderIp)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("P2P_NET", "Listen error: ${e.message}")
                }
            }
        }
    }

    fun sendUdp(ip: String, message: String) {
        scope.launch {
            try {
                val socket = DatagramSocket()
                val data = message.toByteArray()
                val address = InetAddress.getByName(ip)
                socket.send(DatagramPacket(data, data.size, address, P2P_PORT))
                socket.close()
            } catch (e: Exception) {
                Log.e("P2P_NET", "Send error to $ip: ${e.message}")
            }
        }
    }

    /* ----------------------------------------------------
       DISCOVERY & SECURITY
     ---------------------------------------------------- */

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
                        if (discoveredPeers.add(ip)) {
                            Log.d("P2P_DISCOVERY", "New peer found: $ip")
                        }
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun broadcastPresence() {
        scope.launch {
            val socket = DatagramSocket().apply { broadcast = true }
            val msg = "IAM_HERE".toByteArray()
            while (isActive) {
                try {
                    val broadcastAddr = InetAddress.getByName("255.255.255.255")
                    socket.send(DatagramPacket(msg, msg.size, broadcastAddr, DISCOVERY_PORT))
                } catch (e: Exception) { }
                delay(15_000) // Оповещаем о себе каждые 15 сек
            }
        }
    }

    private fun sign(json: JSONObject): String {
        val clone = JSONObject(json.toString()).apply { remove("signature") }
        val signature = CryptoManager.sign(clone.toString().toByteArray())
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    private fun verify(json: JSONObject): Boolean {
        return try {
            val signature = json.getString("signature")
            val pubKeyHash = json.getString("from")
            val timestamp = json.getLong("timestamp")

            // Проверка "свежести" сообщения
            if (kotlin.math.abs(System.currentTimeMillis() - timestamp) > MAX_MESSAGE_AGE_MS) return false
            
            // Защита от повторов (Replay attack)
            if (seenTimestamps.putIfAbsent(pubKeyHash + timestamp, timestamp) != null) return false

            val clone = JSONObject(json.toString()).apply { remove("signature") }
            val pubKey = CryptoManager.getPublicKeyByHash(pubKeyHash) ?: return false

            CryptoManager.verify(
                clone.toString().toByteArray(),
                Base64.decode(signature, Base64.NO_WRAP),
                pubKey
            )
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

    private fun getLocalIp(): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            Formatter.formatIpAddress(ip)
        } catch (e: Exception) { null }
    }

    private fun hash(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}

