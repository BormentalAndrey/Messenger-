package com.kakdela.p2p.data

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.kakdela.p2p.security.CryptoManager
import com.kakdela.p2p.ui.call.CallActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

class IdentityRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val P2P_PORT = 8888
    private val DISCOVERY_PORT = 8889
    private val MAX_DRIFT = 30_000L

    /* ===================== SIGNAL BUS ===================== */

    private val listeners =
        CopyOnWriteArrayList<(type: String, data: String, fromIp: String) -> Unit>()

    fun addListener(cb: (String, String, String) -> Unit) {
        listeners += cb
    }

    fun removeListener(cb: (String, String, String) -> Unit) {
        listeners -= cb
    }

    /* ===================== ID ===================== */

    fun getMyId(): String {
        if (!CryptoManager.isKeyReady()) {
            CryptoManager.generateKeys(context)
        }
        return sha256(CryptoManager.getMyPublicKeyStr())
    }

    /* ===================== INIT ===================== */

    init {
        startUdpListener()
        startDiscoveryListener()
        startPresenceBroadcast()
    }

    /* ===================== DHT ===================== */

    fun findPeerInDHT(keyHash: String) {
        val json = JSONObject()
        json.put("type", "FIND")
        json.put("key", keyHash)
        broadcast(json)
    }

    fun sendBroadcastPacket(type: String, key: String, value: String) {
        val json = JSONObject()
        json.put("type", type)
        json.put("key", key)
        json.put("value", value)
        broadcast(json)
    }

    /* ===================== SIGNALING ===================== */

    fun sendSignaling(ip: String, type: String, data: String) {
        val json = JSONObject()
        json.put("type", type)
        json.put("data", data)
        send(ip, json)
    }

    fun sendSignalingData(ip: String, type: String, bytes: ByteArray) {
        sendSignaling(
            ip,
            type,
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        )
    }

    /* ===================== UDP LISTENER ===================== */

    private fun startUdpListener() = scope.launch {
        DatagramSocket(P2P_PORT).use { socket ->
            val buffer = ByteArray(65_535)

            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val fromIp = packet.address.hostAddress ?: continue
                    val msg = String(packet.data, 0, packet.length)

                    val json = JSONObject(msg)
                    if (!verify(json)) continue

                    val type = json.getString("type")
                    val data = json.optString("data")

                    if (type == "OFFER") {
                        launchCall(fromIp, data)
                    } else {
                        listeners.forEach { it(type, data, fromIp) }
                    }

                } catch (e: Exception) {
                    Log.e("P2P", "UDP packet error", e)
                }
            }
        }
    }

    /* ===================== DISCOVERY ===================== */

    private fun startDiscoveryListener() = scope.launch {
        DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(DISCOVERY_PORT))
        }.use { socket ->
            val buf = ByteArray(128)
            while (isActive) {
                socket.receive(DatagramPacket(buf, buf.size))
            }
        }
    }

    private fun startPresenceBroadcast() = scope.launch {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            val msg = "IAM".toByteArray()

            while (isActive) {
                try {
                    socket.send(
                        DatagramPacket(
                            msg,
                            msg.size,
                            InetAddress.getByName("255.255.255.255"),
                            DISCOVERY_PORT
                        )
                    )
                } catch (_: Exception) {
                }
                delay(15_000)
            }
        }
    }

    /* ===================== HELPERS ===================== */

    private fun send(ip: String, json: JSONObject) {
        enrich(json)
        sendUdp(ip, json.toString())
    }

    private fun broadcast(json: JSONObject) {
        enrich(json)
        sendUdp("255.255.255.255", json.toString())
    }

    private fun enrich(json: JSONObject) {
        json.put("from", getMyId())
        json.put("timestamp", System.currentTimeMillis())
        json.put("signature", sign(json))
    }

    private fun sendUdp(ip: String, msg: String) {
        scope.launch {
            try {
                DatagramSocket().use { socket ->
                    socket.send(
                        DatagramPacket(
                            msg.toByteArray(),
                            msg.length,
                            InetAddress.getByName(ip),
                            P2P_PORT
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun verify(json: JSONObject): Boolean {
        val ts = json.optLong("timestamp", 0L)
        if (abs(System.currentTimeMillis() - ts) > MAX_DRIFT) return false
        return true
    }

    private fun sign(json: JSONObject): String {
        val clean = JSONObject(json.toString()).apply {
            remove("signature")
        }.toString()

        return Base64.encodeToString(
            CryptoManager.sign(clean.toByteArray()),
            Base64.NO_WRAP
        )
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /* ===================== CALL ===================== */

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
