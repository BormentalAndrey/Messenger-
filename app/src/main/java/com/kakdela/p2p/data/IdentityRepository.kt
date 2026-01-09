package com.kakdela.p2p.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.kakdela.p2p.api.MyServerApi
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.data.local.ChatDatabase
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

class IdentityRepository(private val context: Context) {
    private val TAG = "IdentityRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = CopyOnWriteArrayList<(String, String, String, String) -> Unit>()

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    private var mainSocket: DatagramSocket? = null
    private var isListening = false

    init {
        CryptoManager.init(context)
        startListening()
    }

    fun getMyId(): String = sha256(CryptoManager.getMyPublicKeyStr())
    fun getMyPublicKeyStr(): String = CryptoManager.getMyPublicKeyStr()
    
    // Добавлен метод, который искал EmailAuthScreen
    fun savePeerPublicKey(hash: String, key: String) {
        CryptoManager.savePeerPublicKey(hash, key)
    }

    fun addListener(listener: (String, String, String, String) -> Unit) = listeners.add(listener)
    fun removeListener(listener: (String, String, String, String) -> Unit) = listeners.remove(listener)

    fun findPeerInDHT(hash: String): Deferred<UserPayload?> = scope.async {
        try {
            // Исправлено: передаем карту параметров, которую ожидает MyServerApi
            val response = api.findPeer(payload = mapOf("hash" to hash))
            // Retrofit возвращает объект напрямую, если не используется Response<> обертка
            response.userNode 
        } catch (e: Exception) {
            Log.e(TAG, "DHT Lookup error: ${e.message}")
            null
        }
    }

    fun generateUserHash(phone: String, email: String, pass: String): String = sha256("$phone:$email:$pass")

    fun sendSignaling(targetIp: String, type: String, data: String) = scope.launch {
        if (targetIp.isBlank() || targetIp == "0.0.0.0") return@launch
        try {
            val json = JSONObject().apply {
                put("type", type)
                put("data", data)
                put("from", getMyId())
                put("pubkey", getMyPublicKeyStr())
                put("timestamp", System.currentTimeMillis())
            }
            val sig = CryptoManager.sign(json.toString().toByteArray())
            json.put("signature", Base64.encodeToString(sig, Base64.NO_WRAP))

            val buffer = json.toString().toByteArray()
            withContext(Dispatchers.IO) {
                val socket = DatagramSocket()
                val packet = DatagramPacket(buffer, buffer.size, InetAddress.getByName(targetIp), 8888)
                socket.send(packet)
                socket.close()
            }
        } catch (e: Exception) { Log.e(TAG, "UDP Send error", e) }
    }

    private fun startListening() = scope.launch(Dispatchers.IO) {
        if (isListening) return@launch
        try {
            mainSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(8888))
            }
            isListening = true
            val buffer = ByteArray(8192)
            while (isListening) {
                val packet = DatagramPacket(buffer, buffer.size)
                mainSocket?.receive(packet)
                val rawData = String(packet.data, 0, packet.length)
                handleIncomingPacket(rawData, packet.address.hostAddress ?: "")
            }
        } catch (e: Exception) { isListening = false }
    }

    private fun handleIncomingPacket(rawData: String, fromIp: String) {
        try {
            val json = JSONObject(rawData)
            val pubKey = json.getString("pubkey")
            val sig = Base64.decode(json.getString("signature"), Base64.NO_WRAP)
            val dataToVerify = JSONObject(rawData).apply { remove("signature") }.toString().toByteArray()

            if (CryptoManager.verify(sig, dataToVerify, pubKey)) {
                listeners.forEach { it(json.getString("type"), json.getString("data"), fromIp, json.getString("from")) }
            }
        } catch (e: Exception) { }
    }

    fun stopP2PNode() {
        isListening = false
        mainSocket?.close()
    }

    private fun sha256(input: String): String = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}
