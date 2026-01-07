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

    // DHT: Хэш ключа -> (Зашифрованные данные, TTL)
    private val localDhtSlice = ConcurrentHashMap<String, Pair<String, Long>>()
    private val discoveredPeers = CopyOnWriteArraySet<String>()
    private val seenTimestamps = ConcurrentHashMap<String, Long>()

    // Коллбэк для внешних модулей (CallActivity, FileWorker)
    var onSignalingMessageReceived: ((type: String, data: String, fromIp: String) -> Unit)? = null

    private val P2P_PORT = 8888
    private val DISCOVERY_PORT = 8889
    private val MAX_MESSAGE_AGE_MS = 30_000
    private val DHT_TTL_MS = 48 * 60 * 60 * 1000L // 48 часов для чужих записей

    init {
        startListening()
        startDiscovery()
        broadcastPresence()
        cleanupWorker()
    }

    /* ----------------------------------------------------
       PUBLIC API (IDENTITY & RECOVERY)
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

            // Рассылаем свой ID по сети для индексации
            sendBroadcastPacket("STORE", phoneHash, publicKey)
            true
        } catch (e: Exception) { false }
    }

    suspend fun updateEmailBackup(email: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val emailHash = hash(email.lowercase())
            val encryptedVault = CryptoManager.exportEncryptedKeyset(pass)
            
            val identityJson = JSONObject().apply {
                put("name", prefs.getString("my_name", "User"))
                put("vault", encryptedVault)
            }.toString()

            sendBroadcastPacket("STORE", emailHash, identityJson)
            true
        } catch (e: Exception) { false }
    }

    /* ----------------------------------------------------
       SIGNALING (CALLS & FILES)
     ---------------------------------------------------- */

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
        discoveredPeers.forEach { sendUdp(it, payload.toString()) }
    }

    /* ----------------------------------------------------
       NETWORK ENGINE
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
                    val json = JSONObject(String(packet.data, 0, packet.length))

                    // Проверка подписи и времени
                    if (!verify(json)) continue

                    val type = json.getString("type")
                    
                    when (type) {
                        "STORE" -> {
                            val key = json.getString("key")
                            val value = json.getString("value")
                            localDhtSlice[key] = Pair(value, System.currentTimeMillis() + DHT_TTL_MS)
                        }
                        "FIND" -> {
                            val key = json.getString("key")
                            localDhtSlice[key]?.let { (value, _) ->
                                sendSignaling(senderIp, "STORE_RESPONSE", value)
                            }
                        }
                        "OFFER" -> launchIncomingCall(senderIp, json.optString("data"))
                        "ANSWER", "CANDIDATE", "FILE_SIGNAL", "STORE_RESPONSE" ->
                            onSignalingMessageReceived?.invoke(type, json.optString("data"), senderIp)
                    }

                } catch (e: Exception) {
                    Log.e("P2P", "Receive error: ${e.message}")
                }
            }
        }
    }

    private fun sendUdp(ip: String, message: String) {
        scope.launch {
            try {
                val socket = DatagramSocket()
                val data = message.toByteArray()
                socket.send(DatagramPacket(data, data.size, InetAddress.getByName(ip), P2P_PORT))
                socket.close()
            } catch (e: Exception) { Log.e("P2P", "Send error to $ip") }
        }
    }

    /* ----------------------------------------------------
       DISCOVERY & SECURITY
     ---------------------------------------------------- */

    private fun startDiscovery() {
        scope.launch {
            val socket = DatagramSocket(DISCOVERY_PORT)
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
                socket.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT))
                delay(15_000)
            }
        }
    }

    private fun sign(json: JSONObject): String {
        val clone = JSONObject(json.toString()).apply { remove("signature") }
        val signature = CryptoManager.sign(clone.toString().toByteArray())
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    private fun verify(json: JSONObject): Boolean {
        try {
            val signature = json.optString("signature", "")
            val pubKeyHash = json.optString("from", "")
            if (signature.isEmpty() || pubKeyHash.isEmpty()) return false

            val timestamp = json.optLong("timestamp", 0)
            if (kotlin.math.abs(System.currentTimeMillis() - timestamp) > MAX_MESSAGE_AGE_MS) return false

            val replayKey = pubKeyHash + timestamp
            if (seenTimestamps.putIfAbsent(replayKey, timestamp) != null) return false

            val clone = JSONObject(json.toString()).apply { remove("signature") }
            val pubKey = CryptoManager.getPublicKeyByHash(pubKeyHash) ?: return false

            return CryptoManager.verify(
                clone.toString().toByteArray(),
                Base64.decode(signature, Base64.NO_WRAP),
                pubKey
            )
        } catch (e: Exception) { return false }
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
            Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
        } catch (e: Exception) { null }
    }

    private fun hash(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    fun getMyPublicKeyHash(): String = hash(prefs.getString("my_pub_key", "") ?: "")
}

