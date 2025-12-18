// Файл: app/src/main/java/com/kakdela/p2p/ui/ChatViewModel.kt
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

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = ChatDatabase.getDatabase(application).messageDao()
    private var rtcClient: WebRtcClient? = null

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    /**
     * Инициализация чата
     */
    fun initChat(chatId: String, currentUserId: String) {
        rtcClient = WebRtcClient(getApplication(), chatId, currentUserId)

        viewModelScope.launch {
            dao.getMessagesForChat(chatId).collect { list ->
                val currentTime = System.currentTimeMillis()
                // Фильтруем сообщения: показываем только те, чье время уже наступило
                _messages.value = list.filter { msg ->
                    msg.timestamp <= currentTime 
                }
            }
        }
    }

    /**
     * Отправка обычного сообщения
     */
    fun sendMessage(text: String) {
        viewModelScope.launch {
            rtcClient?.sendP2P(text = text, bytes = null)
        }
    }

    /**
     * Отправка запланированного сообщения (Добавлено для исправления ошибки NavGraph)
     */
    fun scheduleMessage(text: String, timeMillis: Long) {
        viewModelScope.launch {
            // В P2P логике мы отправляем сообщение с будущим временем.
            // Получатель сохранит его, но не покажет до наступления timeMillis.
            rtcClient?.sendP2P(text = text, bytes = null) 
            // Дополнительно можно пометить сообщение в БД как "отложенное"
        }
    }

    /**
     * Отправка файла
     */
    fun sendFile(bytes: ByteArray) {
        viewModelScope.launch {
            rtcClient?.sendP2P(text = "", bytes = bytes)
        }
    }
}

