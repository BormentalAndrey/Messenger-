package com.kakdela.p2p.data

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Base64
import android.util.Log
import com.kakdela.p2p.api.MyServerApi
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.*
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

class IdentityRepository(private val context: Context) {

    private val TAG = "IdentityRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()

    private val listeners =
        CopyOnWriteArrayList<(type: String, data: String, fromIp: String, fromId: String) -> Unit>()

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    private var mainSocket: DatagramSocket? = null

    init {
        CryptoManager.generateKeysIfNeeded(context)
        startMainSocket()
    }

    fun stopP2PNode() {
        scope.cancel()
        mainSocket?.close()
    }

    fun getMyId(): String = sha256(CryptoManager.getMyPublicKeyStr())
    fun getMyPublicKeyStr(): String = CryptoManager.getMyPublicKeyStr()
    fun savePeerPublicKey(hash: String, key: String) = CryptoManager.savePeerPublicKey(hash, key)

    fun addListener(listener: (String, String, String, String) -> Unit) = listeners.add(listener)
    fun removeListener(listener: (String, String, String, String) -> Unit) = listeners.remove(listener)

    fun sendSignaling(targetIp: String, type: String, data: String) = scope.launch {
        val json = JSONObject().apply {
            put("type", type)
            put("data", data)
            put("from", getMyId())
            put("pubkey", getMyPublicKeyStr())
            put("timestamp", System.currentTimeMillis())
            put("signature", sign(JSONObject().apply { put("type", type); put("data", data) }))
        }
        sendUdp(targetIp, json.toString())
    }

    suspend fun findPeerInDHT(hash: String): UserPayload? {
        return try {
            api.findPeer(mapOf("hash" to hash)).userNode
        } catch (_: Exception) { null }
    }

    fun generateUserHash(email: String, phone: String): String =
        sha256("$email:$phone")

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    private suspend fun sendUdp(ip: String, message: String, broadcast: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = broadcast
                    val data = message.toByteArray()
                    val packet = DatagramPacket(data, data.size, InetAddress.getByName(ip), 8888)
                    socket.send(packet)
                }
                true
            } catch (_: Exception) { false }
        }

    private fun startMainSocket() = scope.launch {
        try {
            mainSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(8888))
            }
        } catch (e: Exception) {
            Log.e(TAG, "UDP bind failed", e)
        }
    }

    private fun sign(json: JSONObject): String {
        val clean = JSONObject(json.toString()).apply { remove("signature") }
        val sig = CryptoManager.sign(clean.toString().toByteArray())
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }
}
