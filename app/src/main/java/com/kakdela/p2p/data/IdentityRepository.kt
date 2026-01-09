package com.kakdela.p2p.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.kakdela.p2p.api.MyServerApi
import com.kakdela.p2p.api.UserPayload
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
    
    // Периодическая задача для обновления статуса на сервере
    private var keepAliveJob: Job? = null

    private val api: MyServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://kakdela.infinityfree.me/") // Убедитесь, что адрес совпадает с вашим хостингом
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MyServerApi::class.java)
    }

    private var mainSocket: DatagramSocket? = null
    private var isListening = false

    init {
        CryptoManager.init(context)
        startListening()
        startKeepAlive() // Автоматическая регистрация при старте
    }

    // --- ИДЕНТИФИКАЦИЯ ---

    fun getMyId(): String = sha256(CryptoManager.getMyPublicKeyStr())
    
    fun getMyPublicKeyStr(): String = CryptoManager.getMyPublicKeyStr()

    fun generateUserHash(phone: String, email: String, pass: String): String = 
        sha256("$phone:$email:$pass") //

    // --- СЕРВЕРНОЕ ВЗАИМОДЕЙСТВИЕ ---

    /**
     * Поддержание "онлайн" статуса. Отправляет данные на сервер каждые 2 минуты.
     * Это обновляет ваш IP в базе данных сервера для других пиров.
     */
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive) {
                try {
                    val payload = UserPayload(
                        hash = getMyId(),
                        publicKey = getMyPublicKeyStr(),
                        port = 8888 // Стандартный порт для UDP
                    )
                    api.announceSelf(payload = payload) // Вызов action=add_user
                    Log.d(TAG, "Keep-alive: Успешно обновлен статус в сети")
                } catch (e: Exception) {
                    Log.e(TAG, "Keep-alive error: ${e.message}")
                }
                delay(120_000) // 2 минуты
            }
        }
    }

    /**
     * Поиск пира через сервер. Получает список всех узлов и ищет нужный хеш.
     * Соответствует логике PHP 'list_users'.
     */
    fun findPeerInDHT(hash: String): Deferred<UserPayload?> = scope.async {
        try {
            val response = api.getAllNodes() // Получаем список всех
            val peer = response.users?.find { it.hash == hash } // Ищем в списке
            
            if (peer != null && peer.ip != null) {
                // NAT Hole Punching: отправляем пустой пакет для открытия порта
                sendPing(peer.ip)
            }
            peer
        } catch (e: Exception) {
            Log.e(TAG, "DHT Lookup error: ${e.message}")
            null
        }
    }

    // --- P2P СИГНАЛИНГ (UDP) ---

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
            
            // Подпись сообщения для безопасности
            val sig = CryptoManager.sign(json.toString().toByteArray())
            json.put("signature", Base64.encodeToString(sig, Base64.NO_WRAP))

            val buffer = json.toString().toByteArray()
            withContext(Dispatchers.IO) {
                val socket = DatagramSocket()
                val packet = DatagramPacket(buffer, buffer.size, InetAddress.getByName(targetIp), 8888)
                socket.send(packet)
                socket.close()
            }
        } catch (e: Exception) { 
            Log.e(TAG, "UDP Send error to $targetIp", e) 
        }
    }

    private fun sendPing(targetIp: String) {
        scope.launch {
            try {
                val buffer = "PING".toByteArray()
                val socket = DatagramSocket()
                val packet = DatagramPacket(buffer, buffer.size, InetAddress.getByName(targetIp), 8888)
                socket.send(packet)
                socket.close()
            } catch (e: Exception) { }
        }
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
        } catch (e: Exception) { 
            Log.e(TAG, "UDP Listen error", e)
            isListening = false 
        }
    }

    private fun handleIncomingPacket(rawData: String, fromIp: String) {
        if (rawData == "PING") return // Игнорируем пакеты пробивки порта
        
        try {
            val json = JSONObject(rawData)
            val pubKey = json.getString("pubkey")
            val sig = Base64.decode(json.getString("signature"), Base64.NO_WRAP)
            
            // Верификация подписи: гарантирует, что сообщение пришло именно от этого пользователя
            val dataToVerify = JSONObject(rawData).apply { remove("signature") }.toString().toByteArray()

            if (CryptoManager.verify(sig, dataToVerify, pubKey)) {
                listeners.forEach { 
                    it(json.getString("type"), json.getString("data"), fromIp, json.getString("from")) 
                }
            }
        } catch (e: Exception) { 
            Log.d(TAG, "Received non-JSON or invalid packet from $fromIp")
        }
    }

    // --- УПРАВЛЕНИЕ ---

    fun savePeerPublicKey(hash: String, key: String) {
        CryptoManager.savePeerPublicKey(hash, key)
    }

    fun addListener(listener: (String, String, String, String) -> Unit) = listeners.add(listener)
    fun removeListener(listener: (String, String, String, String) -> Unit) = listeners.remove(listener)

    fun stopP2PNode() {
        keepAliveJob?.cancel()
        isListening = false
        mainSocket?.close()
    }

    private fun sha256(input: String): String = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}
