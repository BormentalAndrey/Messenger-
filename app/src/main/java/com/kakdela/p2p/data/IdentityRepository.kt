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

    private val prefs =
        context.getSharedPreferences("p2p_identity", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /* ----------------------------------------------------
       INTERNAL STATE
     ---------------------------------------------------- */

    private val localDhtSlice =
        ConcurrentHashMap<String, Pair<String, Long>>() // key -> (value, expire)

    private val discoveredPeers = CopyOnWriteArraySet<String>()
    private val seenTimestamps = ConcurrentHashMap<String, Long>()

    var onSignalingMessageReceived:
            ((type: String, data: String, fromIp: String) -> Unit)? = null

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

    /* ----------------------------------------------------
       PUBLIC IDENTITY API
     ---------------------------------------------------- */

    fun getMyId(): String {
        prefs.getString("my_pub_key_hash", null)?.let { return it }

        val pubKey = CryptoManager.getMyPublicKeyStr()
        val hash = sha256(pubKey)
        prefs.edit().putString("my_pub_key_hash", hash).apply()
        return hash
    }

    fun getMyPublicKeyHash(): String = getMyId()

    fun isKeyReady(): Boolean = CryptoManager.isKeyReady()

    /* ----------------------------------------------------
       BACKWARD COMPATIBILITY
     ---------------------------------------------------- */

    companion object {
        @JvmStatic
        fun isKeyReady(): Boolean = CryptoManager.isKeyReady()
    }

    fun sendToSingleAddress(ip: String, message: String) {
        sendUdp(ip, message)
    }

    fun sendSignalingData(targetIp: String, type: String, data: String) {
        sendSignaling(targetIp, type, data)
    }

    suspend fun updateEmailBackup(email: String, pass: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val emailHash = sha256(email.lowercase().trim())
                val backup = "BACKUP_${getMyId()}"
                sendBroadcastPacket("STORE", emailHash, backup)
                true
            } catch (e: Exception) {
                false
            }
        }

    /* ----------------------------------------------------
       IDENTITY PUBLISHING
     ---------------------------------------------------- */

    suspend fun publishIdentity(
        phoneNumber: String,
        name: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val publicKey = CryptoManager.getMyPublicKeyStr()
            val phoneHash = sha256(phoneNumber)

            prefs.edit().apply {
                putString("my_phone", phoneNumber)
                putString("my_name", name)
                putString("my_pub_key", publicKey)
                apply()
            }

            sendBroadcastPacket("STORE", phoneHash, publicKey)
            true
        } catch (e: Exception) {
            false
        }
    }

    /* ----------------------------------------------------
       DHT & SIGNALING
     ---------------------------------------------------- */

    fun findPeerInDHT(key: String) {
        val json = basePayload("FIND").apply {
            put("key", key)
        }
        discoveredPeers.forEach { sendUdp(it, json.toString()) }
    }

    fun sendSignaling(targetIp: String, type: String, data: String) {
        val json = basePayload(type).apply {
            put("data", data)
        }
        sendUdp(targetIp, json.toString())
    }

    private fun sendBroadcastPacket(type: String, key: String, value: String) {
        val json = basePayload(type).apply {
            put("key", key)
            put("value", value)
        }
        discoveredPeers.forEach { sendUdp(it, json.toString()) }
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
                    val raw = String(packet.data, 0, packet.length)
                    val json = JSONObject(raw)

                    if (!verify(json)) continue

                    when (json.getString("type")) {
                        "STORE" -> {
                            localDhtSlice[json.getString("key")] =
                                json.getString("value") to
                                        (System.currentTimeMillis() + DHT_TTL_MS)
                        }

                        "FIND" -> {
                            val key = json.getString("key")
                            localDhtSlice[key]?.let { (value, _) ->
                                sendSignaling(
                                    senderIp,
                                    "STORE_RESPONSE",
                                    "$key:$value"
                                )
                            }
                        }

                        "OFFER" ->
                            launchIncomingCall(senderIp, json.optString("data"))

                        "ANSWER", "ICE", "CANDIDATE", "STORE_RESPONSE", "CHAT_MSG" ->
                            onSignalingMessageReceived?.invoke(
                                json.getString("type"),
                                json.optString("data"),
                                senderIp
                            )
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
                DatagramSocket().use { socket ->
                    val data = message.toByteArray()
                    val addr = InetAddress.getByName(ip)
                    socket.send(
                        DatagramPacket(data, data.size, addr, P2P_PORT)
                    )
                }
            } catch (e: Exception) {
                Log.e("P2P_NET", "Send error: ${e.message}")
            }
        }
    }

    /* ----------------------------------------------------
       DISCOVERY
     ---------------------------------------------------- */

    private fun startDiscovery() {
        scope.launch {
            try {
                val socket = DatagramSocket(DISCOVERY_PORT)
                val buffer = ByteArray(512)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    packet.address.hostAddress?.let { ip ->
                        if (ip != getLocalIp()) discoveredPeers.add(ip)
                    }
                }
            } catch (_: Exception) {
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
                    socket.send(
                        DatagramPacket(msg, msg.size, addr, DISCOVERY_PORT)
                    )
                } catch (_: Exception) {
                }
                delay(15_000)
            }
        }
    }

    /* ----------------------------------------------------
       SECURITY
     ---------------------------------------------------- */

    private fun basePayload(type: String): JSONObject =
        JSONObject().apply {
            put("type", type)
            put("from", getMyPublicKeyHash())
            put("timestamp", System.currentTimeMillis())
            put("signature", sign(this))
        }

    private fun sign(json: JSONObject): String {
        val clone = JSONObject(json.toString()).apply { remove("signature") }
        val sig = CryptoManager.sign(clone.toString().toByteArray())
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }

    private fun verify(json: JSONObject): Boolean =
        try {
            val signature = json.getString("signature")
            val from = json.getString("from")
            val timestamp = json.getLong("timestamp")

            if (abs(System.currentTimeMillis() - timestamp) > MAX_MESSAGE_AGE_MS) return false
            if (seenTimestamps.putIfAbsent(from + timestamp, timestamp) != null) return false

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

    /* ----------------------------------------------------
       CALLS & MAINTENANCE
     ---------------------------------------------------- */

    private fun launchIncomingCall(ip: String, sdp: String) {
        context.startActivity(
            Intent(context, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("targetIp", ip)
                putExtra("isIncoming", true)
                putExtra("remoteSdp", sdp)
            }
        )
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

    private fun getLocalIp(): String? =
        try {
            val wm =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
        } catch (_: Exception) {
            null
        }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
