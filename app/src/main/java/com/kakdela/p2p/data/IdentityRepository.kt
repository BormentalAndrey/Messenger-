package com.kakdela.p2p.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kakdela.p2p.api.ServerResponse
import com.kakdela.p2p.api.UserPayload
import com.kakdela.p2p.api.WebViewApiClient
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.NodeEntity
import com.kakdela.p2p.security.CryptoManager
import kotlinx.coroutines.*
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class IdentityRepository(private val context: Context) {

    private val TAG = "IdentityRepo"
    
    // Основной Scope для фоновых операций репозитория
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val api = WebViewApiClient
    private val db = ChatDatabase.getDatabase(context)
    private val nodeDao = db.nodeDao()

    // Хранение активных пиров в памяти
    val wifiPeers = ConcurrentHashMap<String, String>()
    val swarmPeers = ConcurrentHashMap<String, String>()
    
    private val listeners = CopyOnWriteArrayList<(String, String, String, String) -> Unit>()
    
    @Volatile private var isRunning = false

    /**
     * Возвращает уникальный хеш текущего пользователя
     */
    fun getMyId(): String = prefs.getString("my_security_hash", "") ?: ""

    /**
     * Запуск сетевой активности: анонс себя на сервере и синхронизация списка узлов
     */
    fun startNetwork() {
        if (isRunning) return
        isRunning = true
        
        repositoryScope.launch {
            val myId = getMyId()
            if (myId.isNotEmpty()) {
                performServerSync(myId)
            } else {
                Log.w(TAG, "My ID is empty. Skipping network start.")
            }
        }
    }

    /**
     * Добавление конкретного узла по его хешу (используется в SettingsScreen)
     */
    suspend fun addNodeByHash(hash: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response: ServerResponse = api.getAllNodes()
            if (response.success && response.users != null) {
                val node = response.users.find { it.hash == hash }
                if (node != null) {
                    saveNodeToDb(node)
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error adding node by hash: ${e.message}")
            false
        }
    }

    /**
     * Регистрация/обновление данных о себе на центральном сервере (Discovery)
     */
    private suspend fun performServerSync(myId: String) {
        val payload = UserPayload(
            hash = myId,
            phone_hash = prefs.getString("my_phone_hash", ""),
            publicKey = CryptoManager.getMyPublicKeyStr(),
            ip = getCurrentIp(),
            port = 8888,
            phone = prefs.getString("my_phone", ""),
            lastSeen = System.currentTimeMillis()
        )
        
        try {
            val res = api.announceSelf(payload)
            if (res.success) {
                Log.d(TAG, "Self-announce successful")
                syncLocalNodesWithServer()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync error during announce: ${e.message}")
        }
    }

    /**
     * Получение списка всех узлов с сервера и сохранение их в локальную БД
     */
    private suspend fun syncLocalNodesWithServer() {
        try {
            val response = api.getAllNodes()
            val users = response.users ?: return
            
            // Используем обычный цикл for, чтобы избежать проблем с suspend в лямбдах
            for (user in users) {
                if (user.hash != getMyId()) {
                    saveNodeToDb(user)
                }
            }
            Log.d(TAG, "Sync complete. Found ${users.size} nodes.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync local nodes with server", e)
        }
    }

    /**
     * Сохранение или обновление данных узла в Room
     */
    private suspend fun saveNodeToDb(node: UserPayload) {
        val entity = NodeEntity(
            userHash = node.hash,
            phone_hash = node.phone_hash ?: "",
            ip = node.ip ?: "0.0.0.0",
            port = node.port,
            publicKey = node.publicKey,
            lastSeen = node.lastSeen ?: System.currentTimeMillis()
        )
        nodeDao.insert(entity) // insert должен иметь OnConflictStrategy.REPLACE в NodeDao
    }

    /**
     * Определение текущего IP адреса устройства в локальной сети
     */
    fun getCurrentIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && !addr.hostAddress!!.contains(":")) {
                        return addr.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP", e)
        }
        return "127.0.0.1"
    }

    // --- Методы взаимодействия (Сигналинг и P2P) ---

    /**
     * Интеллектуальная отправка сообщения: пробует разные каналы связи
     */
    fun sendMessageSmart(toHash: String, payload: String?, message: String): Boolean {
        // Здесь должна быть логика выбора между локальной сетью, WebRTC или сервером
        Log.d(TAG, "Sending message to $toHash: $message")
        return true 
    }

    /**
     * Отправка сигнальных данных (SDP/Ice Candidates) для WebRTC
     */
    fun sendSignaling(toHash: String, sdp: String) {
        repositoryScope.launch {
            try {
                // Пример: отправка через WebView API посредника
                // api.sendSignal(getMyId(), toHash, sdp)
                Log.d(TAG, "Signaling sent to $toHash")
            } catch (e: Exception) {
                Log.e(TAG, "Signaling failed", e)
            }
        }
    }

    fun addListener(l: (String, String, String, String) -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: (String, String, String, String) -> Unit) {
        listeners.remove(l)
    }

    /**
     * Очистка ресурсов при уничтожении репозитория
     */
    fun onDestroy() {
        isRunning = false
        repositoryScope.cancel()
    }
}
