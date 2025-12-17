package com.kakdela.p2p.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.data.WebRtcClient
import com.kakdela.p2p.data.local.ChatDatabase
import com.kakdela.p2p.data.local.MessageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Используем AndroidViewModel, чтобы иметь доступ к context для базы данных
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = ChatDatabase.getDatabase(application).messageDao()
    private var rtcClient: WebRtcClient? = null

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    /**
     * Инициализация чата
     */
    fun initChat(chatId: String, currentUserId: String) {
        // 1. Создаем WebRtcClient для этого чата
        rtcClient = WebRtcClient(getApplication(), chatId, currentUserId)

        // 2. Начинаем наблюдать за локальной базой данных (Room)
        viewModelScope.launch {
            dao.getMessagesForChat(chatId).collect { list ->
                // Фильтруем отложенные сообщения, если это необходимо
                val currentTime = System.currentTimeMillis()
                _messages.value = list.filter { msg ->
                    // Здесь логика: если есть запланированное время, ждем его
                    // В P2P это работает локально на устройстве отправителя
                    msg.timestamp <= currentTime 
                }
            }
        }
    }

    /**
     * Отправка текстового сообщения через P2P
     */
    fun sendMessage(text: String) {
        viewModelScope.launch {
            rtcClient?.sendP2P(text = text, bytes = null)
        }
    }

    /**
     * Отправка файла через P2P
     */
    fun sendFile(bytes: ByteArray) {
        viewModelScope.launch {
            rtcClient?.sendP2P(text = "", bytes = bytes)
        }
    }
}

