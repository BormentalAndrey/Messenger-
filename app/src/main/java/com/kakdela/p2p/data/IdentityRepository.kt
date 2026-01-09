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
        // Инициализация ключей при старте
        CryptoManager.init(context)
        startMainSocket()
    }

    fun getMyId(): String = sha256(CryptoManager.getMyPublicKeyStr())
    fun getMyPublicKeyStr(): String = CryptoManager.getMyPublicKeyStr()
    fun savePeerPublicKey(hash: String, key: String) = CryptoManager.savePeerPublicKey(hash, key)

    fun addListener(listener: (String, String, String, String) -> Unit) = listeners.add(listener)
    fun removeListener(listener: (String, String, String, String) -> Unit) = listeners.remove(listener)

    /**
     * Поиск пользователя в DHT/Сети. Исправлен Type Mismatch.
     */
    fun findPeerInDHT(hash: String): Deferred<UserPayload?> = scope.async {
        try {
            val response = api.findPeer(payload = mapOf("hash" to hash))
            response.userNode
        } catch (e: Exception) {
            Log.e(TAG, "DHT Lookup error: ${e.message}")
            null
        }
    }

    /**
     * Генерация хеша пользователя. 
     * Теперь принимает 3 аргумента для полной совместимости с EmailAuthScreen.
     */
    fun generateUserHash(phone: String, email: String, pass: String): String {
        return sha256("$phone:$email:$pass")
    }

    /**
     * Отправка сигнальных сообщений (WebRTC/Status).
     */
    fun sendSignaling(targetIp: String, type: String, data: String) = scope.launch {
        try {
            val json = JSONObject().apply {
                put("type", type)
                put("data", data)
                put("from", getMyId())
                put("pubkey", getMyPublicKeyStr())
                put("timestamp", System.currentTimeMillis())
            }
            // Добавляем подпись
            json.put("signature", sign(json))
            sendUdp(targetIp, json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Signaling failed", e)
        }
    }

    private suspend fun sendUdp(ip: String, message: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (ip == "0.0.0.0" || ip.isBlank()) return@withContext false
                DatagramSocket().use { socket ->
                    val data = message.toByteArray()
                    val packet = DatagramPacket(data, data.size, InetAddress.getByName(ip), 8888)
                    socket.send(packet)
                }
                true
            } catch (e: Exception) { false }
        }

    private fun startMainSocket() = scope.launch {
        try {
            mainSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(8888))
            }
            // Здесь должен быть цикл receive(), если планируется слушать входящие UDP
        } catch (e: Exception) {
            Log.e(TAG, "UDP bind failed", e)
        }
    }

    private fun sign(json: JSONObject): String {
        val sig = CryptoManager.sign(json.toString().toByteArray())
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun onDestroy() {
        scope.cancel()
        mainSocket?.close()
    }
}
