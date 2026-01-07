package com.kakdela.p2p.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.Message
import com.kakdela.p2p.data.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

/**
 * ViewModel для управления чатом. Реализует отправку сообщений через UDP
 * и поиск маршрута к узлу через DHT.
 */
class ChatViewModel(private val repository: IdentityRepository) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var partnerId: String = "" // Хеш публичного ключа партнера
    private var partnerIp: String = "" // Текущий IP адрес партнера в сети

    init {
        setupIncomingListener()
    }

    /**
     * Инициализация чата. Если IP неизвестен, запускается поиск в DHT.
     */
    fun initChat(id: String, myUid: String) {
        this.partnerId = id
        
        // Пытаемся найти IP партнера, если он еще не определен
        if (partnerIp.isBlank()) {
            resolvePartnerIp()
        }
    }

    private fun resolvePartnerIp() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("ChatVM", "Resolving IP for partner: $partnerId")
            // Отправляем запрос в сеть для поиска IP по хешу ключа
            repository.findPeerInDHT(partnerId)
            
            // В реальном P2P ответе придет STORE_RESPONSE, который обработает setupIncomingListener
        }
    }

    /**
     * Настройка слушателя. Важно: используем паттерн "цепочки", чтобы не стереть
     * обработчики звонков в IdentityRepository.
     */
    private fun setupIncomingListener() {
        val originalListener = repository.onSignalingMessageReceived
        
        repository.onSignalingMessageReceived = { type, data, fromIp ->
            when (type) {
                "CHAT_MSG" -> {
                    handleIncomingP2PMessage(data, fromIp)
                }
                "STORE_RESPONSE" -> {
                    // Если пришел ответ на наш запрос поиска IP
                    val parts = data.split(":")
                    if (parts.size >= 1 && parts[0] == partnerId) {
                        this.partnerIp = fromIp
                        Log.d("ChatVM", "Partner IP resolved: $partnerIp")
                    }
                }
            }
            // Пробрасываем другим компонентам (например, CallActivity)
            originalListener?.invoke(type, data, fromIp)
        }
    }

    private fun handleIncomingP2PMessage(jsonStr: String, fromIp: String) {
        try {
            val json = JSONObject(jsonStr)
            val senderId = json.getString("senderId")
            
            // Проверяем, что сообщение именно от нашего собеседника
            if (senderId == partnerId) {
                this.partnerIp = fromIp // Обновляем IP, если он изменился
                
                val msg = Message(
                    id = json.getString("id"),
                    senderId = senderId,
                    text = json.getString("text"),
                    timestamp = json.getLong("timestamp"),
                    isMe = false
                )

                viewModelScope.launch(Dispatchers.Main) {
                    _messages.value = _messages.value + msg
                }
            }
        } catch (e: Exception) {
            Log.e("ChatVM", "Error parsing incoming msg", e)
        }
    }

    

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Если IP еще не найден, пробуем отправить широковещательно или повторяем поиск
        if (partnerIp.isBlank()) {
            resolvePartnerIp()
            // Здесь можно добавить сообщение "Поиск собеседника в сети..." в UI
        }

        val myId = repository.getMyId()
        val timestamp = System.currentTimeMillis()
        val msgId = UUID.randomUUID().toString()

        val msgObj = Message(
            id = msgId,
            senderId = myId,
            text = text,
            timestamp = timestamp,
            isMe = true
        )

        // 1. Обновляем локальный UI немедленно
        _messages.value = _messages.value + msgObj

        // 2. Отправка через сеть
        viewModelScope.launch(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("id", msgId)
                put("senderId", myId)
                put("text", text)
                put("timestamp", timestamp)
            }

            if (partnerIp.isNotEmpty()) {
                repository.sendSignaling(partnerIp, "CHAT_MSG", payload.toString())
            } else {
                // Если IP нет, можно попробовать отправить на все известные узлы (Flood)
                Log.e("ChatVM", "Target IP unknown, message might not be delivered")
            }
        }
    }

    fun sendFile(uri: Uri, type: MessageType) {
        // Логика: отправляем текстовое уведомление, затем запускаем передачу байтов
        val fileName = uri.lastPathSegment ?: "file"
        sendMessage("📁 Файл: $fileName")
        
        viewModelScope.launch(Dispatchers.IO) {
            // Здесь вызывается логика FileTransferManager (протокол TCP/UDP Stream)
            Log.d("ChatVM", "Initiating file transfer for $uri to $partnerIp")
        }
    }

    fun sendAudio(uri: Uri, duration: Int) {
        sendMessage("🎤 Голосовое сообщение ($duration сек.)")
        // Логика передачи аудио-файла аналогична sendFile
    }

    fun scheduleMessage(text: String, timeMillis: Long) {
        val delayMs = timeMillis - System.currentTimeMillis()
        if (delayMs <= 0) return

        viewModelScope.launch {
            delay(delayMs)
            sendMessage(text)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Важно: здесь можно сбросить слушатель, но в P2P лучше оставить его 
        // на уровне Repository для работы фоновых уведомлений.
    }
}

