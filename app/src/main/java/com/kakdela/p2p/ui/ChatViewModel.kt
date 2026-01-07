package com.kakdela.p2p.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.data.Message
import com.kakdela.p2p.data.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

class ChatViewModel(private val repository: IdentityRepository) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var partnerId: String = "" // В P2P это публичный ключ или хеш партнера
    private var partnerIp: String = "" // IP адрес для отправки UDP пакетов

    init {
        // Подписываемся на входящие P2P сообщения
        repository.onSignalingMessageReceived = { type, data, fromIp ->
            if (type == "CHAT_MSG") {
                handleIncomingP2PMessage(data, fromIp)
            }
        }
    }

    fun initChat(id: String, myUid: String) {
        this.partnerId = id
        // В реальном сценарии здесь должен быть поиск IP в DHT по id (публичному ключу)
        // Для тестов предполагаем, что id может временно содержать IP или берется из кэша
    }

    fun setPartnerIp(ip: String) {
        this.partnerIp = ip
    }

    private fun handleIncomingP2PMessage(jsonStr: String, fromIp: String) {
        try {
            val json = JSONObject(jsonStr)
            val msg = Message(
                id = json.getString("id"),
                senderId = json.getString("senderId"),
                text = json.getString("text"),
                timestamp = json.getLong("timestamp"),
                isMe = false
            )
            
            // Если сообщение от текущего собеседника, обновляем UI
            if (msg.senderId == partnerId || fromIp == partnerIp) {
                _messages.value = _messages.value + msg
            }
            
            // Здесь должна быть логика сохранения в локальную БД (Room)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || partnerIp.isBlank()) return

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

        // 1. Обновляем локальный UI
        _messages.value = _messages.value + msgObj

        // 2. Сериализуем и отправляем через P2P репозиторий
        val payload = JSONObject().apply {
            put("id", msgId)
            put("senderId", myId)
            put("text", text)
            put("timestamp", timestamp)
        }

        repository.sendSignaling(partnerIp, "CHAT_MSG", payload.toString())
        
        // 3. Сохраняем в локальную БД (dao.insert...)
    }

    fun sendFile(uri: Uri, type: MessageType) {
        sendMessage("Отправлен файл: ${uri.lastPathSegment}")
        // Здесь будет запуск FileTransferWorker
    }

    fun sendAudio(uri: Uri, duration: Int) {
        sendMessage("Аудио сообщение ($duration сек)")
    }

    fun scheduleMessage(text: String, timeMillis: Long) {
        // В P2P отложенные сообщения хранятся локально и отправляются по таймеру
        sendMessage("[Запланировано] $text")
    }
}

