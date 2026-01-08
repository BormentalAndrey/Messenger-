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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

class IdentityRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("p2p_identity", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val P2P_PORT = 8888
    private val DISCOVERY_PORT = 8889
    private val MAX_MESSAGE_AGE = 30_000L

    /** MULTICAST LISTENERS (важно!) */
    private val listeners = CopyOnWriteArrayList<(String, String, String) -> Unit>()

    fun addSignalingListener(cb: (type: String, data: String, fromIp: String) -> Unit) {
        listeners.add(cb)
    }

    fun removeSignalingListener(cb: (String, String, String) -> Unit) {
        listeners.remove(cb)
    }

    init {
        startUdpListener()
        startDiscovery()
        broadcastPresence()
    }

    /* ================= ID ================= */

    fun getMyId(): String {
        return getMyPublicKeyHash()
    }

    private fun getMyPublicKeyHash(): String {
        val cached = prefs.getString("my_id", null)
        if (cached != null) return cached

        if (!CryptoManager.isKeyReady()) CryptoManager.generateKeys(context)
        val hash = sha256(CryptoManager.getMyPublicKeyStr())
        prefs.edit().putString("my_id", hash).apply()
        return hash
    }

    /* ================= DHT ================= */

    fun findPeerInDHT(keyHash: String) {
        val json = JSONObject().apply {
            put("type", "FIND")
            put("key", keyHash)
            put("from", getMyId())
            put("timestamp", System.currentTimeMillis())
        }
        signAndBroadcast(json)
    }

    fun sendBroadcastPacket(type: String, key: String, value: String) {
        val json = JSONObject().apply {
            put("type", type)
            put("key", key)
            put("value", value)
            put("from", getMyId())
            put("timestamp", System.currentTimeMillis())
        }
        signAndBroadcast(json)
    }

    /* ================= SIGNALING ================= */

    fun sendSignaling(targetIp: String, type: String, data: String) {
        val json = JSONObject().apply {
            put("type", type)
            put("data", data)
            put("from", getMyId())
            put("timestamp", System.currentTimeMillis())
        }
        signAndSend(targetIp, json)
    }

    fun sendSignalingData(targetIp: String, type: String, bytes: ByteArray) {
        sendSignaling(targetIp, type, Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    /* ================= UDP CORE ================= */

    private fun startUdpListener() = scope.launch {
        val socket = DatagramSocket(P2P_PORT)
        val buffer = ByteArray(65535)

        while (isActive) {
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            val ip = packet.address.hostAddress ?: continue
            val msg = String(packet.data, 0, packet.length)

            try {
                val json = JSONObject(msg)
                if (!verify(json)) continue

                val type = json.getString("type")
                val data = json.optString("data")

                if (type == "OFFER") {
                    launchCall(ip, data)
                } else {
                    listeners.forEach { it(type, data, ip) }
                }
            } catch (_: Exception) {}
        }
    }

    private fun startDiscovery() = scope.launch {
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(DISCOVERY_PORT))
        }
        val buf = ByteArray(256)
        while (isActive) {
            socket.receive(DatagramPacket(buf, buf.size))
        }
    }

    private fun broadcastPresence() = scope.launch {
        val socket = DatagramSocket().apply { broadcast = true }
        val msg = "IAM".toByteArray()
        while (isActive) {
            socket.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT))
            delay(15_000)
        }
    }

    /* ================= HELPERS ================= */

    private fun signAndSend(ip: String, json: JSONObject) {
        json.put("signature", sign(json))
        sendUdp(ip, json.toString())
    }

    private fun signAndBroadcast(json: JSONObject) {
        json.put("signature", sign(json))
        sendUdp("255.255.255.255", json.toString())
    }

    private fun sendUdp(ip: String, msg: String) {
        scope.launch {
            try {
                val addr = InetAddress.getByName(ip)
                DatagramSocket().use {
                    it.send(DatagramPacket(msg.toByteArray(), msg.length, addr, P2P_PORT))
                }
            } catch (_: Exception) {}
        }
    }

    private fun sign(json: JSONObject): String {
        val clean = JSONObject(json.toString()).apply { remove("signature") }.toString()
        return Base64.encodeToString(CryptoManager.sign(clean.toByteArray()), Base64.NO_WRAP)
    }

    private fun verify(json: JSONObject): Boolean {
        val ts = json.getLong("timestamp")
        if (abs(System.currentTimeMillis() - ts) > MAX_MESSAGE_AGE) return false
        return true
    }

    private fun sha256(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun launchCall(ip: String, sdp: String) {
        context.startActivity(
            Intent(context, CallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("targetIp", ip)
                .putExtra("remoteSdp", sdp)
                .putExtra("isIncoming", true)
        )
    }
}
