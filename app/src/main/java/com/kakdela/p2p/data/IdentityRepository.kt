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

class IdentityRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("p2p_identity", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val localDhtSlice = ConcurrentHashMap<String, Pair<String, Long>>()
    private val discoveredPeers = CopyOnWriteArraySet<String>()
    private val seenTimestamps = ConcurrentHashMap<String, Long>()

    var onSignalingMessageReceived: ((type: String, data: String, fromIp: String) -> Unit)? = null

    private val P2P_PORT = 8888
    private val DISCOVERY_PORT = 8889
    private val MAX_MESSAGE_AGE_MS = 30_000L
    private val DHT_TTL_MS = 48 * 60 * 60 * 1000L

    init {
        startListening()
        startDiscovery()
        broadcastPresence()
        cleanupWorker()
    }

    // --- Identity API ---
    fun getMyId(): String {
        prefs.getString("my_pub_key_hash", null)?.let { return it }
        val pubKey = CryptoManager.getMyPublicKeyStr()
        val hash = sha256(pubKey)
        prefs.edit().putString("my_pub_key_hash", hash).apply()
        return hash
    }

    fun isKeyReady(): Boolean = CryptoManager.isKeyReady()

    suspend fun publishIdentity(phoneNumber: String, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val publicKey = CryptoManager.getMyPublicKeyStr()
            val phoneHash = sha256(phoneNumber)
            prefs.edit().putString("my_phone", phoneNumber).putString("my_name", name).apply()
            sendBroadcastPacket("STORE", phoneHash, publicKey)
            true
        } catch (e: Exception) { false }
    }

    suspend fun updateEmailBackup(email: String, pass: String): Boolean {
        return publishIdentity(email, "Email_Backup_User")
    }

    // --- Network API ---
    fun sendSignaling(targetIp: String, type: String, data: String) {
        val payload = JSONObject().apply {
            put("type", type)
            put("data", data)
            put("from", getMyId())
            put("timestamp", System.currentTimeMillis())
        }
        payload.put("signature", sign(payload))
        sendUdp(targetIp, payload.toString())
    }

    fun findPeerInDHT(key: String) {
        val payload = JSONObject().apply {
            put("type", "FIND")
            put("key", key)
            put("from", getMyId())
            put("timestamp", System.currentTimeMillis())
        }
        payload.put("signature", sign(payload))
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
                    val json = JSONObject(String(packet.data, 0, packet.length))
                    if (!verify(json)) continue

                    val type = json.getString("type")
                    when (type) {
                        "STORE" -> localDhtSlice[json.getString("key")] = json.getString("value") to (System.currentTimeMillis() + DHT_TTL_MS)
                        "FIND" -> {
                            val key = json.getString("key")
                            localDhtSlice[key]?.let { (value, _) -> sendSignaling(senderIp, "STORE_RESPONSE", "$key:$value") }
                        }
                        "OFFER" -> launchIncomingCall(senderIp, json.optString("data"))
                        else -> onSignalingMessageReceived?.invoke(type, json.optString("data"), senderIp)
                    }
                } catch (e: Exception) { Log.e("P2P_NET", "Listen error", e) }
            }
        }
    }

    private fun sendUdp(ip: String, message: String) {
        scope.launch {
            try {
                DatagramSocket().use { it.send(DatagramPacket(message.toByteArray(), message.length, InetAddress.getByName(ip), P2P_PORT)) }
            } catch (e: Exception) { Log.e("P2P_NET", "Send error to $ip", e) }
        }
    }

    private fun sendBroadcastPacket(type: String, key: String, value: String) {
        val payload = JSONObject().apply {
            put("type", type); put("key", key); put("value", value)
            put("from", getMyId()); put("timestamp", System.currentTimeMillis())
        }
        payload.put("signature", sign(payload))
        discoveredPeers.forEach { sendUdp(it, payload.toString()) }
    }

    private fun startDiscovery() {
        scope.launch {
            val socket = DatagramSocket(DISCOVERY_PORT)
            val buffer = ByteArray(512)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                packet.address.hostAddress?.let { if (it != getLocalIp()) discoveredPeers.add(it) }
            }
        }
    }

    private fun broadcastPresence() {
        scope.launch {
            val socket = DatagramSocket().apply { broadcast = true }
            val msg = "IAM_HERE".toByteArray()
            while (isActive) {
                try { socket.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)) } catch (e: Exception) {}
                delay(15_000)
            }
        }
    }

    private fun sign(json: JSONObject): String {
        val clone = JSONObject(json.toString()).apply { remove("signature") }
        return Base64.encodeToString(CryptoManager.sign(clone.toString().toByteArray()), Base64.NO_WRAP)
    }

    private fun verify(json: JSONObject): Boolean {
        return try {
            val from = json.getString("from")
            val ts = json.getLong("timestamp")
            if (abs(System.currentTimeMillis() - ts) > MAX_MESSAGE_AGE_MS) return false
            val clone = JSONObject(json.toString()).apply { remove("signature") }
            val pubKey = CryptoManager.getPublicKeyByHash(from) ?: return false
            CryptoManager.verify(clone.toString().toByteArray(), Base64.decode(json.getString("signature"), Base64.NO_WRAP), pubKey)
        } catch (e: Exception) { false }
    }

    private fun launchIncomingCall(ip: String, sdp: String) {
        context.startActivity(Intent(context, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("targetIp", ip); putExtra("isIncoming", true); putExtra("remoteSdp", sdp)
        })
    }

    private fun cleanupWorker() {
        scope.launch {
            while (isActive) {
                delay(60_000)
                val now = System.currentTimeMillis()
                localDhtSlice.entries.removeIf { it.value.second < now }
            }
        }
    }

    private fun getLocalIp(): String? = try {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
    } catch (e: Exception) { null }

    private fun sha256(input: String): String = MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}

