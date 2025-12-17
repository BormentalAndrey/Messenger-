package com.kakdela.p2p.ui

import androidx.lifecycle.ViewModel
import com.kakdela.p2p.data.ChatRepository
import com.kakdela.p2p.data.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    // Фильтруем список: показываем только те, у которых время уже наступило или не задано
    val messages = _messages.asStateFlow()

    fun start(chatId: String) {
        repository.listen(chatId) { allMsgs ->
            val currentTime = System.currentTimeMillis()
            _messages.value = allMsgs.filter { msg ->
                msg.scheduledTime == 0L || msg.scheduledTime <= currentTime
            }
        }
    }

    fun send(chatId: String, message: Message) {
        repository.send(chatId, message)
    }
}

