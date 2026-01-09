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

/**
 * Репозиторий идентификации и P2P взаимодействия.
 * Управляет DHT-поиском, UDP-сигналингом и верификацией узлов.
 */
class IdentityRepository(private val context: Context) {

    private val TAG = "IdentityRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nodeDao = ChatDatabase.getDatabase(context).nodeDao()

    // Список слушателей входящих событий (Type, Data, FromIP, FromID)
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
        // Инициализация крипто-ключей
        CryptoManager.init(context)
        startListening()
    }

    // --- Публичные методы идентификации ---

    fun getMyId(): String = sha256(CryptoManager.getMyPublicKeyStr())
    
    fun getMyPublicKeyStr(): String = CryptoManager.getMyPublicKeyStr()

    fun addListener(listener: (String, String, String, String) -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: (String, String, String, String) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Поиск узла в DHT. Возвращает данные об IP и публичном ключе.
     */
    fun findPeerInDHT(hash: String): Deferred<UserPayload?> = scope.async {
        try {
            val response = api.findPeer(action = "find", hash = hash)
            if (response.isSuccessful) response.body()?.userNode else null
        } catch (e: Exception) {
            Log.e(TAG, "DHT Lookup error: ${e.message}")
            null
        }
    }

    /**
     * Генерация уникального хеша пользователя на основе учетных данных.
     */
    fun generateUserHash(phone: String, email: String, pass: String): String {
        return sha256("$phone:$email:$pass")
    }

    // --- Сетевое взаимодействие (UDP) ---

    /**
     * Отправка зашифрованного или сигнального сообщения через UDP.
     */
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
            
            // Добавляем цифровую подпись для аутентификации пакета
            val signature = CryptoManager.sign(json.toString().toByteArray())
            json.put("signature", Base64.encodeToString(signature, Base64.NO_WRAP))

            val buffer = json.toString().toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(buffer, buffer.size, InetAddress.getByName(targetIp), 8888)
            
            withContext(Dispatchers.IO) {
                val tempSocket = DatagramSocket()
                tempSocket.send(packet)
                tempSocket.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "UDP Send error: ${e.message}")
        }
    }

    /**
     * Запуск постоянного прослушивания порта 8888 для входящих P2P пакетов.
     */
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
                
                val receivedData = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val fromIp = packet.address.hostAddress ?: ""
                
                handleIncomingPacket(receivedData, fromIp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "UDP Receive loop error", e)
            isListening = false
        }
    }

    private fun handleIncomingPacket(rawData: String, fromIp: String) {
        try {
            val json = JSONObject(rawData)
            val type = json.getString("type")
            val data = json.getString("data")
            val fromId = json.getString("from")
            val pubKey = json.getString("pubkey")
            val signature = Base64.decode(json.getString("signature"), Base64.NO_WRAP)

            // 1. Проверка целостности и авторства (Verify Signature)
            val jsonToVerify = JSONObject(rawData).apply { remove("signature") }
            val isValid = CryptoManager.verify(signature, jsonToVerify.toString().toByteArray(), pubKey)

            if (isValid) {
                // 2. Сохраняем/обновляем ключ собеседника
                CryptoManager.savePeerPublicKey(fromId, pubKey)
                
                // 3. Уведомляем всех подписчиков (ViewModel/Service)
                listeners.forEach { it(type, data, fromIp, fromId) }
            } else {
                Log.w(TAG, "Discarded unverified packet from $fromIp")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Packet parsing error", e)
        }
    }

    fun stopP2PNode() {
        isListening = false
        mainSocket?.close()
        mainSocket = null
    }

    fun onDestroy() {
        stopP2PNode()
        scope.cancel()
    }

    // --- Утилиты ---

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
