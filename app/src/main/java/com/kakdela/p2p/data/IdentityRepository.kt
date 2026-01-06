package com.kakdela.p2p.data

import android.content.Context
import android.util.Log
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class IdentityRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("p2p_identity", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // DHT: Хранилище пар Ключ(Хеш) -> Значение(Зашифрованные данные)
    private val localDhtSlice = ConcurrentHashMap<String, String>()
    
    // Список активных узлов (IP адреса найденных телефонов)
    private val discoveredPeers = CopyOnWriteArraySet<String>()
    
    private val P2P_PORT = 8888
    private val DISCOVERY_PORT = 8889

    init {
        startListening()      // Слушаем входящие данные (STORE/FIND)
        startDiscovery()      // Слушаем анонсы новых узлов
        broadcastPresence()   // Объявляем о себе в сеть
    }

    /**
     * ПУБЛИКАЦИЯ: Привязка номера телефона
     */
    suspend fun publishIdentity(phoneNumber: String, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val publicKey = CryptoManager.getMyPublicKeyStr()
            val phoneHash = hashString(phoneNumber)

            prefs.edit().apply {
                putString("my_phone", phoneNumber)
                putString("my_name", name)
                putString("my_pub_key", publicKey)
                apply()
            }

            // Рассылаем по всем найденным узлам
            val message = createMessage("STORE", phoneHash, publicKey)
            sendToPeers(message)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * ПРИВЯЗКА EMAIL И ПАРОЛЯ: Создание децентрализованного бэкапа
     */
    suspend fun updateEmail(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val emailHash = hashString(email.lowercase())
            val encryptedVault = CryptoManager.exportEncryptedKeyset(password)
            
            val identityJson = JSONObject().apply {
                put("name", prefs.getString("my_name", "User"))
                put("phone_hash", hashString(prefs.getString("my_phone", "") ?: ""))
                put("vault", encryptedVault)
                put("v", 1) // Версия записи
            }

            val message = createMessage("STORE", emailHash, identityJson.toString())
            sendToPeers(message)
            
            prefs.edit().putString("my_email", email).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * ВХОД: Восстановление аккаунта из сети по почте и паролю
     */
    suspend fun signInWithEmailP2P(email: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val emailHash = hashString(email.lowercase())
        
        // 1. Сначала проверяем, нет ли данных в локальном кэше
        var remoteData = localDhtSlice[emailHash]

        // 2. Если нет, делаем запрос в сеть (FIND)
        if (remoteData == null) {
            broadcastDiscoveryRequest(emailHash)
            delay(2000) // Ждем ответов от узлов
            remoteData = localDhtSlice[emailHash]
        }

        remoteData?.let {
            try {
                val json = JSONObject(it)
                val vault = json.getString("vault")
                val success = CryptoManager.importEncryptedKeyset(vault, pass)
                if (success) {
                    prefs.edit().putString("my_name", json.optString("name")).apply()
                    return@withContext true
                }
            } catch (e: Exception) { }
        }
        false
    }

    // --- СЕТЕВАЯ ПОДСИСТЕМА ---

    private fun startListening() {
        scope.launch {
            val socket = DatagramSocket(P2P_PORT)
            val buffer = ByteArray(8192)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val msg = String(packet.data, 0, packet.length)
                val json = JSONObject(msg)
                
                when (json.getString("type")) {
                    "STORE" -> localDhtSlice[json.getString("key")] = json.getString("value")
                    "FIND" -> {
                        val key = json.getString("key")
                        localDhtSlice[key]?.let { value ->
                            val response = createMessage("STORE", key, value)
                            sendToSingleAddress(packet.address.hostAddress, response)
                        }
                    }
                }
            }
        }
    }

    private fun startDiscovery() {
        scope.launch {
            val socket = DatagramSocket(DISCOVERY_PORT)
            socket.broadcast = true
            val buffer = ByteArray(1024)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val senderIp = packet.address.hostAddress
                if (senderIp != InetAddress.getLocalHost().hostAddress) {
                    discoveredPeers.add(senderIp)
                }
            }
        }
    }

    private fun broadcastPresence() {
        scope.launch {
            val socket = DatagramSocket()
            socket.broadcast = true
            val msg = "IAM_HERE".toByteArray()
            while (isActive) {
                val packet = DatagramPacket(msg, msg.size, 
                    InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
                socket.send(packet)
                delay(30000) // Раз в 30 секунд
            }
        }
    }

    private fun sendToPeers(message: String) {
        discoveredPeers.forEach { ip ->
            scope.launch { sendToSingleAddress(ip, message) }
        }
    }

    private fun sendToSingleAddress(ip: String, message: String) {
        try {
            val address = InetAddress.getByName(ip)
            val socket = DatagramSocket()
            val data = message.toByteArray()
            socket.send(DatagramPacket(data, data.size, address, P2P_PORT))
            socket.close()
        } catch (e: Exception) { }
    }

    private fun broadcastDiscoveryRequest(key: String) {
        val msg = createMessage("FIND", key, "")
        sendToPeers(msg)
    }

    private fun createMessage(type: String, key: String, value: String): String {
        return JSONObject().apply {
            put("type", type)
            put("key", key)
            put("value", value)
        }.toString()
    }

    private fun hashString(input: String): String {
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

