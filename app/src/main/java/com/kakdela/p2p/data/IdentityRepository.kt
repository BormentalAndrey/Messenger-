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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

class IdentityRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val P2P_PORT = 8888
    private val DISCOVERY_PORT = 8889
    private val MAX_DRIFT = 30_000L

    /* ===================== SIGNAL BUS ===================== */

    private val listeners = CopyOnWriteArrayList<(String, String, String) -> Unit>()

    fun addListener(cb: (type: String, data: String, fromIp: String) -> Unit) {
        listeners += cb
    }

    fun removeListener(cb: (String, String, String) -> Unit) {
        listeners -= cb
    }

    /* ===================== ID ===================== */

    fun getMyId(): String {
        if (!CryptoManager.isKeyReady()) CryptoManager.generateKeys(context)
        return sha256(CryptoManager.getMyPublicKeyStr())
    }

    /* ===================== INIT ===================== */

    init {
        startUdpListener()
        startDiscovery()
        broadcastPresence()
    }

    /* ===================== DHT ===================== */

    fun findPeerInDHT(keyHash: String) {
        broadcast(json {
            put("type", "FIND")
            put("key", keyHash)
        })
    }

    fun sendBroadcastPacket(type: String, key: String, value: String) {
        broadcast(json {
            put("type", type)
            put("key", key)
            put("value", value)
        })
    }

    /* ===================== SIGNALING ===================== */

    fun sendSignaling(ip: String, type: String, data: String) {
        send(ip, json {
            put("type", type)
            put("data", data)
        })
    }

    fun sendSignalingData(ip: String, type: String, bytes: ByteArray) {
        sendSignaling(ip, type, Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    /* ===================== UDP ===================== */

    private fun startUdpListener() = scope.launch {
        val socket = DatagramSocket(P2P_PORT)
        val buf = ByteArray(65535)

        while (isActive) {
            val packet = DatagramPacket(buf, buf.size)
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
            } catch (e: Exception) {
                Log.e("P2P", "Bad packet", e)
            }
        }
    }

    private fun startDiscovery() = scope.launch {
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(DISCOVERY_PORT))
        }
        val buf = ByteArray(128)
        while (isActive) socket.receive(DatagramPacket(buf, buf.size))
    }

    private fun broadcastPresence() = scope.launch {
        val socket = DatagramSocket().apply { broadcast = true }
        val msg = "IAM".toByteArray()
        while (isActive) {
            socket.send(DatagramPacket(msg, msg.size,
                InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT))
            delay(15_000)
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

    private fun enrich(j: JSONObject) {
        j.put("from", getMyId())
        j.put("timestamp", System.currentTimeMillis())
        j.put("signature", sign(j))
    }

    private fun sendUdp(ip: String, msg: String) {
        scope.launch {
            try {
                DatagramSocket().use {
                    it.send(DatagramPacket(
                        msg.toByteArray(),
                        msg.length,
                        InetAddress.getByName(ip),
                        P2P_PORT
                    ))
                }
            } catch (_: Exception) {}
        }
    }

    private fun verify(j: JSONObject): Boolean {
        val ts = j.getLong("timestamp")
        if (abs(System.currentTimeMillis() - ts) > MAX_DRIFT) return false
        return true
    }

    private fun sign(j: JSONObject): String {
        val clean = JSONObject(j.toString()).apply { remove("signature") }.toString()
        return Base64.encodeToString(
            CryptoManager.sign(clean.toByteArray()),
            Base64.NO_WRAP
        )
    }

    private fun sha256(s: String) =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

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
