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
    
    // Используем SupervisorJob, чтобы ошибка в одной задаче не убила весь репозиторий
    private val repositoryJob = SupervisorJob()
    private val scope = CoroutineScope(repositoryJob + Dispatchers.IO)
    
    private val listeners = CopyOnWriteArrayList<(String, String, String, String) -> Unit>()
    private var keepAliveJob: Job? = null

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
        startKeepAlive()
    }

    // --- ГЕНЕРАЦИЯ ИДЕНТИФИКАТОРОВ ---

    fun getMyId(): String = sha256(CryptoManager.getMyPublicKeyStr())
    
    fun getMyPublicKeyStr(): String = CryptoManager.getMyPublicKeyStr()

    /**
     * Генерирует хеш для регистрации (номер + почта + пароль)
     */
    fun generateUserHash(phone: String, email: String, pass: String): String = 
        sha256("$phone:$email:$pass")

    // --- РАБОТА С СЕРВЕРОМ (PHP API) ---

    /**
     * Регистрация и обновление IP. 
     * Соответствует PHP: case 'add_user'
     */
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive) {
                try {
                    val myPayload = UserPayload(
                        hash = getMyId(),
                        publicKey = getMyPublicKeyStr(),
                        port = 8888,
                        ip = "0.0.0.0" // Сервер сам подставит реальный IP
                    )
                    
                    // Отправляем запрос на index.php?action=add_user
                    val response = api.announceSelf(payload = myPayload)
                    if (response.success) {
                        Log.d(TAG, "Keep-alive: Узел успешно зарегистрирован в БД")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Keep-alive error: ${e.message}")
                }
                delay(180_000) // Раз в 3 минуты
            }
        }
    }

    /**
     * Поиск пира. Получает список последних 2500 узлов и фильтрует их.
     * Соответствует PHP: case 'list_users'
     */
    fun findPeerInDHT(hash: String): Deferred<UserPayload?> = scope.async {
        try {
            val response = api.getAllNodes() // Вызывает action=list_users
            val foundPeer = response.users?.find { it.hash == hash }
            
            if (foundPeer?.ip != null) {
                // Пробивка NAT (UDP Hole Punching)
                sendRawPing(foundPeer.ip)
            }
            return@async foundPeer
        } catch (e: Exception) {
            Log.e(TAG, "DHT Lookup error: ${e.message}")
            null
        }
    }

    // --- P2P ПЕРЕДАЧА ДАННЫХ (UDP) ---

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
            
            // Подпись данных приватным ключом устройства
            val signatureBytes = CryptoManager.sign(json.toString().toByteArray())
            json.put("signature", Base64.encodeToString(signatureBytes, Base64.NO_WRAP))

            val buffer = json.toString().toByteArray()
            withContext(Dispatchers.IO) {
                DatagramSocket().use { socket ->
                    val packet = DatagramPacket(
                        buffer, buffer.size, 
                        InetAddress.getByName(targetIp), 8888
                    )
                    socket.send(packet)
                }
            }
        } catch (e: Exception) { 
            Log.e(TAG, "UDP Send error", e) 
        }
    }

    private fun sendRawPing(targetIp: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val buf = "PING".toByteArray()
                DatagramSocket().use { s ->
                    s.send(DatagramPacket(buf, buf.size, InetAddress.getByName(targetIp), 8888))
                }
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
            val buffer = ByteArray(16384)
            
            while (isListening && isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                mainSocket?.receive(packet)
                
                val rawData = String(packet.data, 0, packet.length)
                if (rawData != "PING") {
                    handleIncomingPacket(rawData, packet.address.hostAddress ?: "")
                }
            }
        } catch (e: Exception) { 
            isListening = false 
        } finally {
            mainSocket?.close()
        }
    }

    private fun handleIncomingPacket(rawData: String, fromIp: String) {
        try {
            val json = JSONObject(rawData)
            val pubKey = json.getString("pubkey")
            val signature = Base64.decode(json.getString("signature"), Base64.NO_WRAP)
            
            // Подготовка данных для верификации (удаляем поле подписи)
            val originalData = JSONObject(rawData).apply { remove("signature") }
                .toString().toByteArray()

            if (CryptoManager.verify(signature, originalData, pubKey)) {
                // Если подпись верна, уведомляем UI через слушателей
                listeners.forEach { 
                    it(json.getString("type"), json.getString("data"), fromIp, json.getString("from")) 
                }
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Packet handling error")
        }
    }

    // --- УПРАВЛЕНИЕ РЕПОЗИТОРИЕМ ---

    fun savePeerPublicKey(hash: String, key: String) {
        CryptoManager.savePeerPublicKey(hash, key)
    }

    fun addListener(listener: (String, String, String, String) -> Unit) = listeners.add(listener)
    
    fun removeListener(listener: (String, String, String, String) -> Unit) = listeners.remove(listener)

    fun onDestroy() {
        keepAliveJob?.cancel()
        repositoryJob.cancel()
        isListening = false
        mainSocket?.close()
    }

    private fun sha256(input: String): String = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}
