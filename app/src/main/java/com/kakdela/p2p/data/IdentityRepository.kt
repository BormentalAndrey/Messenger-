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

    private val TAG = "P2P_Repo"
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

    fun generateUserHash(phone: String, email: String, password: String): String {
        val combined = (phone.trim() + email.trim().lowercase() + password).toByteArray()
        return MessageDigest.getInstance("SHA-256")
            .digest(combined)
            .joinToString("") { "%02x".format(it) }
    }

    /* ===================== INIT ===================== */

    init {
        startUdpListener()
        startDiscoveryListener()
        startPresenceBroadcast()
    }

    /**
     * Важно вызвать этот метод при уничтожении приложения/сервиса,
     * чтобы остановить все фоновые процессы и освободить порты.
     */
    fun onDestroy() {
        scope.cancel()
    }

    /* ===================== DHT / MESSAGING ===================== */

    fun findPeerInDHT(keyHash: String) {
        val json = JSONObject().apply {
            put("type", "FIND")
            put("key", keyHash)
        }
        broadcast(json)
    }

    fun sendBroadcastPacket(type: String, key: String, value: String) {
        val json = JSONObject().apply {
            put("type", type)
            put("key", key)
            put("value", value)
        }
        broadcast(json)
    }

    fun sendSignaling(ip: String, type: String, data: String) {
        val json = JSONObject().apply {
            put("type", type)
            put("data", data)
        }
        send(ip, json)
    }

    fun sendSignalingData(ip: String, type: String, bytes: ByteArray) {
        sendSignaling(
            ip,
            type,
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        )
    }

    /* ===================== UDP LISTENER (FIXED) ===================== */

    private fun startUdpListener() = scope.launch {
        try {
            // Использование порта с флагом reuseAddress для исправления EADDRINUSE
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(P2P_PORT))
            }.use { socket ->
                Log.d(TAG, "UDP Listener started on port $P2P_PORT")
                val buffer = ByteArray(65_535)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        socket.receive(packet)

                        val fromIp = packet.address.hostAddress ?: continue
                        val msg = String(packet.data, 0, packet.length)

                        val json = JSONObject(msg)
                        if (!verify(json)) continue

                        val type = json.getString("type")
                        val data = json.optString("data")

                        withContext(Dispatchers.Main) {
                            if (type == "OFFER") {
                                launchCall(fromIp, data)
                            } else {
                                listeners.forEach { it(type, data, fromIp) }
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive) Log.e(TAG, "Packet processing error", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical: Could not bind P2P_PORT $P2P_PORT", e)
        }
    }

    /* ===================== DISCOVERY (FIXED) ===================== */

    private fun startDiscoveryListener() = scope.launch {
        try {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(DISCOVERY_PORT))
            }.use { socket ->
                Log.d(TAG, "Discovery Listener started on port $DISCOVERY_PORT")
                val buf = ByteArray(128)
                val packet = DatagramPacket(buf, buf.size)
                while (isActive) {
                    try {
                        socket.receive(packet)
                        // Здесь можно добавить логику обработки обнаружения других узлов
                    } catch (e: Exception) {
                        if (isActive) Log.e(TAG, "Discovery receive error", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical: Could not bind DISCOVERY_PORT $DISCOVERY_PORT", e)
        }
    }

    private fun startPresenceBroadcast() = scope.launch {
        while (isActive) {
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val msg = "IAM".toByteArray()
                    val packet = DatagramPacket(
                        msg,
                        msg.size,
                        InetAddress.getByName("255.255.255.255"),
                        DISCOVERY_PORT
                    )
                    socket.send(packet)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Presence broadcast error", e)
            }
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

    private fun enrich(json: JSONObject) {
        json.put("from", getMyId())
        json.put("timestamp", System.currentTimeMillis())
        json.put("signature", sign(json))
    }

    private fun sendUdp(ip: String, msg: String) {
        scope.launch {
            try {
                DatagramSocket().use { socket ->
                    // Если вещаем на broadcast адрес, нужно включить флаг
                    if (ip == "255.255.255.255") {
                        socket.broadcast = true
                    }
                    val bytes = msg.toByteArray()
                    socket.send(
                        DatagramPacket(
                            bytes,
                            bytes.size,
                            InetAddress.getByName(ip),
                            P2P_PORT
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send UDP error to $ip", e)
            }
        }
    }

    private fun verify(json: JSONObject): Boolean {
        val ts = json.optLong("timestamp", 0L)
        // Проверка на "протухание" пакета (защита от replay-атак)
        return abs(System.currentTimeMillis() - ts) <= MAX_DRIFT
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
        val intent = Intent(context, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("targetIp", ip)
            putExtra("remoteSdp", sdp)
            putExtra("isIncoming", true)
        }
        context.startActivity(intent)
    }
}
