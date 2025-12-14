package com.kakdela.p2p.ui

import androidx.lifecycle.ViewModel
import com.kakdela.p2p.data.ChatRepository
import com.kakdela.p2p.data.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    fun start(chatId: String) {
        repository.listen(chatId) {
            _messages.value = it
        }
    }

    fun send(chatId: String, message: Message) {
        repository.send(chatId, message)
    }
}
