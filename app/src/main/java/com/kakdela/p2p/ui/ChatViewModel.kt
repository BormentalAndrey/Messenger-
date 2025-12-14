package com.kakdela.p2p.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakdela.p2p.data.ChatRepository
import com.kakdela.p2p.data.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val repo = ChatRepository()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var currentChatId: String? = null

    /**
     * Запуск прослушивания чата
     * Вызывается ТОЛЬКО при смене chatId
     */
    fun start(chatId: String) {
        if (currentChatId == chatId) return

        currentChatId = chatId
        repo.stopListening()

        repo.listenMessages(chatId) { newMessages ->
            viewModelScope.launch {
                _messages.emit(newMessages)
            }
        }
    }

    /**
     * Отправка сообщения
     */
    fun send(chatId: String, message: Message) {
        repo.sendMessage(chatId, message)
    }

    override fun onCleared() {
        super.onCleared()
        repo.stopListening()
    }
}
